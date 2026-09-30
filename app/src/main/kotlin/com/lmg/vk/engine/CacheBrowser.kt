package com.lmg.vk.engine

import android.content.Context
import coil.imageLoader
import com.lmg.vk.artwork.*
import com.lmg.vk.data.local.db.AppDatabase
import com.lmg.vk.ui.lyrics.LoadedLyricsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

internal data class CacheEntry(
    val id: String,
    val title: String,
    val artist: String,
    val source: String,
    val bytes: Long,
    val refs: List<String>,
    val preview: File? = null,
    val recording: String = id,
) {
    fun matches(query: String): Boolean = query.trim().split(Regex("\\s+"))
        .all { word -> "$title $artist $source".contains(word, ignoreCase = true) }
}

/** Individual file deletion is confined to explicit cache roots, including after canonicalization. */
internal fun allowedCacheFile(file: File, roots: List<File>): Boolean = roots.any {
    file.canonicalFile.parentFile == it.canonicalFile && file.isFile
}

internal fun deleteCacheFile(file: File, roots: List<File>) {
    // Validate the path even if an entry vanished between the list and the tap.
    require(roots.any { root -> file.canonicalFile.parentFile == root.canonicalFile })
    if (!file.exists()) return
    require(allowedCacheFile(file, roots))
    if (!file.delete() && file.exists()) throw IOException("Cache entry could not be deleted")
}

@OptIn(coil.annotation.ExperimentalCoilApi::class)
internal object CacheBrowser {
    private data class Song(val id: String, val title: String, val artist: String, val duration: Long, val cover: String?, val album: String = "")

