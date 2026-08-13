package com.lmg.vk.data.local

import android.content.Context
import android.content.SharedPreferences
import com.lmg.vk.engine.backend.HomeBlock
import com.lmg.vk.engine.backend.HomeCatalogActions
import com.lmg.vk.engine.backend.HomeCatalogSection
import com.lmg.vk.engine.backend.HomeItem
import com.lmg.vk.engine.backend.HomeResponse
import com.lmg.vk.engine.backend.HomeSignalInfo
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
    // v6 also stores actionable CatalogLink URLs. Older caches would turn
    // server artist/curator cards back into disabled grey placeholders.
    private const val CACHE_SCHEMA_VERSION = 6

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
            put("updatedAt", response.updatedAt ?: JSONObject.NULL)
            put("selectedSectionId", response.selectedSectionId ?: JSONObject.NULL)
            put("sectionNextFrom", response.sectionNextFrom ?: JSONObject.NULL)
            put("sections", JSONArray().apply {
                response.sections.forEach { section ->
                    put(JSONObject().apply {
                        put("id", section.id)
                        put("title", section.title)
                    })
                }
            })
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
                        put("actions", JSONObject().apply {
                            put("playBlockId", block.actions.playBlockId ?: JSONObject.NULL)
                            put("playRef", block.actions.playRef ?: JSONObject.NULL)
                            put("shuffled", block.actions.shuffled)
                            put("openSectionId", block.actions.openSectionId ?: JSONObject.NULL)
                            put("openSectionTitle", block.actions.openSectionTitle ?: JSONObject.NULL)
                        })
                        block.signalInfo?.let { signal ->
                            put("signalInfo", JSONObject().apply {
                                put("id", signal.id)
                                put("cover", signal.cover ?: JSONObject.NULL)
                                put("title", signal.title)
                                put("subtitle", signal.subtitle)
                                put("currentMonth", signal.currentMonth)
                                put("audioIds", JSONArray(signal.audioIds))
                                put("playBlockId", signal.playBlockId ?: JSONObject.NULL)
                                put("openSectionId", signal.openSectionId ?: JSONObject.NULL)
                                put("ref", signal.ref ?: JSONObject.NULL)
                                put("shuffled", signal.shuffled)
                            })
                        }
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
                                    put("artistName", item.artistName ?: JSONObject.NULL)
                                    put("artistId", item.artistId ?: JSONObject.NULL)
                                    put("cover", item.cover ?: JSONObject.NULL)
                                    put("duration", item.duration ?: JSONObject.NULL)
                                    put("source", item.source ?: JSONObject.NULL)
                                    put("collectionId", item.collectionId ?: JSONObject.NULL)
                                    put("album", item.album ?: JSONObject.NULL)
                                    put("genre", item.genre ?: JSONObject.NULL)
                                    put("trackId", item.trackId ?: JSONObject.NULL)
                                    put("subtitle", item.subtitle ?: JSONObject.NULL)
                                    put("isExplicit", item.isExplicit)
                                    put("isAlbum", item.isAlbum)
                                    put("isPlaylist", item.isPlaylist)
                                    put("isArtist", item.isArtist)
                                    put("isClip", item.isClip)
                                    put("isCustom", item.isCustom)
                                    put("isAvailable", item.isAvailable)
                                    put("musicOwnerId", item.musicOwnerId ?: JSONObject.NULL)
                                    put("catalogBlockId", item.catalogBlockId ?: JSONObject.NULL)
                                    put("catalogUrl", item.catalogUrl ?: JSONObject.NULL)
                                    put("radioStreamUrl", item.radioStreamUrl ?: JSONObject.NULL)
                                    put("isRadio", item.isRadio)
                                    put("streamMixId", item.streamMixId ?: JSONObject.NULL)
                                    put("streamMixTunable", item.streamMixTunable)
                                    put("streamMixEntityId", item.streamMixEntityId ?: JSONObject.NULL)
                                    put("streamMixSectionId", item.streamMixSectionId ?: JSONObject.NULL)
                                    put("streamMixCatalogItemId", item.streamMixCatalogItemId ?: JSONObject.NULL)
                                    put("streamMixAnimationUrl", item.streamMixAnimationUrl ?: JSONObject.NULL)
                                    put("streamMixResolveSettings", item.streamMixResolveSettings)
                                    put("streamMixOptions", JSONObject().apply {
                                        item.streamMixOptions.forEach { (category, values) ->
                                            put(category, JSONArray(values))
                                        }
                                    })
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
                        artistName = itemObj.optString("artistName", null)?.takeIf { it != "null" },
                        artistId = itemObj.optString("artistId", null)?.takeIf { it != "null" },
                        cover = itemObj.optString("cover", null)?.takeIf { it != "null" },
                        duration = itemObj.optString("duration", null)
                            ?.takeIf { it != "null" && it.isNotBlank() }
                            ?.toLongOrNull(),
                        source = itemObj.optString("source", null)?.takeIf { it != "null" },
                        collectionId = itemObj.optString("collectionId", null)?.takeIf { it != "null" },
                        album = itemObj.optString("album", null)?.takeIf { it != "null" },
                        genre = itemObj.optString("genre", null)?.takeIf { it != "null" },
                        trackId = itemObj.optString("trackId", null)?.takeIf { it != "null" },
                        subtitle = itemObj.optString("subtitle", null)?.takeIf { it != "null" },
                        isExplicit = itemObj.optBoolean("isExplicit", false),
                        isAlbum = itemObj.optBoolean("isAlbum", false),
                        isPlaylist = itemObj.optBoolean("isPlaylist", false),
                        isArtist = itemObj.optBoolean("isArtist", false),
                        isClip = itemObj.optBoolean("isClip", false),
                        isCustom = itemObj.optBoolean("isCustom", false),
                        isAvailable = itemObj.optBoolean("isAvailable", true),
                        musicOwnerId = itemObj.optString("musicOwnerId", null)
                            ?.takeIf { it != "null" && it.isNotBlank() }?.toLongOrNull(),
                        catalogBlockId = itemObj.optString("catalogBlockId", null)
                            ?.takeIf { it != "null" },
                        catalogUrl = itemObj.optString("catalogUrl", null)
                            ?.takeIf { it != "null" },
                        radioStreamUrl = itemObj.optString("radioStreamUrl", null)
                            ?.takeIf { it != "null" },
                        isRadio = itemObj.optBoolean("isRadio", false),
                        streamMixId = itemObj.optString("streamMixId", null)?.takeIf { it != "null" },
                        streamMixTunable = itemObj.optBoolean("streamMixTunable", false),
                        streamMixEntityId = itemObj.optString("streamMixEntityId", null)
                            ?.takeIf { it != "null" },
                        streamMixSectionId = itemObj.optString("streamMixSectionId", null)
                            ?.takeIf { it != "null" },
                        streamMixCatalogItemId = itemObj.optString("streamMixCatalogItemId", null)
                            ?.takeIf { it != "null" },
                        streamMixAnimationUrl = itemObj.optString("streamMixAnimationUrl", null)
                            ?.takeIf { it != "null" },
                        streamMixResolveSettings = itemObj.optBoolean("streamMixResolveSettings", false),
                        streamMixOptions = itemObj.optJSONObject("streamMixOptions")?.let { options ->
                            buildMap {
                                val keys = options.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    val values = options.optJSONArray(key) ?: continue
                                    put(key, (0 until values.length()).mapNotNull { index ->
                                        values.optString(index).takeIf(String::isNotBlank)
                                    })
                                }
                            }
                        }.orEmpty(),
                    ))
                }
                
                val actionsObj = blockObj.optJSONObject("actions")
                val signalObj = blockObj.optJSONObject("signalInfo")
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
                    } ?: emptyList(),
                    signalInfo = signalObj?.let { signal ->
                        HomeSignalInfo(
                            id = signal.optString("id", ""),
                            cover = signal.optString("cover", null)?.takeIf { it != "null" },
                            title = signal.optString("title", ""),
                            subtitle = signal.optString("subtitle", ""),
                            currentMonth = signal.optString("currentMonth", ""),
                            audioIds = signal.optJSONArray("audioIds")?.let { ids ->
                                (0 until ids.length()).mapNotNull { index ->
                                    ids.optString(index).takeIf(String::isNotBlank)
                                }
                            }.orEmpty(),
                            playBlockId = signal.optString("playBlockId", null)
                                ?.takeIf { it != "null" },
                            openSectionId = signal.optString("openSectionId", null)
                                ?.takeIf { it != "null" },
                            ref = signal.optString("ref", null)?.takeIf { it != "null" },
                            shuffled = signal.optBoolean("shuffled", false),
                        )
                    },
                    actions = HomeCatalogActions(
                        playBlockId = actionsObj?.optString("playBlockId", null)
                            ?.takeIf { it != "null" },
                        playRef = actionsObj?.optString("playRef", null)?.takeIf { it != "null" },
                        shuffled = actionsObj?.optBoolean("shuffled", false) == true,
                        openSectionId = actionsObj?.optString("openSectionId", null)
                            ?.takeIf { it != "null" },
                        openSectionTitle = actionsObj?.optString("openSectionTitle", null)
                            ?.takeIf { it != "null" },
                    ),
                ))
            }

            val sections = json.optJSONArray("sections")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val section = array.optJSONObject(index) ?: return@mapNotNull null
                    val id = section.optString("id", "")
                    if (id.isBlank()) return@mapNotNull null
                    HomeCatalogSection(id, section.optString("title", "VK Музыка"))
                }
            }.orEmpty()
            HomeResponse(
                blocks = blocks,
                updatedAt = json.optString("updatedAt", null)
                    ?.takeIf { it != "null" && it.isNotBlank() }
                    ?.toLongOrNull(),
                sections = sections,
                selectedSectionId = json.optString("selectedSectionId", null)
                    ?.takeIf { it != "null" },
                sectionNextFrom = json.optString("sectionNextFrom", null)
                    ?.takeIf { it != "null" },
            )
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
