package com.lmg.vk.engine.backend

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Терпимый к битым элементам десериализатор списка: декодирует массив поэлементно
 * и ПРОПУСКАЕТ элементы, которые не распарсились (кривой/частичный объект, неверный
 * тип, отсутствует обязательное поле), вместо того чтобы уронить ВЕСЬ список.
 *
 * Применять к UI-спискам: `@Serializable(with = TolerantListSerializer::class)`.
 * Компилятор kotlinx сам подставит сериализатор элемента в конструктор.
 */
class TolerantListSerializer<T>(
    private val element: KSerializer<T>
) : KSerializer<List<T>> {
    private val delegate = ListSerializer(element)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        // Не-JSON формат — обычное поведение.
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val arr = jsonDecoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        val out = ArrayList<T>(arr.size)
        for (e in arr) {
            try {
                out.add(jsonDecoder.json.decodeFromJsonElement(element, e))
            } catch (_: Throwable) {
                // битый элемент — пропускаем, список не роняем
            }
        }
        return out
    }
}

// ─── Health ───

@Serializable
data class LinkedUser(
    @SerialName("lmg_user_id") val userId: Long,
    @SerialName("subscription_upgrade") val subscriptionUpgrade: Boolean,
    @SerialName("effective_stream") val effectiveStream: StreamConfig
)

@Serializable
data class HealthResponse(
    @SerialName("partner_id") val partnerId: String,
    val status: String,
    val scopes: List<String> = emptyList(),
    @SerialName("rate_limits") val rateLimits: RateLimits? = null,
    val stream: StreamConfig? = null,
    val search: SearchConfig? = null,
    @SerialName("server_time") val serverTime: Long? = null,
    @SerialName("linked_user") val linkedUser: LinkedUser? = null
)

@Serializable
data class RateLimits(
    val search: RateLimit? = null,
    val stream: RateLimit? = null,
    @SerialName("session_issue") val sessionIssue: RateLimit? = null,
    val default: RateLimit? = null,
    @SerialName("playlists") val playlists: RateLimit? = null
)

@Serializable
data class RateLimit(
    val rpm: Int,
    val burst: Int
)

@Serializable
data class StreamConfig(
    @SerialName("max_quality") val maxQuality: String,
    @SerialName("allowed_sources") val allowedSources: List<String>,
    @SerialName("signed_url_ttl_seconds") val signedUrlTtlSeconds: Int
)

@Serializable
data class SearchConfig(
    @SerialName("max_results") val maxResults: Int,
    @SerialName("regions_allowed") val regionsAllowed: List<String>
)

// ─── Session ───

@Serializable
data class SessionRequest(
    @SerialName("partner_user_id") val partnerUserId: String,
    @SerialName("hide_explicit") val hideExplicit: Boolean = false
)

@Serializable
data class SessionResponse(
    @SerialName("partner_session_token") val partnerSessionToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    // Дефолты: ответ теперь минтит НАШ сервер-брокер (/session/refresh) —
    // не падаем парсингом, если какое-то поле он не прислал. Брокер заодно
    // владеет premium — поля ниже кэшируются локально при наличии.
    @SerialName("partner_user_id") val partnerUserId: String = "",
    val scopes: List<String> = emptyList(),
    @SerialName("is_premium") val isPremium: Boolean? = null,
    @SerialName("premium_expires_at") val premiumExpiresAt: Long? = null,
    val plan: String? = null
)

// ─── Search ───

@Serializable
data class SearchResponse(
    val query: String,
    val region: String,
    val source: String? = null,
    @Serializable(with = TolerantListSerializer::class)
    val items: List<SearchItem> = emptyList(),
    /** Offset for the next `audio.search` page; independent of merged item count. */
    @SerialName("nextOffset") val nextOffset: Int? = null,
    @SerialName("hasMore") val hasMore: Boolean = false,
)

@Serializable
data class SearchItem(
    val id: String,
    val title: String,
    val artist: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("artistId") val artistId: String? = null,
    val artists: List<MiniArtist> = emptyList(),
    val cover: String? = null,
    val preview: String? = null,
    @SerialName("collectionId") val collectionId: String? = null,
    val album: String? = null,
    @SerialName("is_explicit") val isExplicit: Boolean = false,
    val region: String? = null,
    @SerialName("isArtist") val isArtist: Boolean = false,
    @SerialName("isAlbum") val isAlbum: Boolean = false,
    @SerialName("isCustom") val isCustom: Boolean = false,
    // Видеоклип из Apple-каталога: валидная сущность каталога (числовой id, как у
    // песни), но аудио-стрима у неё нет — POST /track стабильно даёт 404
    // track_not_found. Именно клипы были «битыми треками» из полевых логов.
    @SerialName("isClip") val isClip: Boolean = false,
    val duration: Long? = null,
    val source: String? = null,
    @SerialName("trackId") val trackId: String? = null,
    @SerialName("isAvailable") val isAvailable: Boolean = true,
    /**
     * `access_key` записи VK. Нужен, чтобы `audio.getById` вернул трек ВМЕСТЕ с
     * полем `url`: без ключа ограниченные записи приходят без ссылки, и трек
     * выглядит «не найденным».
     */
    @SerialName("accessKey") val accessKey: String? = null
) {
    /**
     * Пусто, если исполнителя нет: у плейлистов и сборников VK его действительно
     * не бывает. Синтетическая заглушка вместо пустой строки показывала
     * «Unknown Artist» на карточках, где показывать нечего — UI сам скрывает
     * пустой подзаголовок.
     */
    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() && it != "Исполнитель" }
            ?: artistName?.takeIf { it.isNotBlank() && it != "Исполнитель" }
            ?: title.takeIf { isArtist }
            ?: ""

    /** VK returns duration in seconds, Apple in milliseconds. Normalized to ms. */
    val durationMs: Long
        get() = normalizeDurationMs(duration, source)

    /** Трек = не артист, не альбом и не видеоклип (клип не стримится). */
    val isTrack: Boolean
        get() = !isArtist && !isAlbum && !isClip

    val isVk: Boolean
        get() = id.startsWith("vk_") || source == "vk"
}