    suspend fun entries(context: Context, category: CacheCategory): List<CacheEntry> = withContext(Dispatchers.IO) {
        CacheCatalog.prune(context) { key -> key.startsWith("/") && !File(key).isFile }
        val labels = CacheCatalog.labels(context).toMutableMap()
        val db = AppDatabase.getInstance(context)
        val songs = db.waveDao().getCacheBrowserTracks(AppDatabase.activeAccountId()).map {
            Song(it.id, it.title, it.artist, it.durationMs, it.coverUrl)
        }.toMutableList()
        PlayerController.getCurrentQueue().forEach { songs.add(Song(it.id, it.title, it.artist, it.durationMs, it.coverUrl, it.albumName)) }
        val urls = linkedMapOf<String, CacheCatalog.Label>()
        fun mapUrl(url: String?, label: CacheCatalog.Label) {
            if (!url.isNullOrBlank()) {
                ItunesArtworkQuality.urls(url).forEach { variant ->
                    urls[variant] = label
                    labels[File(context.cacheDir, "image_cache/${CacheCatalog.hash(variant, "MD5")}").absolutePath] = label
                }
            }
        }
        songs.forEach { song ->
            val label = CacheCatalog.Label(song.title, song.artist)
            labels["audio:lmg_${song.id}"] = label
            mapUrl(song.cover, label)
            if (category == CacheCategory.LYRICS) {
                fun candidate(folder: File, key: String) {
                    val path = File(folder, key).absolutePath
                    val existing = labels[path]
                    if (existing == null || existing.group.isBlank()) labels[path] = label.copy(group = song.duration.toString())
                }
                val normalized = "${normalize(song.title)}\u0000${normalize(song.artist)}\u0000${song.duration.coerceAtLeast(0)}"
                val legacy = "${song.title}\u0000${song.artist}\u0000${song.duration / 1000}"
                listOf("lyrics/ttml", "lyrics/lmg_lyrics_plus").forEach { folder ->
                    candidate(File(context.filesDir, folder), CacheCatalog.hash(normalized) + ".ttml")
                    candidate(File(context.filesDir, folder), CacheCatalog.hash(legacy) + ".ttml")
                }
                candidate(File(context.filesDir, "lyrics/apple_ttml"), CacheCatalog.hash("${song.title.trim().lowercase()}|${song.artist.trim().lowercase()}|${song.duration / 1000}|") + ".ttml")
                listOf("", song.album).distinct().forEach { album ->
                    val key = "${song.artist}|${song.title}|$album|${song.duration / 1000}".lowercase()
                    candidate(File(context.cacheDir, "lrclib"), "lrc_${Integer.toHexString(key.hashCode())}_${song.duration / 1000}.lrc")
                }
            }
            if (category == CacheCategory.ARTWORK) {
                val query = ItunesArtworkMatcher.lookupQuery(ArtworkQuery(song.title, song.artist, song.duration, song.album))
                val itunesKey = listOf(query.title, query.artist, query.durationMs.toString(), query.album).joinToString("\u0000")
                labels.putIfAbsent(File(context.cacheDir, "itunes_artwork_v3/${CacheCatalog.hash(itunesKey)}.json").absolutePath, label)
                labels.putIfAbsent(File(context.cacheDir, "apple_artwork_v3/${CacheCatalog.hash(MotionArtwork.requestUrl(query).toString())}.json").absolutePath, label)
            }
        }
        val result = mutableListOf<CacheEntry>()
        val roots = cacheCategoryDirectories(context.cacheDir, context.filesDir, category)
        // Read artwork lookup metadata before image payloads, so old cover URLs can be recovered.
        if (category == CacheCategory.ARTWORK) {
            roots.filter { it.name.endsWith("_v3") }.forEach { dir -> dir.listFiles().orEmpty().filter { it.extension == "json" }.forEach { file ->
                runCatching {
                    val json = JSONObject(file.readText())
                    val body = json.optString("body").takeIf { it.isNotBlank() }?.let(::JSONObject)
                    val label = labels[file.absolutePath] ?: CacheCatalog.Label(body?.optString("title").orEmpty(), body?.optString("artist").orEmpty())
                    labels[file.absolutePath] = label
                    val url = body?.optString("static_artwork") ?: json.optString("artwork")
                    mapUrl(url, label)
                }
            } }
        }
        roots.forEach { dir -> dir.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { file ->
            val label = labels[file.absolutePath]
            val source = when (dir.name) {
                "ttml", "apple_ttml" -> "Apple TTML"
                "lmg_lyrics_plus" -> "LMG Lyrics Plus"
                "lrclib" -> "LRCLIB"
                "apple_artwork_v3" -> "Apple · данные обложки"
                "itunes_artwork_v3" -> "iTunes · данные обложки"
                else -> "Обложка"
            }
            val fallback = if (category == CacheCategory.LYRICS) lyricExcerpt(file) else "Обложка без названия"
            result.add(CacheEntry(file.absolutePath, label?.title?.takeIf { it.isNotBlank() } ?: fallback,
                label?.artist.orEmpty(), source, file.length(), listOf(file.absolutePath),
                file.takeIf { dir.name == "image_cache" }, label?.group?.takeIf { it.isNotBlank() } ?: file.absolutePath))
        } }
        if (category == CacheCategory.AUDIO || category == CacheCategory.MOTION) {
            val prefix = if (category == CacheCategory.AUDIO) "audio:" else "motion:"
            val resources = if (category == CacheCategory.AUDIO) MediaCacheManager.browserEntries(context)
                else MotionArtworkRepository.browserEntries(context)
            CacheCatalog.prune(context) { it.startsWith(prefix) && it.removePrefix(prefix) !in resources }
            if (category == CacheCategory.MOTION) {
                File(context.cacheDir, "apple_artwork_v3").listFiles().orEmpty().forEach { file ->
                    runCatching {
                        val body = JSONObject(JSONObject(file.readText()).getString("body"))
                        val label = CacheCatalog.Label(body.optString("title"), body.optString("artist"))
                        for (kind in listOf("tall", "square")) {
                            val asset = body.optJSONObject(kind) ?: continue
                            for (field in listOf("m3u8", "mp4")) {
                                val root = asset.optString(field).takeIf { it.startsWith("https://") } ?: continue
                                val known = HashSet<String>()
                                val pending = ArrayDeque<String>()
                                pending.add(root)
                                while (pending.isNotEmpty() && known.size < 2048) {
                                    val key = pending.removeFirst()
                                    if (!known.add(key) || key !in resources) continue
                                    labels.putIfAbsent(prefix + key, label.copy(group = root))
                                    pending.addAll(MotionArtworkRepository.cachedPlaylistLinks(key))
                                }
                            }
                        }
                    }
                }
            }
            resources.entries.groupBy { (key, _) -> labels[prefix + key]?.group?.takeIf { it.isNotBlank() } ?: key }
                .forEach { (group, parts) ->
                    val label = parts.firstNotNullOfOrNull { labels[prefix + it.key] }
                    result.add(CacheEntry(prefix + group,
                        label?.title?.takeIf { it.isNotBlank() } ?: if (category == CacheCategory.AUDIO) "Трек без названия" else "Моушен без названия",
                        label?.artist.orEmpty(), if (category == CacheCategory.AUDIO) "Аудио · ${parts.first().key.removePrefix("lmg_").take(36)}" else "Моушен · ${parts.size} фрагм.",
                        parts.sumOf { it.value }, parts.map { prefix + it.key }))
                }
        }
        if (category == CacheCategory.ARTWORK) {
            val coil = context.imageLoader.diskCache
            val keys = (labels.keys.filter { it.startsWith("coil:") }.map { it.removePrefix("coil:") } + urls.keys).distinct()
            keys.forEach { key ->
                coil?.openSnapshot(key)?.use { snapshot ->
                    val label = urls[key] ?: labels["coil:$key"]?.let { urls[it.group] ?: it }
                    val data = snapshot.data.toFile()
                    result.add(CacheEntry("coil:$key", label?.title?.takeIf { it.isNotBlank() } ?: "Обложка без названия",
                        label?.artist.orEmpty(), "Обложка", data.length() + snapshot.metadata.toFile().length(), listOf("coil:$key"), data))
                }
            }
        }
        mergeCacheEntries(category, result)
    }

