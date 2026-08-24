package com.lmg.vk.network.dto.music

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AudioStreamDto(
    val type: String? = null,
    val url: String? = null,
    val fallback_url: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioLoudnessDto(
    val lufs: Float = 0f,
    val peak: Float = 0f,
)

@JsonClass(generateAdapter = true)
data class AudioPreviewUrlDto(
    val url: String? = null,
    val clip_from: Int? = null,
    val clip_to: Int? = null,
)

@JsonClass(generateAdapter = true)
data class AudioAdsDto(
    val _SITEID: String? = null,
    val account_age_type: String? = null,
    val content_id: String? = null,
    val duration: String? = null,
    val preview: String? = null,
    val puid1: String? = null,
    val puid22: String? = null,
    val ver: String? = null,
    val vk_id: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioVoiceAssistantSourceDto(
    val album_uid: String? = null,
    val artist: String? = null,
    val audio_hash: String? = null,
    val cpp_hash: String? = null,
    val duration: Int? = null,
    val media_type: String? = null,
    val name: String? = null,
    val phrase_id: String? = null,
    val skill_name: String? = null,
    val title: String? = null,
    val type: String? = null,
    val uid: String? = null,
    val url: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioVoiceAssistantDto(
    val flags: String? = null,
    val kws_skip: List<List<Float>>? = null,
    val source: AudioVoiceAssistantSourceDto? = null,
    val track_id: Int? = null,
)

@JsonClass(generateAdapter = true)
data class AudioAudioMetaDto(
    val promo_style: String? = null,
    val promo_title: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioAudioPermissionsDto(
    val edit: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class AudioAudioIdDto(
    val audio_id: Int,
    val track_code: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioAudioRawIdTrackedDto(
    val audio_id: String,
    val track_code: String,
)

@JsonClass(generateAdapter = true)
data class AudioAlbumPartsFirstAudioDto(
    val part_id: Int,
    val audio_id: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioGetPlaylistExtendedResponseDto(
    val playlist: AudioPlaylist,
    val profiles: List<Any?>? = null,
    val groups: List<Any?>? = null,
    val artists: List<AudioArtistDto>? = null,
    val audio_ids: List<AudioAudioRawIdTrackedDto>? = null,
    val extra_recommendations_section_id: String? = null,
    val album_parts_first_audios: List<AudioAlbumPartsFirstAudioDto>? = null,
    val duration: Int? = null,
)

@JsonClass(generateAdapter = true)
data class AudioRestrictionInfoDto(
    val restriction: Int? = null,
    val title: String? = null,
    val text: String? = null,
    val button: Any? = null,
    val icons: List<BaseImageDto>? = null,
)

@JsonClass(generateAdapter = true)
data class AudioGetKidsModeResponseDto(
    val state: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class AudioGetUserConfigResponseDto(
    val is_rate_app_enabled: Boolean? = null,
    val is_resident: Boolean? = null,
    val is_unnecessary_feedback: Boolean? = null,
    val moosic_user_id: Int? = null,
)

@JsonClass(generateAdapter = true)
data class AudioRadioStationDto(
    val id: Int,
    val name: String,
    val logo_url: String? = null,
    val logo_png_url: String? = null,
    val background_color: String? = null,
    val is_followed: Boolean? = null,
    val stream_url: String? = null,
    val is_enabled: Boolean? = null,
    val track_code: String? = null,
)

@JsonClass(generateAdapter = true)
data class AudioPlaylistAlbumItemDto(
    val type: String? = null,
    val view: String? = null,
)