/** Реальные результаты `execute.SearchInProfile` для третьей вкладки. */
data class ProfileLibrarySearch(
    val query: String,
    val tracks: List<com.lmg.vk.engine.Track> = emptyList(),
    val playlists: List<ProfileLibraryPlaylist> = emptyList(),
)

data class ProfileLibraryPlaylist(
    val id: String,
    val title: String,
    val trackCount: Int,
    val cover: String? = null,
)

// ─── Track (Playback URL) ───

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TrackRequest(
    @SerialName("trackId") val trackId: String,
    val region: String = "us",
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val quality: String? = null
)

@Serializable
data class StreamInfo(
    @SerialName("track_id") val trackId: String,
    @SerialName("file_id") val fileId: String? = null,
    val source: String,
    val quality: String,
    @SerialName("artist_id") val artistId: String? = null,
    val url: String,
    @SerialName("expires_at") val expiresAt: Long
)

// ─── Album ───

@Serializable
data class AlbumResponse(
    val album: Album,
    // tolerant: пропущенный/null tracks не должен ронять парс всего альбома
    @Serializable(with = TolerantListSerializer::class)
    val tracks: List<AlbumTrack> = emptyList()
)

@Serializable
data class Album(
    val id: String = "",
    // Дефолты обязательны: если сервер не прислал title/artist/cover (часто у Apple/VK),
    // без них kotlinx уронил бы парс ВСЕГО альбома → "Unknown Album · 0 tracks".
    val title: String = "",
    val artist: String = "",
    @SerialName("artistId") val artistId: String? = null,
    val cover: String = "",
    @SerialName("motionCoverUrl") val motionCoverUrl: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    val year: String? = null,
    val type: String? = null,
    val genre: String? = null,
    val description: String? = null,
    @SerialName("trackCount") val trackCount: Int? = null,
    val plays: Int? = null,
    val followers: Int? = null,
    @SerialName("createdAt") val createdAt: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    @SerialName("isFollowing") val isFollowing: Boolean = false,
    @SerialName("canFollow") val canFollow: Boolean = false,
    @SerialName("isOwned") val isOwned: Boolean = false,
    val artists: List<MiniArtist> = emptyList(),
    /**
     * Цвет обложки, посчитанный самим VK (`main_color`, hex без `#`).
     *
     * Нужен, чтобы шапка не была серой в первые секунды: обложка ещё грузится, а
     * палитру из неё считать пока не из чего. Как только Coil отдаст bitmap,
     * своя палитра точнее — VK присылает один усреднённый тон.
     */
    @SerialName("mainColor") val mainColor: String? = null,
)

@Serializable
data class AlbumTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    @SerialName("artistId") val artistId: String? = null,
    // Дефолт: одна обложка-null на одном треке не должна ронять весь альбом.
    val cover: String = "",
    @SerialName("collectionId") val collectionId: String? = null,
    @SerialName("is_explicit") val isExplicit: Boolean = false,
    @SerialName("isCustom") val isCustom: Boolean = false,
    val region: String? = null,
    @SerialName("trackNumber") val trackNumber: Int? = null,
    /**
     * Номер части многодискового релиза (`album_part_number` у VK).
     *
     * Без него двойник выглядел как один сплошной список из 30 треков, где
     * нумерация зачем-то доходит до 30, хотя на каждом диске своя с единицы.
     */
    @SerialName("discNumber") val discNumber: Int? = null,
    val duration: Long? = null,
    val source: String? = null,
    @SerialName("isAvailable") val isAvailable: Boolean = true
) {
    val durationMs: Long
        get() = normalizeDurationMs(duration, source)
}

// ─── Artist ───

@Serializable
data class ArtistResponse(
    val id: String,
    val name: String,
    val genre: String? = null,
    val url: String? = null,
    val image: String? = null,
    val cover: String? = null,
    val bio: String? = null,
    val followers: Long? = null,
    @SerialName("isFollowed") val isFollowed: Boolean = false,
    @SerialName("canFollow") val canFollow: Boolean = false,
    @SerialName("mixId") val mixId: String? = null,
    @SerialName("editorialVideoUrl") val editorialVideoUrl: String? = null,
    @SerialName("topSongs")
    @Serializable(with = TolerantListSerializer::class)
    val topSongs: List<ArtistSong> = emptyList(),
    @SerialName("latestRelease") val latestRelease: ArtistAlbum? = null,
    @Serializable(with = TolerantListSerializer::class)
    val albums: List<ArtistAlbum> = emptyList(),
    val singles: List<ArtistAlbum> = emptyList(),
    val featuring: List<ArtistAlbum> = emptyList(),
    @SerialName("similarArtists") val similarArtists: List<SimilarArtist> = emptyList(),
    val playlists: List<ArtistPlaylist> = emptyList(),
    @SerialName("appearsOn") val appearsOn: List<ArtistAlbum> = emptyList(),
    @SerialName("officialPages") val officialPages: List<ArtistOfficialPage> = emptyList(),
    @SerialName("linkedArtists") val linkedArtists: List<SimilarArtist> = emptyList(),
    val links: List<ArtistLink> = emptyList(),
    val videos: List<ArtistVideo> = emptyList(),
    @SerialName("source") val source: String? = null
) {
    val isVk: Boolean
        get() = id.startsWith("vk_") || source == "vk"
}

