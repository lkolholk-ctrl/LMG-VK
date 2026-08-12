package com.lmg.vk.data.local

import android.content.Context
import android.content.SharedPreferences
import com.lmg.vk.engine.backend.HomeBlock
import com.lmg.vk.engine.backend.HomeItem
import com.lmg.vk.engine.backend.HomeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simple JSON-based cache for Home screen content.
 * Stores in SharedPreferences (survives app updates, no schema migrations needed).
 * 
 * Why not Room: Home content is ephemeral (refreshed frequently),
 * and we want zero migration overhead across app updates.
 */
object HomeCacheManager {

    private const val PREFS_NAME = "home_cache"
    private const val KEY_BLOCKS = "blocks_json"
    private const val KEY_TIMESTAMP = "cached_at"
    private const val KEY_ETAG = "etag"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    // v3 also stores the playlist/album discriminator. Old caches would make
    // editorial VK playlists look like playable albums, so invalidate them.
    private const val CACHE_SCHEMA_VERSION = 4

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save home response to cache.
     */
    suspend fun save(response: HomeResponse) = withContext(Dispatchers.IO) {
        val p = prefs ?: return@withContext
        val json = JSONObject().apply {
            put("version", CACHE_SCHEMA_VERSION)
            put("blocks", JSONArray().apply {
                response.blocks.forEach { block ->
                    put(JSONObject().apply {
                        put("id", block.id)
                        put("title", block.title)
                        put("type", block.type)
                        put("layoutName", block.layoutName)
                        // Курсор, ref догрузки и табы подраздела: без них после
                        // холодного старта из кэша шторка «показать все» не умела
                        // листать, а блок табов приезжал пустым — до первого
                        // сетевого обновления.
                        put("nextFrom", block.nextFrom ?: JSONObject.NULL)
                        put("catalogRef", block.catalogRef ?: JSONObject.NULL)
                        put("subsectionTabs", JSONArray().apply {
                            block.subsectionTabs.forEach { tab ->
                                put(JSONObject().apply {
                                    put("replacementId", tab.replacementId)
                                    put("title", tab.title)
                                    put("icon", tab.icon ?: JSONObject.NULL)
                                    put("selected", tab.selected)
                                })
                            }
                        })
                        put("items", JSONArray().apply {
                            block.items.forEach { item ->
                                put(JSONObject().apply {
                                    put("id", item.id)
                                    put("title", item.title)
                                    put("artist", item.artist)
                                    put("artistId", item.artistId ?: JSONObject.NULL)
                                    put("cover", item.cover ?: JSONObject.NULL)
                                    put("duration", item.duration ?: JSONObject.NULL)
                                    put("source", item.source ?: JSONObject.NULL)
                                    put("collectionId", item.collectionId ?: JSONObject.NULL)
                                    put("album", item.album ?: JSONObject.NULL)
                                    put("genre", item.genre ?: JSONObject.NULL)
                                    put("isAlbum", item.isAlbum)
                                    put("isPlaylist", item.isPlaylist)
                                    put("isArtist", item.isArtist)
                                    put("isClip", item.isClip)
                                    put("isCustom", item.isCustom)
                                    put("isAvailable", item.isAvailable)
                                })
                            }
                        })
                    })
                }
            })
        }
        p.edit().apply {
            putString(KEY_BLOCKS, json.toString())
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }

    /**
     * Load cached home response.
     * @return Cached response or null if expired/missing.
     */
    suspend fun load(): HomeResponse? = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        try {
            val p = prefs ?: return@withContext null
            val jsonStr = p.getString(KEY_BLOCKS, null) ?: return@withContext null
            val cachedAt = p.getLong(KEY_TIMESTAMP, 0)
            if (System.currentTimeMillis() - cachedAt > CACHE_TTL_MS) return@withContext null

            val json = JSONObject(jsonStr)
            if (json.optInt("version", 0) != CACHE_SCHEMA_VERSION) return@withContext null
            val blocksArray = json.getJSONArray("blocks")
            val blocks = mutableListOf<HomeBlock>()
            
            for (i in 0 until blocksArray.length()) {
                val blockObj = blocksArray.getJSONObject(i)
                val itemsArray = blockObj.getJSONArray("items")
                val items = mutableListOf<HomeItem>()
                
                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    items.add(HomeItem(
                        id = itemObj.getString("id"),
                        title = itemObj.getString("title"),
                        artist = itemObj.optString("artist", ""),
                        artistId = itemObj.optString("artistId", null)?.takeIf { it != "null" },
                        cover = itemObj.optString("cover", null)?.takeIf { it != "null" },
                        duration = itemObj.optString("duration", null)
                            ?.takeIf { it != "null" && it.isNotBlank() }
                            ?.toLongOrNull(),
                        source = itemObj.optString("source", null)?.takeIf { it != "null" },
                        collectionId = itemObj.optString("collectionId", null)?.takeIf { it != "null" },
                        album = itemObj.optString("album", null)?.takeIf { it != "null" },
                        genre = itemObj.optString("genre", null)?.takeIf { it != "null" },
                        isAlbum = itemObj.optBoolean("isAlbum", false),
                        isPlaylist = itemObj.optBoolean("isPlaylist", false),
                        isArtist = itemObj.optBoolean("isArtist", false),
                        isClip = itemObj.optBoolean("isClip", false),
                        isCustom = itemObj.optBoolean("isCustom", false),
                        isAvailable = itemObj.optBoolean("isAvailable", true),
                    ))
                }
                
                blocks.add(HomeBlock(
                    id = blockObj.getString("id"),
                    title = blockObj.getString("title"),
                    type = blockObj.getString("type"),
                    items = items,
                    layoutName = blockObj.optString("layoutName", ""),
                    nextFrom = blockObj.optString("nextFrom", "")
                        .takeIf { it.isNotBlank() && it != "null" },
                    catalogRef = blockObj.optString("catalogRef", "")
                        .takeIf { it.isNotBlank() && it != "null" },
                    subsectionTabs = blockObj.optJSONArray("subsectionTabs")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            val t = arr.optJSONObject(i) ?: return@mapNotNull null
                            val replacementId = t.optString("replacementId", "")
                            // Таб без replacementId нажать некуда, без подписи —
                            // не видно; такие в кэше не восстанавливаем.
                            if (replacementId.isBlank()) return@mapNotNull null
                            com.lmg.vk.engine.backend.HomeSubsectionTab(
                                replacementId = replacementId,
                                title = t.optString("title", ""),
                                icon = t.optString("icon", "").takeIf { it.isNotBlank() },
                                selected = t.optBoolean("selected", false),
                            )
                        }
                    } ?: emptyList()
                ))
            }
            
            HomeResponse(blocks = blocks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("HomeCacheManager", "Failed to load home cache", e)
            null
        } finally {
            android.util.Log.d("HomeCacheManager", "load finished in ${System.currentTimeMillis() - startedAt}ms")
        }
    }

    /**
     * Check if cache is fresh (not expired).
     */
    fun isFresh(): Boolean {
        val p = prefs ?: return false
        val cachedAt = p.getLong(KEY_TIMESTAMP, 0)
        return cachedAt > 0 && (System.currentTimeMillis() - cachedAt) < CACHE_TTL_MS
    }

    /**
     * Clear cache.
     */
    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
