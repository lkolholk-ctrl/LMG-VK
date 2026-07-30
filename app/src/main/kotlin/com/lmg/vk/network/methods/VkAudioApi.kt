package com.lmg.vk.network.methods

import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkParsedResponse
import com.lmg.vk.network.VkResponseParser
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.RawHttpResponse
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioTrack

/**
 * Обёртки над audio.* эндпоинтами VK.
 * Паттерны восстановлены из `AbstractC1085e`, `C4271e`, `C2193e`, `C4673e`.
 * Все функции: VkMethod -> client.execute -> VkResult.
 */
class VkAudioApi(
    private val client: VkApiClient,
) {
    // ---------------------------------------------------------------
    // audio.get — треки пользователя/плейлиста (AbstractC1085e.ad)
    // ---------------------------------------------------------------
    suspend fun getAudios(
        ownerId: Long,
        offset: Int,
        count: Int,
        playlistId: Int? = null,
    ): VkResult<List<AudioTrack>> {
        val method = VkMethod("audio.get", AudioTrackListParser).apply {
            param("count", count)
            param("offset", offset)
            param("owner_id", ownerId)
            playlistId?.let { param("playlist_id", it) }
        }
        return client.execute(method)
    }

    // ---------------------------------------------------------------
    // audio.search — поиск треков (AbstractC1085e.metrica)
    // count принудительно зажат в 0..300, в оригинале передаётся 120
    // ---------------------------------------------------------------
    suspend fun searchAudios(
        query: String,
        ownerId: Long,
        offset: Int,
        count: Int = 120,
    ): VkResult<List<AudioTrack>> {
        val method = VkMethod("audio.search", AudioTrackListParser).apply {
            param("q", query)
            param("count", count.coerceIn(0, 300))
            param("offset", offset)
            param("owner_id", ownerId)
            param("filter", "all")
        }
        return client.execute(method)
    }

    // ---------------------------------------------------------------
    // audio.searchPlaylists (AbstractC1085e.license)
    // ---------------------------------------------------------------
    suspend fun searchPlaylists(
        query: String,
        ownerId: Long,
        offset: Int,
        filters: List<String> = emptyList(),
        count: Int = 100,
    ): VkResult<List<AudioPlaylist>> {
        val method = VkMethod("audio.searchPlaylists", PlaylistListParser).apply {
            param("q", query)
            param("count", count)
            param("offset", offset)
            param("owner_id", ownerId)
            param("filters", filters.joinToString(","))
        }
        return client.execute(method)
    }

    // ---------------------------------------------------------------
    // audio.getPlaylists (AbstractC1085e.vip)
    // ---------------------------------------------------------------
    suspend fun getPlaylists(
        ownerId: Long,
        offset: Int,
        filters: List<String>? = null,
        count: Int = 100,
    ): VkResult<List<AudioPlaylist>> {
        val method = VkMethod("audio.getPlaylists", PlaylistListParser).apply {
            param("owner_id", ownerId)
            filters?.let { param("filters", it.joinToString(",")) }
            param("count", count)
            param("offset", offset)
        }
        return client.execute(method)
    }

    // ---------------------------------------------------------------
    // audio.getPlaylistById (C4271e)
    // ---------------------------------------------------------------
    suspend fun getPlaylistById(
        ownerId: Long,
        playlistId: Int,
    ): VkResult<AudioPlaylist> {
        val method = VkMethod("audio.getPlaylistById", PlaylistParser).apply {
            param("playlist_id", playlistId)
            param("owner_id", ownerId)
        }
        return client.execute(method)
    }

    // ---------------------------------------------------------------
    // Лайки/дизлайки (C2193e): audio_ids = csv полных id ("owner_audio")
    // ---------------------------------------------------------------
    suspend fun addDislike(audioFullId: String): VkResult<Unit> =
        executeSimpleList("audio.addDislike", audioFullId)

    suspend fun removeDislike(audioFullId: String): VkResult<Unit> =
        executeSimpleList("audio.removeDislike", audioFullId)

    suspend fun add(audioFullId: String): VkResult<Unit> =
        executeSimpleList("audio.add", audioFullId)

    suspend fun delete(audioFullId: String): VkResult<Unit> =
        executeSimpleList("audio.delete", audioFullId)

    suspend fun restore(audioFullId: String): VkResult<Unit> =
        executeSimpleList("audio.restore", audioFullId)

    private suspend fun executeSimpleList(name: String, audioFullId: String): VkResult<Unit> {
        val method = VkMethod(name, UnitParser).apply {
            param("audio_ids", listOf(audioFullId).joinToString(","))
        }
        return client.execute(method)
    }

    // ---------------------------------------------------------------
    // Парсеры (в оригинале — синглтоны C15802e/C5107e/C5438e/C4524e/C5170e)
    // ---------------------------------------------------------------
    private object AudioTrackListParser : VkResponseParser<List<AudioTrack>> {
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<AudioTrack>> {
            TODO("Moshi: VKResponse<ItemsDto<AudioTrack>> — {response:{items:[...]}}")
        }
    }

    private object PlaylistListParser : VkResponseParser<List<AudioPlaylist>> {
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<AudioPlaylist>> {
            TODO("Moshi: VKResponse<ItemsDto<AudioPlaylist>>")
        }
    }

    private object PlaylistParser : VkResponseParser<AudioPlaylist> {
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<AudioPlaylist> {
            TODO("Moshi: VKResponse<AudioPlaylist>")
        }
    }

    private object UnitParser : VkResponseParser<Unit> {
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<Unit> {
            TODO("VKResponse<Int> (1 = ok) -> Unit")
        }
    }
}