/** One ordinary-offset page returned by `audio.getAudiosByArtist`. */
data class ArtistTrackPage(
    val tracks: List<com.lmg.vk.engine.Track>,
    val nextOffset: Int?,
    val hasMore: Boolean,
)

@Serializable
data class ArtistSong(
    val id: String = "",
    // Дефолты: один трек артиста без cover/artist раньше ронял парс ВСЕГО
    // ответа артиста (топ-треки + дискография). coerceInputValues маппит null → "".
    val title: String = "",
    val artist: String = "",
    @SerialName("artistId") val artistId: String? = null,
    val artists: List<MiniArtist> = emptyList(),
    val cover: String = "",
    @SerialName("albumName") val albumName: String? = null,
    @SerialName("isAlbum") val isAlbum: Boolean = false,
    @SerialName("is_explicit") val isExplicit: Boolean = false,
    @SerialName("isCustom") val isCustom: Boolean = false,
    val region: String? = null,
    val source: String? = null,
    val duration: Long? = null,
    @SerialName("isAvailable") val isAvailable: Boolean = true
) {
    val isVk: Boolean
        get() = id.startsWith("vk_") || source == "vk"

    /** VK returns duration in seconds, Apple in milliseconds. Normalized to ms. */
    val durationMs: Long
        get() = normalizeDurationMs(duration, source)
}

@Serializable
data class MiniArtist(
    val id: String? = null,
    val name: String? = null
) {
    val displayName: String
        get() = name.orEmpty()
}

@Serializable
data class ArtistAlbum(
    val id: String = "",
    // Дефолты: одна строка дискографии без cover/title не должна ронять весь ответ артиста.
    val title: String = "",
    val artist: String = "",
    val artists: List<MiniArtist> = emptyList(),
    val year: String? = null,
    val date: String? = null,
    val cover: String = "",
    val type: String? = null,
    @SerialName("isAlbum") val isAlbum: Boolean = false,
    val timestamp: Long? = null,
)

@Serializable
data class SimilarArtist(
    val id: String,
    val name: String? = null,
    val url: String? = null,
    val cover: String? = null
) {
    val displayName: String
        get() = name.orEmpty()
}

@Serializable
data class ArtistPlaylist(
    val id: String,
    val title: String,
    val cover: String? = null
)

@Serializable
data class ArtistOfficialPage(
    val id: Long,
    val name: String,
    val cover: String? = null,
    val subtitle: String? = null,
    @SerialName("isFollowed") val isFollowed: Boolean = false,
    @SerialName("isCommunity") val isCommunity: Boolean = false,
)

@Serializable
data class ArtistLink(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val url: String,
    val cover: String? = null,
)

@Serializable
data class ArtistVideo(
    val id: String,
    val title: String,
    val cover: String? = null,
    val duration: Long = 0L,
    val url: String? = null,
)

// ─── Chart ───

@Serializable
data class Chart(
    val id: String,
    val name: String,
    val query: String,
    val cover: String? = null,
    @Serializable(with = TolerantListSerializer::class)
    val tracks: List<SearchItem> = emptyList()
)

// ─── Track Meta ───

@Serializable
data class TrackMeta(
    val id: String = "",
    @SerialName("collectionId") val collectionId: String? = null,
    // Дефолты: cover/duration часто отсутствуют у VK/secondary треков — раньше
    // одно отсутствующее поле обнуляло весь ответ getTrackMeta.
    val title: String = "",
    val artist: String = "",
    val cover: String = "",
    val duration: Long = 0L,
    // Жанр — для ленивого резолва в жанровых чипах волны (more/less_genre).
    @SerialName("genre") val genre: String? = null
) {
    /** Apple шлёт мс, VK — секунды (у модели нет source — фолбэк по величине). */
    val durationMs: Long
        get() = normalizeDurationMs(duration, null)
}

// ─── Playlist ───

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val curator: String? = null,
    val description: String? = null,
    val cover: String? = null,
    @Serializable(with = TolerantListSerializer::class)
    val tracks: List<PlaylistTrack> = emptyList()
)

@Serializable
data class PlaylistPreviewRequest(
    @SerialName("source") val source: String,
    @SerialName("url") val url: String
)

@Serializable
data class PlaylistTrack(
    val id: String,
    val title: String,
    val artist: String,
    @SerialName("artistId") val artistId: String,
    val cover: String,
    @SerialName("collectionId") val collectionId: String,
    val duration: Long,
    @SerialName("is_explicit") val isExplicit: Boolean = false,
    @SerialName("isCustom") val isCustom: Boolean = false,
    @SerialName("isAvailable") val isAvailable: Boolean = true
) {
    // У модели нет поля source — нормализация по величине (фолбэк).
    val durationMs: Long
        get() = normalizeDurationMs(duration, null)
}

// ─── Cover Sign ───

@Serializable
data class CoverSignResponse(
    val url: String,
    @SerialName("expires_at") val expiresAt: Long
)

// ─── Lyrics ───

@Serializable
data class LyricsResponse(
    @SerialName("track_id") val trackId: String,
    val lyrics: String? = null,
    val synced: Boolean = false,
    val source: String? = null,
    val format: String? = null
)

