package com.lmg.vk.network.dto.music

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Ответ VK X `execute.SearchInProfile`.
 *
 * Исходный execute-код возвращает `playlists` из `audio.searchPlaylists` и
 * `audios` из `audio.search(...).items`. Профили и группы внутри первого
 * ответа не нужны для отображения собственной библиотеки и Moshi их пропускает.
 */
@JsonClass(generateAdapter = true)
data class ProfileLibrarySearchResponse(
    @Json(name = "playlists") val playlists: ProfileLibraryPlaylists = ProfileLibraryPlaylists(),
    @Json(name = "audios") val audios: List<AudioTrack> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ProfileLibraryPlaylists(
    @Json(name = "items") val items: List<AudioPlaylist> = emptyList(),
)