    suspend fun remove(context: Context, category: CacheCategory, entry: CacheEntry) = withContext(Dispatchers.IO) {
        when (category) {
            CacheCategory.AUDIO -> MediaCacheManager.removeBrowserEntries(entry.refs.map { it.removePrefix("audio:") })
            CacheCategory.MOTION -> MotionArtworkRepository.removeBrowserEntries(entry.refs.map { it.removePrefix("motion:") })
            else -> {
                if (category == CacheCategory.LYRICS) {
                    // Cancel pending preparation first; other on-disk lyrics are retained.
                    LoadedLyricsStore.clear()
                    LyricsParser.trimCache()
                } else {
                    context.imageLoader.memoryCache?.clear()
                    AppleArtworkRepository.clearMemoryCache()
                    ItunesArtworkRepository.clearMemoryCache()
                }
                entry.refs.forEach { ref ->
                    if (category == CacheCategory.ARTWORK && ref.startsWith("coil:")) {
                        val cache = context.imageLoader.diskCache ?: throw IOException("Image cache unavailable")
                        val key = ref.removePrefix("coil:")
                        if (!cache.remove(key) && cache.openSnapshot(key)?.use { true } == true) throw IOException("Image cache busy")
                    } else {
                        val file = File(ref)
                        deleteCacheFile(file, cacheCategoryDirectories(context.cacheDir, context.filesDir, category))
                    }
                }
            }
        }
        entry.refs.forEach { CacheCatalog.forget(context, it) }
    }

    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    private fun lyricExcerpt(file: File): String = runCatching {
        val text = file.bufferedReader().use { reader -> val buffer = CharArray(32_768); val count = reader.read(buffer); if (count > 0) String(buffer, 0, count) else "" }
        val content = if (file.extension == "ttml") Regex("<p\\b[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1).orEmpty() else text
        content.replace(Regex("<[^>]*>|\\[[^]]*]"), " ").replace(Regex("\\s+"), " ").trim().take(100)
            .ifBlank { "Текст без названия" }
    }.getOrDefault("Текст без названия")
}

internal fun mergeCacheEntries(category: CacheCategory, result: List<CacheEntry>): List<CacheEntry> {
    fun normalized(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    // Keep alternate providers and recordings separate; merge legacy copies and cover resolutions.
    val grouped = result.groupBy { entry ->
        if (entry.artist.isNotBlank() && category in listOf(CacheCategory.LYRICS, CacheCategory.ARTWORK)) {
            val variant = if (category == CacheCategory.LYRICS) entry.source + "\u0000" + entry.recording else "cover"
            "${normalized(entry.title)}\u0000${normalized(entry.artist)}\u0000$variant"
        } else entry.id
    }.values.map { entries ->
        val first = entries.first()
        first.copy(bytes = entries.sumOf { it.bytes }, refs = entries.flatMap { it.refs }.distinct(),
            preview = entries.firstNotNullOfOrNull { it.preview },
            source = if (category == CacheCategory.ARTWORK && entries.size > 1) "Обложка" else first.source)
    }
    return grouped.sortedWith(compareBy<CacheEntry> { it.title.lowercase() }.thenBy { it.source })
}