// ─── Errors ───

@Serializable
data class Error(
    @SerialName("error") val error: String,
    @SerialName("message") val message: String? = null,
    @SerialName("required_region") val requiredRegion: String? = null,
    @SerialName("retry_after") val retryAfter: Int? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("attempts_left") val attemptsLeft: Int? = null
)

@Serializable
data class ErrorWrapper(
    @SerialName("detail") val detail: Error
)


// ─── Batch Track Meta ───

@Serializable
data class BatchTrackMetaRequest(
    @SerialName("track_ids") val trackIds: List<String>
)

@Serializable
data class BatchTrackMetaResponse(
    val count: Int? = null,
    val items: List<BatchTrackMetaItem> = emptyList()
)

@Serializable
data class BatchTrackMetaItem(
    val id: String,
    val title: String? = null,
    val artist: String? = null,
    val cover: String? = null,
    val duration: Long? = null,
    @SerialName("collectionId") val collectionId: String? = null,
    @SerialName("track_id") val trackId: String? = null,
    val error: String? = null
) {
    val isSuccess: Boolean
        get() = error == null && title != null

    val isError: Boolean
        get() = error != null

    /** У модели нет поля source — нормализация по величине (фолбэк). */
    val durationMs: Long
        get() = normalizeDurationMs(duration, null)
}

// ─── Async Track ───

@Serializable
data class AsyncTrackPending(
    @SerialName("job_id") val jobId: String,
    val status: String = "pending",
    @SerialName("poll_url") val pollUrl: String? = null,
    // Дока: поле называется poll_after (раньше стояло poll_after_seconds —
    // никогда не биндилось, интервал молча падал в дефолт).
    @SerialName("poll_after") val pollAfterSeconds: Int = 3
)

/**
 * Ответ полла /track/job/{id}. ВСЕ поля опциональные: pending-ответ содержит
 * только status (+job_id), ready-ответ — «как обычный /track» БЕЗ job_id
 * (дока). Раньше обязательные поля роняли парсер на первом же pending-полле,
 * цикл считал это фаталом — «холодные» треки (202) не стартовали вообще.
 */
@Serializable
data class AsyncTrackReady(
    @SerialName("job_id") val jobId: String? = null,
    val status: String = "pending",
    @SerialName("track_id") val trackId: String? = null,
    @SerialName("file_id") val fileId: String? = null,
    val source: String? = null,
    val quality: String? = null,
    @SerialName("artist_id") val artistId: String? = null,
    val url: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null
)

// ─── Account Linking ───

@Serializable
data class AccountLinkUrl(
    val url: String,
    @SerialName("expires_at") val expiresAt: Long? = null
)

@Serializable
data class AccountLinkCallback(
    val state: String,
    val linked: Boolean,
    @SerialName("lmg_user_id") val userId: String? = null,
    val error: String? = null
)

// ─── Domain Model Conversion ───

fun SearchItem.toTrack(): com.lmg.vk.engine.Track {
    return com.lmg.vk.engine.Track(
        id = id,
        title = title,
        artist = displayArtist,
        albumName = album ?: collectionId ?: "Single",
        // Не используем preview или сторонний resolver как playback URI.
        // Реальный URL возвращает VK; при его отсутствии плеер разрешит fullId
        // через audio.getById непосредственно перед воспроизведением.
        uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
        // VK возвращает длительность в секундах; модель нормализует её в мс.
        // Reuse the model's normalized accessor so the progress bar shows the right scale.
        durationMs = durationMs,
        albumId = collectionId?.hashCode()?.toLong() ?: id.hashCode().toLong(),
        coverUrl = cover?.replace("1000x1000", "600x600") ?: cover,
        // Сервер часто шлёт artistId скаляром при ПУСТОМ списке artists —
        // без фолбэка артист в плеере был некликабелен для треков из поиска.
        artists = artists.ifEmpty {
            artistId?.let { listOf(MiniArtist(id = it, name = displayArtist)) } ?: emptyList()
        },
        isExplicit = isExplicit,
        isCustom = isCustom,
        source = source,
        isAvailable = isAvailable
    )
}

fun AlbumTrack.toTrack(): com.lmg.vk.engine.Track {
    return com.lmg.vk.engine.Track(
        id = id,
        title = title,
        artist = artist,
        albumName = "",
        uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
        durationMs = durationMs,
        albumId = collectionId?.hashCode()?.toLong() ?: id.hashCode().toLong(),
        coverUrl = cover.replace("1000x1000", "600x600"),
        // artistId есть в модели — прокидываем, чтобы артист был кликабелен
        artists = if (artistId != null)
            listOf(MiniArtist(id = artistId, name = artist))
        else emptyList(),
        isExplicit = isExplicit,
        isCustom = isCustom,
        source = source,
        isAvailable = isAvailable
    )
}

fun ArtistSong.toTrack(): com.lmg.vk.engine.Track {
    return com.lmg.vk.engine.Track(
        id = id,
        title = title,
        artist = artists.firstOrNull()?.displayName?.takeIf { it.isNotBlank() }
            ?: artist.takeIf { it.isNotBlank() }
            ?: "",
        albumName = albumName ?: "",
        uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
        durationMs = durationMs,
        albumId = 0L,
        coverUrl = cover.replace("300x300", "600x600"),
        artists = artists,
        isExplicit = isExplicit,
        isCustom = isCustom,
        source = source,
        isAvailable = isAvailable
    )
}

