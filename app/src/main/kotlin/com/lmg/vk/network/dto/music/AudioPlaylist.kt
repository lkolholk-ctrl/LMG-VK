package com.lmg.vk.network.dto.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Восстановлено из `ua.lmg.vkapi2.objects.music.playlist.AudioPlaylist`
 * (31 ключ Moshi) и `playlist.album.AudioAlbum`.
 */
@JsonClass(generateAdapter = true)
data class AudioPlaylist(
    val id: Int,
    val owner_id: Long,
    val type: String? = null,
    val album: AlbumMeta? = null,
    val title: String,
    val description: String? = null,
    val count: Int = 0,
    val followers: Int = 0,
    val plays: Int = 0,
    val create_time: Long = 0L,
    val update_time: Long? = null,
    val genres: List<Genre>? = null,
    val is_following: Boolean? = null,
    val is_curator: Boolean? = null,
    val audios: List<AudioTrack>? = null,
    val year: Int = 0,
    val followed: FollowedMetadata? = null,
    val original: OriginalPlaylist? = null,
    val photo: AlbumThumb? = null,
    val thumbs: List<AlbumThumb>? = null,
    val access_key: String? = null,
    val is_explicit: Boolean? = null,
    val subtitle: String? = null,
    val main_artists: List<MainArtist>? = null,
    val subtitle_badge: Boolean = false,
    val no_discover: Boolean = false,
    val audio_chart_info: AudioChartInfo? = null,
    val meta: AudioPlaylistMeta? = null,
    val restriction: MusicDynamicRestriction? = null,
    val permissions: AudioPlaylistPermissions? = null,
    val main_color: String? = null,
) {
    val fullId: String get() = "${owner_id}_$id"

    /** Из вложенного `AudioPlaylist.AlbumMeta`. */
    @JsonClass(generateAdapter = true)
    data class AlbumMeta(
        val id: String? = null,
        val title: String? = null,
    )
}

/** Из `ua.lmg.vkapi2.objects.music.playlist.AudioPlaylistPermissions`. */
@JsonClass(generateAdapter = true)
data class AudioPlaylistPermissions(
    @Json(name = "save_as_copy") val saveAsCopy: Boolean = false,
    @Json(name = "follow") val follow: Boolean = false,
    @Json(name = "delete") val delete: Boolean = false,
    @Json(name = "edit") val edit: Boolean = false,
    @Json(name = "share") val share: Boolean = false,
    @Json(name = "play") val play: Boolean = false,
)

/** Из `ua.lmg.vkapi2.objects.music.playlist.album.AudioAlbum`. */
@JsonClass(generateAdapter = true)
data class AudioAlbum(
    val id: Int,
    val owner_id: Long,
    val access_key: String,
    val title: String,
    val thumb: AlbumThumb? = null,
    val main_color: String? = null,
)

/** Из `ua.lmg.vkapi2.objects.music.playlist.thumb.AlbumThumb` (srcSet обложек). */
@JsonClass(generateAdapter = true)
data class AlbumThumb(
    val width: Int = 0,
    val height: Int = 0,
    val src: String = "",
)

/** Из `playlist.metadata.FollowedMetadata` / `OriginalPlaylist` / `AudioPlaylistMeta`. */
@JsonClass(generateAdapter = true)
data class FollowedMetadata(
    val follower_id: Long = 0L,
    val is_followed: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class OriginalPlaylist(
    val id: Int = 0,
    val owner_id: Long = 0L,
    val access_key: String? = null,
    val title: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioPlaylistMeta(
    val view: String? = null,
    val variant: String? = null,
)
