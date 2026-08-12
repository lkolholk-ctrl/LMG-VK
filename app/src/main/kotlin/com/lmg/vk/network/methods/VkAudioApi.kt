package com.lmg.vk.network.methods

import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkParsedResponse
import com.lmg.vk.network.VkResponseParser
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.RawHttpResponse
import com.lmg.vk.network.MappingVkResponseParser
import com.lmg.vk.network.MoshiEnvelopeParser
import com.lmg.vk.network.VkItems
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioAddResponse
import com.lmg.vk.network.dto.music.AudioAddResult
import com.lmg.vk.network.dto.music.AudioGetAutoflowMixParamsResponse
import com.lmg.vk.network.dto.music.AudioLyricsContainer
import com.lmg.vk.network.dto.music.AudioRelatedArtistsResponse
import com.lmg.vk.network.dto.music.AudioSearchMainResponse
import com.lmg.vk.network.dto.music.AudioStreamMixSettingsResponse
import com.lmg.vk.network.dto.VKError
import com.lmg.vk.network.dto.music.AudioTrack
import com.lmg.vk.network.dto.music.AudioAudioDto
import com.lmg.vk.network.dto.music.AudioSnippetEntry
import com.lmg.vk.network.dto.music.VkArtistDto
import com.lmg.vk.network.dto.music.VkRootItems
import com.lmg.vk.network.dto.gen.music.AudioWidgetItem
import com.squareup.moshi.Types
import org.json.JSONArray
import org.json.JSONObject

/**
 * Обёртки над audio.* эндпоинтами VK.
 * Паттерны восстановлены из `AbstractC1085e`, `C4271e`, `C2193e`, `C4673e`.
 * Все функции: VkMethod -> client.execute -> VkResult.
 */