fun PlaylistTrack.toTrack(): com.lmg.vk.engine.Track {
    return com.lmg.vk.engine.Track(
        id = id,
        title = title,
        artist = artist,
        albumName = "",
        uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
        durationMs = durationMs,
        albumId = collectionId.hashCode().toLong(),
        coverUrl = cover.replace("1000x1000", "600x600"),
        artists = emptyList(),
        isExplicit = isExplicit,
        isCustom = isCustom,
        isAvailable = isAvailable,
    )
}


/**
 * Нормализация длительности в мс. По доке: Apple шлёт МИЛЛИСЕКУНДЫ,
 * VK/secondary — СЕКУНДЫ. Старая эвристика «< 1000 значит секунды» ломала
 * длинные VK-треки (≥ 1000с ≈ 16.7 мин трактовались как миллисекунды —
 * «трек длиной одну секунду»). Порог 30 000 у VK: любой сет короче 8+ часов
 * в секундах, а в мс это было бы < 30-секундным треком.
 */
internal fun normalizeDurationMs(raw: Long?, source: String?): Long {
    val d = raw ?: return 0L
    val s = source?.lowercase()
    val vkLike = s != null && (s.startsWith("vk") || s.startsWith("secondary"))
    return when {
        vkLike && d < 30_000L -> d * 1000L
        vkLike -> d
        // Фолбэк для неизвестного источника: значения до 10 000 — секунды
        // (трека короче 10с в мс не бывает, а 10 000с = 2ч46м покрывает даже
        // длинные миксы). Прежний порог 1000 ломал треки длиннее 16.6 мин,
        // приходящие в секундах без source.
        d < 10_000L -> d * 1000L
        else -> d
    }
}

// ─── Error Codes ───

object ErrorCodes {
    const val MISSING_API_KEY = "missing_api_key"
    const val INVALID_SESSION_TOKEN = "invalid_session_token"
    const val INVALID_API_KEY = "invalid_api_key"
    const val PARTNER_SUSPENDED = "partner_suspended"
    const val SCOPE_NOT_ALLOWED = "scope_not_allowed"
    const val SOURCE_NOT_ALLOWED = "source_not_allowed"
    const val INVALID_OR_EXPIRED_SIGNATURE = "invalid_or_expired_signature"
    const val TRACK_NOT_FOUND = "track_not_found"
    const val RATE_LIMITED = "rate_limited"
    const val REGION_UNAVAILABLE = "region_unavailable"
    const val NOT_FOUND = "not_found"
    const val QUERY_TOO_SHORT = "query_too_short"
    const val QUERY_SPAM_DETECTED = "query_spam_detected"
    const val EARLY_ACCESS = "early_access"
    const val SUBSCRIPTION_REQUIRED = "subscription_required"
    const val USER_NOT_LINKED = "user_not_linked"
    // Дополнено по доке (аудит): auth/regions/email-link
    const val S2S_ONLY = "s2s_only"
    const val TOKEN_REVOKED = "token_revoked"
    const val PARTNER_USER_REQUIRED = "partner_user_required"
    const val INVALID_API_KEY_FORMAT = "invalid_api_key_format"
    const val INVALID_REGION = "invalid_region"
    const val REGION_NOT_ALLOWED_BY_PARTNER = "region_not_allowed_by_partner"
    const val INVALID_NONCE = "invalid_nonce"
    const val INVALID_OTP = "invalid_otp"
    const val NONCE_LOCKED = "nonce_locked"
    const val NONCE_BELONGS_TO_ANOTHER_PARTNER = "nonce_belongs_to_another_partner"
}

// ─── Search Source ───

object SearchSource {
    const val PRIMARY = "primary"
    const val SECONDARY = "secondary"
    const val ALL = "all"

    // Legacy aliases for backward compatibility
    const val APPLE = PRIMARY
    const val VK = SECONDARY
}

// ─── Stream Quality ───

object StreamQuality {
    const val K128 = "128K"
    const val K256 = "256K"
    const val K320 = "320K"
    const val ALAC = "ALAC"
}

// ─── Personal Cabinet (/me/*) ───


@Serializable
data class UserPreferences(
    @SerialName("partner_user_id") val partnerUserId: String? = null,
    @SerialName("quality_preference") val qualityPreference: String? = null,
    @SerialName("max_quality") val maxQuality: String? = null,
    @SerialName("allowed_qualities") val allowedQualities: List<String> = emptyList(),
    @SerialName("updated_at") val updatedAt: Long? = null
)

@Serializable
data class UpdatePreferencesRequest(
    @SerialName("quality") val quality: String?
)

@Serializable
data class UserProfile(
    @SerialName("partner_user_id") val partnerUserId: String? = null,
    val name: String? = null,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

// ─── Wave (Personal Radio) ───

@Serializable
data class WaveResponse(
    val track: WaveTrack? = null,
    val status: String = "",
    val region: String? = null
)

@Serializable
data class WaveTrack(
    val id: String,
    val title: String,
    val artist: String? = null,
    @SerialName("artistId") val artistId: String? = null,
    val cover: String? = null,
    val duration: Long? = null,
    @SerialName("collectionId") val collectionId: String? = null,
    @SerialName("is_explicit") val isExplicit: Boolean = false,
    @SerialName("isCustom") val isCustom: Boolean = false,
    // Видеоклип: каталог волны его отдаёт, но стрима нет (404 track_not_found) —
    // WaveRepository режет такие ДО очереди, не тратя запрос /track.
    @SerialName("isClip") val isClip: Boolean = false,
    val source: String? = null
) {
    val durationMs: Long
        get() = normalizeDurationMs(duration, source)

    fun toTrack(): com.lmg.vk.engine.Track {
        return com.lmg.vk.engine.Track(
            id = id,
            title = title,
            artist = artist.orEmpty(),
            albumName = "",
            uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
            durationMs = durationMs,
            albumId = collectionId?.hashCode()?.toLong() ?: id.hashCode().toLong(),
            coverUrl = cover?.replace("1000x1000", "600x600"),
            // artistId сервер отдаёт — без него артист в FullPlayer был
            // некликабельным для ВСЕХ треков волны (главный сценарий).
            artists = if (artistId != null)
                listOf(MiniArtist(id = artistId, name = artist))
            else emptyList(),
            isExplicit = isExplicit,
            isCustom = isCustom,
            source = source
        )
    }
}

// ─── Library (likes, subscriptions) ───

@Serializable
data class LibraryLikesResponse(
    @Serializable(with = TolerantListSerializer::class)
    val items: List<LibraryTrack> = emptyList(),
    val count: Int? = null,
    val total: Int? = null,
    val offset: Int? = null,
    val limit: Int? = null
)

@Serializable
data class LikeRequest(
    @SerialName("track_id") val trackIdSnake: String,
    @SerialName("trackId") val trackIdCamel: String
)

@Serializable
data class LikeResponse(
    @SerialName("status") val status: String? = null,
    @SerialName("ok") val ok: Boolean = false,
    @SerialName("logged") val logged: Boolean = false,
    // POST /library/likes теперь toggle (changelog backend 2026-07-11): в ответе
    // liked = ИТОГОВОЕ состояние. Nullable: старый сервер/omit → null → откат
    // на HTTP-2xx = успех.
    @SerialName("liked") val liked: Boolean? = null
)

@Serializable
data class LibrarySubscriptionsResponse(
    val items: List<LibraryArtist> = emptyList(),
    val count: Int? = null,
    val total: Int? = null,
    val offset: Int? = null,
    val limit: Int? = null
)

@Serializable
data class LibraryTrack(
    val id: String,
    @SerialName("trackId") val trackId: String? = null,
    val title: String,
    val artist: String? = null,
    @SerialName("artistId") val artistId: String? = null,
    val cover: String? = null,
    val duration: Long? = null,
    @SerialName("collectionId") val collectionId: String? = null,
    @SerialName("is_explicit") val isExplicit: Boolean = false,
    @SerialName("isCustom") val isCustom: Boolean = false,
    val source: String? = null,
    @SerialName("liked_at") val likedAt: Long? = null,
    @SerialName("isAvailable") val isAvailable: Boolean = true,
    /**
     * `access_key` записи VK, сохранённый вместе с элементом библиотеки.
     * Без него `audio.getById` может вернуть трек без URL после перезапуска.
     */
    @SerialName("accessKey") val accessKey: String? = null
) {
    /** Duration normalized to milliseconds. */
    val durationMs: Long
        get() = normalizeDurationMs(duration, source)
}

@Serializable
data class LibraryArtist(
    val id: String,
    val name: String? = null,
    val cover: String? = null,
    val image: String? = null,
    @SerialName("isCustom") val isCustom: Boolean = false,
    val source: String? = null
) {
    val displayName: String
        get() = name.orEmpty()

    /** Prefer the explicit image field, fallback to cover. */
    val displayImage: String?
        get() = image ?: cover
}

// ─── Wave Feedback ───

@Serializable
data class WaveFeedbackRequest(
    @SerialName("feedback_type") val feedbackType: String,
    @SerialName("value") val value: String
)

@Serializable
data class WaveFeedbackResponse(
    @SerialName("ok") val ok: Boolean = false
)

@Serializable
data class WaveResetResponse(
    @SerialName("status") val status: String,
    @SerialName("removed") val removed: Int
) {
    val isSuccess: Boolean get() = status == "ok"
}

// ─── Wave Playback Logging ───

@Serializable
data class WavePlaybackRequest(
    @SerialName("track_id") val trackId: String,
    @SerialName("played_seconds") val playedSeconds: Double,
    @SerialName("total_seconds") val totalSeconds: Double? = null,
    @SerialName("completed") val completed: Boolean? = null,
    @SerialName("skipped") val skipped: Boolean? = null
)

@Serializable
data class WavePlaybackResponse(
    @SerialName("status") val status: String,
    @SerialName("logged") val logged: Boolean = false
)

// ─── Email Account Linking ───

@Serializable
data class EmailLinkRequest(
    @SerialName("partner_user_id") val partnerUserId: String,
    @SerialName("email") val email: String,
    @SerialName("state") val state: String? = null
)

@Serializable
data class EmailLinkResponse(
    @SerialName("sent") val sent: Boolean = false,
    @SerialName("nonce") val nonce: String,
    @SerialName("expires_in") val expiresIn: Int
)

@Serializable
data class EmailVerifyRequest(
    @SerialName("nonce") val nonce: String,
    @SerialName("otp") val otp: String
)

@Serializable
data class EmailVerifyResponse(
    @SerialName("linked") val linked: Boolean = false,
    @SerialName("lmg_user_id") val userId: Long? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("password_issued") val passwordIssued: Boolean = false
)

@Serializable
data class PasswordChangeRequest(
    @SerialName("partner_user_id") val partnerUserId: String,
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class PasswordChangeResponse(
    @SerialName("changed") val changed: Boolean = false
)

@Serializable
data class PasswordResetRequest(
    @SerialName("partner_user_id") val partnerUserId: String
)

@Serializable
data class PasswordResetResponse(
    val reset: Boolean = false
)

// ═══════════════════════════════════════════════════════════
//  Video Clips (Apple Music only, scope "clips", premium)
//  Дока backend (2026-07-22): /clips/search, /clips/resolve, /clips/job
// ═══════════════════════════════════════════════════════════

@Serializable
data class ClipItem(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val thumbnail: String? = null,
    val duration: Int = 0,
    @SerialName("has4K") val has4K: Boolean = false,
    @SerialName("hasHDR") val hasHDR: Boolean = false
)

@Serializable
data class ClipSearchResponse(
    val results: List<ClipItem> = emptyList()
)

/** Ответ /clips/resolve и /clips/job: status ready|pending|failed. */
@Serializable
data class ClipResolveResponse(
    val status: String = "",
    @SerialName("clip_id") val clipId: String? = null,
    @SerialName("file_id") val fileId: String? = null,
    val quality: Int? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    // pending
    @SerialName("job_id") val jobId: String? = null,
    @SerialName("poll_url") val pollUrl: String? = null,
    @SerialName("poll_after") val pollAfter: Int? = null,
    // failed
    val error: String? = null
)

// ═══════════════════════════════════════════════════════════
//  Home Screen Models (Banners, New Releases, Charts)
// ═══════════════════════════════════════════════════════════

/**
 * A generic content block returned by the backend for the home screen.
 * Each block has a title, type, and a list of items.
 */
@Serializable
data class HomeBlock(
    val id: String,
    val title: String,
    val type: String, // "banner", "new_releases", "charts", "recommendations"
    @Serializable(with = TolerantListSerializer::class)
    val items: List<HomeItem> = emptyList(),
    /** CatalogKit layout discriminator (`slider`, `triple_stacked_slider`, …). */
    @SerialName("layoutName") val layoutName: String = "",
    /**
     * `Catalog2Block.next_from` — курсор следующей порции элементов ЭТОГО блока.
     * Раньше терялся при сборке HomeBlock, поэтому шторка «показать все» всегда
     * показывала только первую порцию, сколько бы её ни листали.
     */
    @SerialName("nextFrom") val nextFrom: String? = null,
    /** Optional server context for catalog.getBlockItems. */
    @SerialName("catalogRef") val catalogRef: String? = null,
    /**
     * Табы подраздела для `layoutName == "subsection_tabs"`.
     * Приходят не в layout, а в `actions[0].options` — см. [HomeSubsectionTab].
     */
    @SerialName("subsectionTabs") val subsectionTabs: List<HomeSubsectionTab> = emptyList(),
)

/**
 * A single item inside a home block.
 * Can represent a track, album, artist, or promotional card.
 */
@Serializable
data class HomeItem(
    val id: String,
    val title: String,
    val artist: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("artistId") val artistId: String? = null,
    val cover: String? = null,
    @SerialName("collectionId") val collectionId: String? = null,
    val album: String? = null,
    @SerialName("is_explicit") val isExplicit: Boolean = false,
    val region: String? = null,
    val duration: Long? = null,
    val source: String? = null,
    @SerialName("trackId") val trackId: String? = null,
    @SerialName("rank") val rank: Int? = null,
    @SerialName("subtitle") val subtitle: String? = null,
    @SerialName("genre") val genre: String? = null,
    // Тип сущности. Раньше его теряли при конвертации search→home, и UI решал
    // «альбом или трек» по наличию collectionId — но трек по API ТОЖЕ несёт
    // collectionId (id своего альбома), поэтому тап по треку открывал альбом.
    @SerialName("isAlbum") val isAlbum: Boolean = false,
    /** VK catalog playlist that is not an album/single release. */
    @SerialName("isPlaylist") val isPlaylist: Boolean = false,
    @SerialName("isArtist") val isArtist: Boolean = false,
    @SerialName("isClip") val isClip: Boolean = false,
    /** Server CatalogKit promotional/editorial card without an audio stream. */
    @SerialName("isCustom") val isCustom: Boolean = false,
    @SerialName("isAvailable") val isAvailable: Boolean = true,
    /** Nested `AudioStreamMix.stream_mix.id`, not the CatalogKit item id. */
    @SerialName("streamMixId") val streamMixId: String? = null,
    @SerialName("streamMixTunable") val streamMixTunable: Boolean = false,
    @SerialName("streamMixEntityId") val streamMixEntityId: String? = null,
    @SerialName("streamMixSectionId") val streamMixSectionId: String? = null,
    @SerialName("streamMixCatalogItemId") val streamMixCatalogItemId: String? = null,
    @SerialName("streamMixAnimationUrl") val streamMixAnimationUrl: String? = null,
    @SerialName("streamMixOptions") val streamMixOptions: Map<String, List<String>> = emptyMap(),
    /** `play_vk_mix` resolves settings before constructing StartPlayVkMixSource. */
    @SerialName("streamMixResolveSettings") val streamMixResolveSettings: Boolean = false,
    /** VK profile/community whose music is exposed by the catalog as a curator. */
    @SerialName("musicOwnerId") val musicOwnerId: Long? = null,
    /** Catalog block which acts as StartPlayCatalogSource for Autoflow. */
    @SerialName("catalogBlockId") val catalogBlockId: String? = null,
) {
    /** A CatalogKit Mix is custom content, but unlike a promo card it is playable. */
    val isStreamMix: Boolean
        get() = !streamMixId.isNullOrBlank()

    val isMusicOwner: Boolean
        get() = musicOwnerId != null

    /** Keep action-less custom cards disabled while allowing server Mix cards. */
    val isInteractive: Boolean
        get() = !isCustom || isStreamMix || isMusicOwner

    /** Трек = не альбом, не артист и не видеоклип (клип не стримится). */
    val isTrack: Boolean
        get() = !isAlbum && !isPlaylist && !isArtist && !isClip && !isCustom

    /** Пусто — значит исполнителя нет; UI прячет строку, а не пишет заглушку. */
    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() && it != "Исполнитель" }
            ?: artistName?.takeIf { it.isNotBlank() && it != "Исполнитель" }
            ?: subtitle?.takeIf { it.isNotBlank() }
            ?: ""

    /** VK returns duration in seconds, Apple in milliseconds. Normalized to ms. */
    val durationMs: Long
        get() = normalizeDurationMs(duration, source)
}

/**
 * Full home screen response — a list of content blocks.
 * This is what синтетический home-контент: собирается КЛИЕНТОМ из /search-запросов (эндпоинта /home в партнёрском API нет)
 * or what we construct from multiple API calls.
 */
@Serializable
data class HomeResponse(
    val blocks: List<HomeBlock> = emptyList(),
    @SerialName("updated_at") val updatedAt: Long? = null
)

// ─── Subscription ───

@Serializable
data class SubscriptionResponse(
    val active: Boolean,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("expires_at_iso") val expiresAtIso: String? = null,
    @SerialName("days_left") val daysLeft: Int = 0,
    @SerialName("plan_type") val planType: String? = null,
    @SerialName("is_family_owner") val isFamilyOwner: Boolean = false,
    @SerialName("is_family_member") val isFamilyMember: Boolean = false,
    val regions: List<SubscriptionRegion> = emptyList()
) {
    /** Whether subscription is currently active and not expired. */
    val isActive: Boolean
        get() = active && (daysLeft > 0 || expiresAt == null)

    /** Whether subscription has expired. */
    val isExpired: Boolean
        get() = !active || (daysLeft <= 0 && expiresAt != null)

    /** Subscription tier name (plan_type or default). */
    val tier: String
        get() = planType ?: "standard"
}

@Serializable
data class SubscriptionRegion(
    val code: String,
    val name: String,
    @SerialName("expires_at") val expiresAt: Long? = null
)

// ─── Region ───

@Serializable
data class RegionResponse(
    val current: String,
    val available: List<AvailableRegion> = emptyList(),
    @SerialName("allowed_by_partner") val allowedByPartner: List<String> = emptyList(),
    @SerialName("requires_subscription") val requiresSubscription: List<String> = emptyList()
)

@Serializable
data class AvailableRegion(
    val code: String,
    val name: String,
    val free: Boolean = false,
    @SerialName("expires_at") val expiresAt: Long? = null
)

@Serializable
data class UpdateRegionRequest(
    @SerialName("region") val region: String
)

@Serializable
data class UpdateRegionResponse(
    @SerialName("region") val region: String
)

// ─── Playlist Management ───

@Serializable
data class UserPlaylistsResponse(
    @SerialName("count") val count: Int? = null,
    @SerialName("total") val total: Int? = null,
    @SerialName("offset") val offset: Int? = null,
    @SerialName("limit") val limit: Int? = null,
    @SerialName("items") val items: List<UserPlaylist> = emptyList()
)

@Serializable
data class UserPlaylist(
    // API returns Int for playlist id — accept both Int and String
    @SerialName("id") val idRaw: JsonElement? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("track_count") val trackCount: Int? = null,
    @SerialName("cover") val cover: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null
) {
    /** Normalized playlist id as String (handles both Int and String from API) */
    val id: String?
        get() = idRaw?.let {
            when {
                it is JsonPrimitive && it.isString -> it.content
                it is JsonPrimitive && !it.isString -> it.longOrNull?.toString()
                else -> null
            }
        }
}

@Serializable
data class UserPlaylistTracksResponse(
    @SerialName("playlist") val playlist: UserPlaylistInfo? = null,
    @SerialName("tracks")
    @Serializable(with = TolerantListSerializer::class)
    val tracks: List<UserPlaylistTrack> = emptyList()
)

@Serializable
data class UserPlaylistInfo(
    // API returns Int for playlist id — accept both Int and String
    @SerialName("id") val idRaw: JsonElement? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("source") val source: String? = null,
    @SerialName("track_count") val trackCount: Int? = null
) {
    /** Normalized playlist id as String (handles both Int and String from API) */
    val id: String?
        get() = idRaw?.let {
            when {
                it is JsonPrimitive && it.isString -> it.content
                it is JsonPrimitive && !it.isString -> it.longOrNull?.toString()
                else -> null
            }
        }
}

@Serializable
data class UserPlaylistTrack(
    @SerialName("trackId") val trackIdRaw: JsonElement? = null,
    @SerialName("id") val idRaw: JsonElement? = null,
    @SerialName("track_id") val trackIdUnderscore: JsonElement? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("cover") val cover: String? = null,
    @SerialName("duration") val duration: Long? = null,
    @SerialName("collectionId") val collectionId: String? = null,
    @SerialName("position") val position: Int? = null
) {
    val trackId: String?
        get() = (trackIdRaw ?: idRaw ?: trackIdUnderscore)?.let {
            when {
                it is JsonPrimitive && it.isString -> it.content
                it is JsonPrimitive && !it.isString -> it.longOrNull?.toString()
                else -> null
            }
        }
}

@Serializable
data class DeletePlaylistResponse(
    val deleted: Boolean = true
)