class VkAudioApi(
    private val client: VkApiClient,
) {
    /** `audio.getById`: точечный resolve полных VK id (`owner_id_audio_id`). */
    suspend fun getById(audioFullIds: Collection<String>): VkResult<List<AudioTrack>> {
        if (audioFullIds.isEmpty()) return VkResult.Success(emptyList())
        val method = VkMethod("audio.getById", DirectAudioTrackListParser).apply {
            param("audios", audioFullIds.joinToString(",") { it.removePrefix("vk_") })
        }
        return client.execute(method)
    }

    /** `audio.getLyrics` (C13029e, id=12). Параметр — полный audio_id. */
    suspend fun getLyrics(audioFullId: String): VkResult<AudioLyricsContainer> {
        val method = VkMethod(
            "audio.getLyrics",
            MoshiEnvelopeParser<AudioLyricsContainer>(AudioLyricsContainer::class.java),
        ).apply {
            param("audio_id", audioFullId.removePrefix("vk_"))
        }
        return client.execute(method)
    }

    /** `audio.getAudiosByArtist` (C13029e, id=10). */
    suspend fun getAudiosByArtist(
        artistId: String,
        type: String? = "top",
        offset: Int = 0,
        count: Int = 100,
    ): VkResult<List<AudioTrack>> {
        val listType = Types.newParameterizedType(List::class.java, AudioTrack::class.java)
        val method = VkMethod(
            "audio.getAudiosByArtist",
            MoshiEnvelopeParser<List<AudioTrack>>(listType),
        ).apply {
            param("artist_id", artistId)
            type?.let { param("type", it) }
            param("count", count.coerceIn(1, 100))
            param("offset", offset.coerceAtLeast(0))
        }
        return client.execute(method)
    }

    /**
     * `audio.getAlbumsByArtist`: полная дискография исполнителя с пагинацией.
     * UI не считает релизы по урезанному catalog-блоку.
     */
    suspend fun getAlbumsByArtist(
        artistId: String,
        offset: Int = 0,
        count: Int = 100,
    ): VkResult<List<AudioPlaylist>> {
        val listType = Types.newParameterizedType(List::class.java, AudioPlaylist::class.java)
        val method = VkMethod(
            "audio.getAlbumsByArtist",
            MoshiEnvelopeParser<List<AudioPlaylist>>(listType),
        ).apply {
            param("artist_id", artistId)
            param("offset", offset.coerceAtLeast(0))
            param("count", count.coerceIn(1, 100))
        }
        return client.execute(method)
    }

    /** `audio.getPopular` (C13029e, id=14). */
    suspend fun getPopular(
        offset: Int = 0,
        count: Int = 100,
        genreId: Int? = null,
    ): VkResult<List<AudioTrack>> {
        val listType = Types.newParameterizedType(List::class.java, AudioTrack::class.java)
        val method = VkMethod(
            "audio.getPopular",
            MoshiEnvelopeParser<List<AudioTrack>>(listType),
        ).apply {
            param("offset", offset.coerceAtLeast(0))
            param("count", count.coerceIn(1, 100))
            genreId?.let { param("genre_id", it) }
        }
        return client.execute(method)
    }

    /** `audio.getRecommendations` (C13029e, id=15). */
    suspend fun getRecommendations(
        targetAudio: String? = null,
        offset: Int = 0,
        count: Int = 100,
        userId: Long? = null,
    ): VkResult<List<AudioTrack>> {
        val listType = Types.newParameterizedType(List::class.java, AudioTrack::class.java)
        val method = VkMethod(
            "audio.getRecommendations",
            MoshiEnvelopeParser<List<AudioTrack>>(listType),
        ).apply {
            param("offset", offset.coerceAtLeast(0))
            param("count", count.coerceIn(1, 100))
            userId?.let { param("user_id", it) }
            param("target_audio", targetAudio?.removePrefix("vk_"))
        }
        return client.execute(method)
    }

    /**
     * Official VK 8.185 "similar tracks" request.
     *
     * Unlike the generic recommendations wrapper above, the player call site
     * sends exactly `target_audio`, `count = 100` and `is_child`; it does not
     * send an offset, user id, source or ref.
     */
    suspend fun getSimilarTrackRecommendations(
        targetAudio: String,
        isChild: Boolean = false,
    ): VkResult<List<AudioTrack>> {
        val listType = Types.newParameterizedType(List::class.java, AudioTrack::class.java)
        val method = VkMethod(
            "audio.getRecommendations",
            MoshiEnvelopeParser<List<AudioTrack>>(listType),
        ).apply {
            param("target_audio", targetAudio.removePrefix("vk_"))
            param("count", 100)
            param("is_child", isChild)
        }
        return client.execute(method)
    }

    /** `audio.getStreamMixAudios` (C13029e, id=17). */
    suspend fun getStreamMixAudios(
        mixId: String,
        entityId: String? = null,
        append: Boolean = false,
        options: Map<String, List<String>> = emptyMap(),
        mixOptionsId: Long? = null,
        sourceRef: String? = null,
        promptEvents: String? = null,
    ): VkResult<List<AudioTrack>> {
        val listType = Types.newParameterizedType(List::class.java, AudioTrack::class.java)
        val method = VkMethod(
            "audio.getStreamMixAudios",
            MoshiEnvelopeParser<List<AudioTrack>>(listType),
        ).apply {
            param("mix_id", mixId)
            param("entity_id", entityId)
            param("count", 5)
            param("append", append)
            if (options.isNotEmpty() || mixOptionsId != null) {
                val json = JSONObject()
                // Official VK adds the generated settings-session id first,
                // then `category_id -> [selected_option_id]` entries.
                mixOptionsId?.let { json.put("id", it.toString()) }
                options.forEach { (key, values) ->
                    if (values.isNotEmpty()) {
                        val selected = JSONArray()
                        values.forEach { selected.put(it) }
                        json.put(key, selected)
                    }
                }
                param("options", json.toString())
            }
            param("prompt_events", promptEvents)
            param("ref", sourceRef)
        }
        return client.execute(method)
    }

    /** `audio.getStreamMixSettings` (C7914e/C6114e). */
    suspend fun getStreamMixSettings(mixId: String): VkResult<AudioStreamMixSettingsResponse> {
        val method = VkMethod(
            "audio.getStreamMixSettings",
            MoshiEnvelopeParser<AudioStreamMixSettingsResponse>(AudioStreamMixSettingsResponse::class.java),
        ).apply { param("mix_id", mixId) }
        return client.execute(method)
    }

    /**
     * Official VK 8.185 Autoflow hand-off for a finite music queue.
     *
     * `count` is the size of the complete queue, while `audio_ids` contains at
     * most its last 50 full VK ids. The response is Mix identity, not tracks.
     */
    suspend fun getAutoflowMixParams(
        count: Int,
        queueType: String,
        audioIds: List<String>,
        queueEntityId: String? = null,
    ): VkResult<AudioGetAutoflowMixParamsResponse> {
        val method = VkMethod(
            "audio.getAutoflowMixParams",
            MoshiEnvelopeParser<AudioGetAutoflowMixParamsResponse>(
                AudioGetAutoflowMixParamsResponse::class.java,
            ),
        ).apply {
            param("count", count.coerceAtLeast(0))
            param("queue_type", queueType)
            param("audio_ids", audioIds.takeLast(50).joinToString(",") { it.removePrefix("vk_") })
            param("queue_entity_id", queueEntityId)
        }
        return client.execute(method)
    }

    /** `audio.searchArtists` (C14197e/C4590e). */
    suspend fun searchArtists(
        query: String,
        offset: Int = 0,
        count: Int = 100,
    ): VkResult<VkRootItems<VkArtistDto>> {
        val responseType = Types.newParameterizedType(VkRootItems::class.java, VkArtistDto::class.java)
        val method = VkMethod(
            "audio.searchArtists",
            MoshiEnvelopeParser<VkRootItems<VkArtistDto>>(responseType),
        ).apply {
            param("q", query)
            param("offset", offset.coerceAtLeast(0))
            param("count", count.coerceIn(1, 100))
        }
        return client.execute(method)
    }

    /** `audio.searchMain` (C18378e) с 7 типизированными секциями Priority 2. */
    suspend fun searchMain(
        query: String,
        offset: Int = 0,
        count: Int = 100,
    ): VkResult<AudioSearchMainResponse> {
        val method = VkMethod(
            "audio.searchMain",
            MoshiEnvelopeParser<AudioSearchMainResponse>(AudioSearchMainResponse::class.java),
        ).apply {
            param("q", query)
            param("offset", offset.coerceAtLeast(0))
            // C18378e создаёт диапазон 0..300 для searchMain.
            param("count", count.coerceIn(1, 300))
        }
        return client.execute(method)
    }

    /** `audio.getRelatedArtistsById` (C17019e/C10990e). */
    suspend fun getRelatedArtistsById(
        artistId: String,
        offset: Int = 0,
        count: Int = 10,
    ): VkResult<AudioRelatedArtistsResponse> {
        val method = VkMethod(
            "audio.getRelatedArtistsById",
            MoshiEnvelopeParser<AudioRelatedArtistsResponse>(AudioRelatedArtistsResponse::class.java),
        ).apply {
            param("artist_id", artistId)
            param("offset", offset.coerceAtLeast(0))
            param("count", count.coerceIn(1, 100))
        }
        return client.execute(method)
    }

    suspend fun followRadioStation(stationId: Int): VkResult<Unit> {
        val method = VkMethod("audio.followRadioStation", UnitParser).apply {
            param("station_id", stationId)
        }
        return client.execute(method)
    }

    suspend fun unfollowRadioStation(stationId: Int): VkResult<Unit> {
        val method = VkMethod("audio.unfollowRadioStation", UnitParser).apply {
            param("station_id", stationId)
        }
        return client.execute(method)
    }

    /**
     * Скрытый путь получения ссылки из VK MP3 MOD (prefs-флаг `audio_rip`).
     *
     * Один `execute`-запрос, дословно повторяющий расшифрованный из мода код
     * (`AudioGetLink`, 3DES-ключ `AudioPlayerService`). Почему одним запросом, а не
     * тремя отдельными вызовами board.*: серверный `execute` атомарен — комментарий
     * создаётся и читается в одном обращении, поэтому между createComment и
     * getComments не может вклиниться чужая запись и сдвинуть `start_comment_id`.
     *
     * ВНИМАНИЕ: метод ПИШЕТ от имени пользователя (комментарий в тему сообщества).
     * Подробности и мотивация — в [com.lmg.vk.audio.AudioRipFallback], вызывать
     * только оттуда.
     *
     * @param audioIdWithKey `owner_id_audio_id[_access_key]` — формат `AudioFile.asIdWithKey()`.
     * @return прямой mp3/m3u8-URL без ограничений на прослушивание.
     */
    suspend fun getLinkViaBoardComment(
        audioIdWithKey: String,
        groupId: Long,
        topicId: Long,
    ): VkResult<String> {
        // Значение уходит внутрь кода execute, а не в отдельный параметр, — как в
        // оригинале (String.format). Поэтому id обязан быть уже провалидирован
        // вызывающим: любой посторонний символ здесь стал бы инъекцией в скрипт.
        val code = "var g=$groupId,t=$topicId," +
            "i=API.board.createComment({group_id:g,topic_id:t,attachments:\"audio$audioIdWithKey\"})," +
            "a=API.board.getComments({group_id:g,topic_id:t,start_comment_id:i,count:1,photo_sizes:1}).items[0];" +
            "if(a.id!=i)a=null;" +
            "API.newsfeed.unsubscribe({type:\"topic\",owner_id:-g,item_id:t});" +
            "return a.attachments[0].audio.url;"
        val method = VkMethod("execute", BoardRipUrlParser).apply {
            param("code", code)
        }
        return client.execute(method)
    }

    // ---------------------------------------------------------------
    // Старое ua.lmg/vkapi2-семейство C13029e. Эти ручки не заменяют
    // bruhcollective DTO Priority 2 и сохраняются как отдельный контур.
    // ---------------------------------------------------------------

    suspend fun addToPlaylist(
        playlistId: Int,
        ownerId: Long,
        audioIds: Collection<String>,
        accessKey: String? = null,
    ): VkResult<Any> {
        val method = VkMethod("audio.addToPlaylist", MoshiEnvelopeParser<Any>(Any::class.java)).apply {
            param("playlist_id", playlistId)
            param("owner_id", ownerId)
            param("access_key", accessKey)
            param("audio_ids", audioIds.joinToString(",") { it.removePrefix("vk_") })
        }
        return client.execute(method)
    }

    /** `audio.createPlaylist`: контракт owner_id/title подтверждён VK MP3. */
    suspend fun createPlaylist(
        ownerId: Long,
        title: String,
    ): VkResult<AudioPlaylist> {
        val method = VkMethod("audio.createPlaylist", PlaylistParser).apply {
            param("owner_id", ownerId)
            param("title", title)
        }
        return client.execute(method)
    }

    /**
     * `audio.editPlaylist`: title меняет имя, audio_ids атомарно заменяет состав.
     * Именно эти две формы восстановлены из `AudioEdit` в архиве VK MP3.
     */
    suspend fun editPlaylist(
        ownerId: Long,
        playlistId: Int,
        title: String,
        audioIds: Collection<String>,
    ): VkResult<Unit> {
        val method = VkMethod("audio.editPlaylist", UnitParser).apply {
            param("owner_id", ownerId)
            param("playlist_id", playlistId)
            param("title", title)
            param("audio_ids", audioIds.joinToString(",") { it.removePrefix("vk_") })
        }
        return client.execute(method)
    }

    suspend fun edit(
        audioFullId: String,
        title: String,
        artist: String,
    ): VkResult<Unit> {
        val (ownerId, audioId) = parseAudioFullId(audioFullId)
        val method = VkMethod("audio.edit", UnitParser).apply {
            param("audio_id", audioId)
            param("owner_id", ownerId)
            param("title", title)
            param("artist", artist)
            param("no_search", "true")
        }
        return client.execute(method)
    }

    suspend fun followArtist(userId: Long, artistId: String): VkResult<Unit> =
        followArtistMethod("audio.followArtist", userId, artistId)

    suspend fun unfollowArtist(userId: Long, artistId: String): VkResult<Unit> =
        followArtistMethod("audio.unfollowArtist", userId, artistId)

    private suspend fun followArtistMethod(
        name: String,
        userId: Long,
        artistId: String,
    ): VkResult<Unit> {
        val method = VkMethod(name, UnitParser).apply {
            param("user_id", userId)
            param("artist_id", artistId)
            param("ref", "banner")
        }
        return client.execute(method)
    }

    suspend fun followOwner(ownerId: Long): VkResult<Unit> =
        ownerMutation("audio.followOwner", ownerId)

    suspend fun unfollowOwner(ownerId: Long): VkResult<Unit> =
        ownerMutation("audio.unfollowOwner", ownerId)

    private suspend fun ownerMutation(name: String, ownerId: Long): VkResult<Unit> {
        val method = VkMethod(name, UnitParser).apply { param("owner_id", ownerId) }
        return client.execute(method)
    }

    suspend fun followCurator(userId: Long, curatorId: Long): VkResult<Unit> =
        curatorMutation("audio.followCurator", userId, curatorId)

    suspend fun unfollowCurator(userId: Long, curatorId: Long): VkResult<Unit> =
        curatorMutation("audio.unfollowCurator", userId, curatorId)

    private suspend fun curatorMutation(name: String, userId: Long, curatorId: Long): VkResult<Unit> {
        val method = VkMethod(name, UnitParser).apply {
            param("user_id", userId)
            param("curator_id", curatorId)
        }
        return client.execute(method)
    }

    suspend fun followPlaylist(
        playlistId: Int,
        ownerId: Long,
        accessKey: String? = null,
    ): VkResult<Int> {
        val method = VkMethod("audio.followPlaylist", IntParser).apply {
            param("playlist_id", playlistId)
            param("owner_id", ownerId)
            param("access_key", accessKey)
        }
        return client.execute(method)
    }

    suspend fun getSnippets(count: Int = 3): VkResult<List<AudioSnippetEntry>> {
        val type = Types.newParameterizedType(List::class.java, AudioSnippetEntry::class.java)
        val method = VkMethod(
            "audio.getSnippets",
            MoshiEnvelopeParser<List<AudioSnippetEntry>>(type),
        ).apply { param("count", count) }
        return client.execute(method)
    }

    suspend fun getWidgetElements(
        size: String,
        type: String,
        userId: Long,
    ): VkResult<List<AudioWidgetItem>> {
        val listType = Types.newParameterizedType(List::class.java, AudioWidgetItem::class.java)
        val method = VkMethod(
            "audio.getWidgetElements",
            MoshiEnvelopeParser<List<AudioWidgetItem>>(listType),
        ).apply {
            param("size", size)
            param("type", type)
            param("user_id", userId)
        }
        return client.execute(method)
    }

    suspend fun removeListenedAudio(audioFullId: String): VkResult<Unit> =
        executeTrackMutation("audio.removeListenedAudio", audioFullId)

    suspend fun reorder(
        audioFullId: String,
        before: Int? = null,
        after: Int? = null,
    ): VkResult<Int> {
        val (ownerId, audioId) = parseAudioFullId(audioFullId)
        val method = VkMethod("audio.reorder", IntParser).apply {
            before?.let { param("before", it) }
            param("audio_id", audioId)
            param("owner_id", ownerId)
            after?.let { param("after", it) }
        }
        return client.execute(method)
    }

    suspend fun reorderPlaylists(
        playlistId: Int,
        ownerId: Long,
        before: Int? = null,
        after: Int? = null,
    ): VkResult<Int> {
        val method = VkMethod("audio.reorderPlaylists", IntParser).apply {
            param("playlist_id", playlistId)
            param("owner_id", ownerId)
            before?.let { param("before", it) }
            after?.let { param("after", it) }
        }
        return client.execute(method)
    }

    suspend fun save(
        audio: String,
        server: String,
        hash: String,
        artist: String,
        title: String,
    ): VkResult<AudioTrack> {
        val method = VkMethod(
            "audio.save",
            MoshiEnvelopeParser<AudioTrack>(AudioTrack::class.java),
        ).apply {
            param("audio", audio)
            param("server", server)
            param("hash", hash)
            param("artist", artist)
            param("title", title)
        }
        return client.execute(method)
    }

    suspend fun savePlaylistAsCopy(playlistId: Int, ownerId: Long): VkResult<AudioPlaylist> {
        val method = VkMethod("audio.savePlaylistAsCopy", PlaylistParser).apply {
            param("playlist_id", playlistId)
            param("owner_id", ownerId)
        }
        return client.execute(method)
    }

    suspend fun setBroadcast(audioFullId: String?, targetUserId: Long): VkResult<Unit> {
        val method = VkMethod("audio.setBroadcast", UnitParser).apply {
            param("audio", audioFullId?.removePrefix("vk_"))
            param("enabled", audioFullId != null)
            param("target_ids", targetUserId)
        }
        return client.execute(method)
    }

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

    /**
     * То же, что [getAudios], но с сохранением `count` из ответа VK.
     * Нужно там, где показывается реальное общее количество треков владельца,
     * а не длина выданной страницы.
     */
    suspend fun getAudiosPage(
        ownerId: Long,
        offset: Int,
        count: Int,
        playlistId: Int? = null,
    ): VkResult<VkItems<AudioTrack>> {
        val method = VkMethod("audio.get", AudioTrackPageParser).apply {
            param("count", count)
            param("offset", offset)
            param("owner_id", ownerId)
            playlistId?.let { param("playlist_id", it) }
        }
        return client.execute(method)
    }

    /** [getPlaylists] с сохранением `count` — реальное число плейлистов владельца. */
    suspend fun getPlaylistsPage(
        ownerId: Long,
        offset: Int,
        filters: List<String>? = null,
        count: Int = 100,
    ): VkResult<VkItems<AudioPlaylist>> {
        val method = VkMethod("audio.getPlaylists", PlaylistPageParser).apply {
            param("owner_id", ownerId)
            filters?.let { param("filters", it.joinToString(",")) }
            param("count", count)
            param("offset", offset)
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

    /** `audio.search` page with the server's total `count` preserved. */
    suspend fun searchAudiosPage(
        query: String,
        ownerId: Long,
        offset: Int,
        count: Int = 120,
    ): VkResult<VkItems<AudioTrack>> {
        val method = VkMethod("audio.search", AudioTrackPageParser).apply {
            param("q", query)
            param("count", count.coerceIn(1, 300))
            param("offset", offset.coerceAtLeast(0))
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
    /**
     * `audio.getPlaylistById`.
     *
     * [extraFields] — набор дополнительных полей ответа. Допустимые значения
     * сверены с декомпилом 8.185 (`AudioGetPlaylistByIdExtraFieldsDto`), их
     * ровно четыре: `album_parts_first_audios`, `audio_ids`,
     * `extra_recommendations_section_id`, `owner`.
     *
     * Практический смысл:
     *  - `audio_ids` — лёгкий список id без полных объектов треков, дешевле для
     *    построения очереди;
     *  - `album_parts_first_audios` — первый трек каждой части многодискового
     *    альбома, даёт заголовки «Диск 1 / Диск 2».
     *
     * `duration` в этот список НЕ входит, хотя в первичном отчёте по декомпилу
     * он упоминался — проверка по самому DTO это не подтвердила.
     *
     * `access_key` и `extended` СОЗНАТЕЛЬНО не добавлены: официальный клиент их
     * шлёт, но это путь доступа к трекам — зона, менять которую в этом проекте
     * запрещено владельцем.
     */
    suspend fun getPlaylistById(
        ownerId: Long,
        playlistId: Int,
        extraFields: List<String> = emptyList(),
    ): VkResult<AudioPlaylist> {
        val method = VkMethod("audio.getPlaylistById", PlaylistParser).apply {
            param("playlist_id", playlistId)
            param("owner_id", ownerId)
            extraFields.takeIf { it.isNotEmpty() }
                ?.let { param("extra_fields", it.joinToString(",")) }
        }
        return client.execute(method)
    }

    /** Удаление плейлиста; контракт подтверждён execute-кодом из `C9518e`. */
    suspend fun deletePlaylist(
        ownerId: Long,
        playlistId: Int,
        accessKey: String? = null,
    ): VkResult<Unit> {
        val method = VkMethod("audio.deletePlaylist", UnitParser).apply {
            param("playlist_id", playlistId)
            param("owner_id", ownerId)
            param("access_key", accessKey)
        }
        return client.execute(method)
    }

    // ---------------------------------------------------------------
    // Лайки/дизлайки (C2193e): audio_ids = csv полных id ("owner_audio")
    // ---------------------------------------------------------------
    suspend fun addDislike(audioFullId: String): VkResult<AudioAudioDto> {
        val method = VkMethod(
            "audio.addDislike",
            MoshiEnvelopeParser<AudioAudioDto>(AudioAudioDto::class.java),
        ).apply {
            param("audio_ids", listOf(audioFullId).joinToString(","))
        }
        return client.execute(method)
    }

    suspend fun removeDislike(audioFullId: String): VkResult<Unit> =
        executeSimpleList("audio.removeDislike", audioFullId)

    /**
     * Актуальный batch-контракт официального клиента. VK возвращает новый
     * owner/audio id пользовательской копии; терять его нельзя, иначе локальная
     * строка продолжит ссылаться на исходник и задвоится при следующем audio.get.
     */
    suspend fun add(audioFullId: String, accessKey: String? = null): VkResult<AudioAddResult> {
        val normalized = audioFullId.removePrefix("vk_")
        val requestId = if (normalized.count { it == '_' } < 2 && !accessKey.isNullOrBlank()) {
            "${normalized}_$accessKey"
        } else {
            normalized
        }
        val method = VkMethod(
            "audio.add",
            MoshiEnvelopeParser<AudioAddResponse>(AudioAddResponse::class.java),
        ).apply {
            param("audio_ids", requestId)
        }
        return when (val result = client.execute(method)) {
            is VkResult.Success -> {
                val item = result.data.items.orEmpty().firstOrNull()
                val error = result.data.errors.orEmpty().firstOrNull()
                when {
                    item != null -> VkResult.Success(item)
                    error != null -> VkResult.Error(
                        code = error.error_code.toIntOrNull() ?: -1,
                        message = error.error_msg.ifBlank { "VK не добавил аудио" },
                    )
                    else -> VkResult.Error(-1, "VK вернул пустой ответ audio.add")
                }
            }

            is VkResult.Error -> result
        }
    }

    suspend fun delete(audioFullId: String): VkResult<Unit> =
        executeTrackMutation("audio.delete", audioFullId)

    suspend fun restore(audioFullId: String): VkResult<Unit> =
        executeTrackMutation("audio.restore", audioFullId)

    /**
     * Priority 3 подтвердил второй, новый контракт `audio.restore`:
     * ответом является 39-польный `AudioAudioDto` (C18422e/C14729e).
     * Старый Unit-вариант выше сохранён отдельно.
     */
    suspend fun restoreDetailed(audioFullId: String): VkResult<AudioAudioDto> {
        val (ownerId, audioId) = parseAudioFullId(audioFullId)
        val method = VkMethod(
            "audio.restore",
            MoshiEnvelopeParser<AudioAudioDto>(AudioAudioDto::class.java),
        ).apply {
            param("audio_id", audioId)
            param("owner_id", ownerId)
        }
        return client.execute(method)
    }

    private suspend fun executeSimpleList(name: String, audioFullId: String): VkResult<Unit> {
        val method = VkMethod(name, UnitParser).apply {
            param("audio_ids", listOf(audioFullId).joinToString(","))
        }
        return client.execute(method)
    }

    private suspend fun executeTrackMutation(
        name: String,
        audioFullId: String,
        accessKey: String? = null,
    ): VkResult<Unit> {
        val (ownerId, audioId) = parseAudioFullId(audioFullId)
        val method = VkMethod(name, UnitParser).apply {
            param("audio_id", audioId)
            param("owner_id", ownerId)
            param("access_key", accessKey)
        }
        return client.execute(method)
    }

    private fun parseAudioFullId(fullId: String): Pair<Long, Int> {
        val normalized = fullId.removePrefix("vk_")
        val separator = normalized.indexOf('_')
        require(separator > 0 && separator < normalized.lastIndex) {
            "Invalid VK audio id: $fullId"
        }
        return normalized.substring(0, separator).toLong() to
            normalized.substring(separator + 1).toInt()
    }

    // ---------------------------------------------------------------
    // Парсеры (в оригинале — синглтоны C15802e/C5107e/C5438e/C4524e/C5170e)
    // ---------------------------------------------------------------
    private object AudioTrackListParser : VkResponseParser<List<AudioTrack>> {
        private val delegate = MappingVkResponseParser(
            MoshiEnvelopeParser<VkItems<AudioTrack>>(
                Types.newParameterizedType(VkItems::class.java, AudioTrack::class.java),
            ),
        ) { it.items }

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<AudioTrack>> {
            return delegate.parse(raw)
        }
    }

    /** `{count, items}` целиком — когда общее количество важно так же, как список. */
    private object AudioTrackPageParser : VkResponseParser<VkItems<AudioTrack>> {
        private val delegate = MoshiEnvelopeParser<VkItems<AudioTrack>>(
            Types.newParameterizedType(VkItems::class.java, AudioTrack::class.java),
        )

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<VkItems<AudioTrack>> {
            return delegate.parse(raw)
        }
    }

    private object PlaylistPageParser : VkResponseParser<VkItems<AudioPlaylist>> {
        private val delegate = MoshiEnvelopeParser<VkItems<AudioPlaylist>>(
            Types.newParameterizedType(VkItems::class.java, AudioPlaylist::class.java),
        )

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<VkItems<AudioPlaylist>> {
            return delegate.parse(raw)
        }
    }

    private object DirectAudioTrackListParser : VkResponseParser<List<AudioTrack>> {
        private val delegate = MoshiEnvelopeParser<List<AudioTrack>>(
            Types.newParameterizedType(List::class.java, AudioTrack::class.java),
        )

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<AudioTrack>> {
            return delegate.parse(raw)
        }
    }

    private object PlaylistListParser : VkResponseParser<List<AudioPlaylist>> {
        private val delegate = MappingVkResponseParser(
            MoshiEnvelopeParser<VkItems<AudioPlaylist>>(
                Types.newParameterizedType(VkItems::class.java, AudioPlaylist::class.java),
            ),
        ) { it.items }

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<AudioPlaylist>> {
            return delegate.parse(raw)
        }
    }

    private object PlaylistParser : VkResponseParser<AudioPlaylist> {
        private val delegate = MoshiEnvelopeParser<AudioPlaylist>(AudioPlaylist::class.java)

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<AudioPlaylist> {
            return delegate.parse(raw)
        }
    }

    private object UnitParser : VkResponseParser<Unit> {
        private val delegate = MoshiEnvelopeParser<Any>(Any::class.java)

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<Unit> {
            val parsed = delegate.parse(raw)
            return VkParsedResponse(parsed.data?.let { Unit }, parsed.error)
        }
    }

    private object IntParser : VkResponseParser<Int> {
        private val delegate = MoshiEnvelopeParser<Int>(Int::class.javaObjectType)
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<Int> = delegate.parse(raw)
    }

    /**
     * Ответ `execute` для [getLinkViaBoardComment] — голая строка URL в `response`.
     *
     * Разбирается вручную (org.json), а не Moshi, из-за `execute_errors`: если
     * board.createComment упал, VK отдаёт `response: null` + непустой
     * `execute_errors`, и без явного подъёма этой ошибки наверх вызывающий увидел бы
     * бессмысленное «response is null» вместо настоящей причины отказа.
     */
    private object BoardRipUrlParser : VkResponseParser<String> {
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<String> {
            val json = JSONObject(raw.bodyText())

            json.optJSONObject("error")?.let { error ->
                return VkParsedResponse(
                    null,
                    VKError(
                        error_code = error.optInt("error_code"),
                        error_msg = error.optString("error_msg"),
                        method = error.optString("method").takeIf(String::isNotEmpty),
                    ),
                )
            }

            val url = json.optString("response").takeIf { it.isNotEmpty() && it != "null" }
            if (url != null) return VkParsedResponse(url, null)

            // response пуст: причина — в первой из execute_errors (обычно это
            // отказ board.createComment: нет доступа к теме, она закрыта и т.п.).
            val executeError = json.optJSONArray("execute_errors")?.optJSONObject(0)
            val error = VKError(
                error_code = executeError?.optInt("error_code") ?: 0,
                error_msg = executeError?.optString("error_msg")
                    ?: "audio_rip: VK вернул пустой response без execute_errors",
                method = executeError?.optString("method")?.takeIf(String::isNotEmpty),
            )
            return VkParsedResponse(null, error)
        }
    }
}
