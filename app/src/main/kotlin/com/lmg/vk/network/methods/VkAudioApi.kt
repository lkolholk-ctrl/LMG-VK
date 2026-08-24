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
import com.lmg.vk.network.dto.music.AudioAudioIdDto
import com.lmg.vk.network.dto.music.AudioDeleteExtendedResponseDto
import com.lmg.vk.network.dto.music.AudioGetAutoflowMixParamsResponse
import com.lmg.vk.network.dto.music.AudioGetKidsModeResponseDto
import com.lmg.vk.network.dto.music.AudioGetPlaylistExtendedResponseDto
import com.lmg.vk.network.dto.music.AudioGetUserConfigResponseDto
import com.lmg.vk.network.dto.music.AudioLyricsContainer
import com.lmg.vk.network.dto.music.AudioPlaylistOriginalFollowedDto
import com.lmg.vk.network.dto.music.AudioPlaylistReorderAction
import com.lmg.vk.network.dto.music.AudioRadioStationDto
import com.lmg.vk.network.dto.music.AudioRelatedArtistsResponse
import com.lmg.vk.network.dto.music.AudioRestrictionInfoDto
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
    suspend fun getById(
        audioFullIds: Collection<String>,
        ref: String? = null,
    ): VkResult<List<AudioTrack>> {
        val ids = audioFullIds.map { it.removePrefix("vk_") }
        if (ids.isEmpty()) return VkResult.Success(emptyList())
        val tracks = ArrayList<AudioTrack>(ids.size)
        for (chunk in ids.chunked(100)) {
            val method = VkMethod("audio.getById", DirectAudioTrackListParser).apply {
                param("audios", chunk.joinToString(","))
                param("ref", ref)
            }
            when (val result = client.execute(method)) {
                is VkResult.Success -> tracks.addAll(result.data)
                is VkResult.Error -> return result
            }
        }
        return VkResult.Success(tracks)
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
        count: Int = 100,
        isChild: Boolean = false,
    ): VkResult<List<AudioTrack>> {
        require(count in 0..1000)
        val method = VkMethod(
            "audio.getRecommendations",
            AudioTrackListParser,
        ).apply {
            param("target_audio", targetAudio?.removePrefix("vk_"))
            param("count", count)
            param("is_child", isChild)
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
    ): VkResult<List<AudioTrack>> = getRecommendations(targetAudio, 100, isChild)

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
    suspend fun getStreamMixSettings(
        mixId: String?,
        needUserSettings: Boolean = true,
    ): VkResult<AudioStreamMixSettingsResponse> {
        val method = VkMethod(
            "audio.getStreamMixSettings",
            MoshiEnvelopeParser<AudioStreamMixSettingsResponse>(AudioStreamMixSettingsResponse::class.java),
        ).apply {
            param("mix_id", mixId)
            param("need_user_settings", needUserSettings)
        }
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

    suspend fun followRadioStation(stationId: Int, ref: String? = null): VkResult<Unit> {
        val method = VkMethod("audio.followRadioStation", UnitParser).apply {
            param("station_id", stationId)
            param("ref", ref)
        }
        return client.execute(method)
    }

    suspend fun unfollowRadioStation(stationId: Int, ref: String? = null): VkResult<Unit> {
        val method = VkMethod("audio.unfollowRadioStation", UnitParser).apply {
            param("station_id", stationId)
            param("ref", ref)
        }
        return client.execute(method)
    }

    suspend fun getRestrictionsInfo(): VkResult<List<AudioRestrictionInfoDto>> {
        return client.execute(VkMethod("audio.getRestrictionsInfo", RestrictionInfoListParser))
    }

    suspend fun getKidsMode(): VkResult<AudioGetKidsModeResponseDto> {
        val parser = MoshiEnvelopeParser<AudioGetKidsModeResponseDto>(
            AudioGetKidsModeResponseDto::class.java,
        )
        return client.execute(VkMethod("audio.getKidsMode", parser))
    }

    suspend fun setKidsMode(state: Boolean): VkResult<Unit> {
        val method = VkMethod("audio.setKidsMode", UnitParser).apply {
            param("state", state)
        }
        return client.execute(method)
    }

    suspend fun radioGetById(stationIds: Collection<Int>): VkResult<List<AudioRadioStationDto>> {
        require(stationIds.isNotEmpty())
        val method = VkMethod("audio.radioGetById", RadioStationListParser).apply {
            param("station_ids", stationIds.joinToString(","))
        }
        return client.execute(method)
    }

    suspend fun getUserConfig(): VkResult<AudioGetUserConfigResponseDto> {
        val parser = MoshiEnvelopeParser<AudioGetUserConfigResponseDto>(
            AudioGetUserConfigResponseDto::class.java,
        )
        return client.execute(VkMethod("audio.getUserConfig", parser))
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
    ): VkResult<List<AudioAudioIdDto>> {
        val method = VkMethod("audio.addToPlaylist", AudioIdListParser).apply {
            param("owner_id", ownerId)
            param("playlist_id", playlistId)
            param("audio_ids", audioIds.joinToString(",") { it.removePrefix("vk_") })
        }
        return client.execute(method)
    }

    suspend fun createPlaylist(
        ownerId: Long,
        title: String,
        description: String? = null,
        audioIds: Collection<String>? = null,
        noDiscover: Boolean? = null,
    ): VkResult<AudioPlaylist> {
        require(title.length <= 1024)
        require(description == null || description.length <= 1024)
        val method = VkMethod("audio.createPlaylist", PlaylistParser).apply {
            param("owner_id", ownerId)
            param("title", title)
            param("description", description)
            audioIds?.takeIf { it.isNotEmpty() }?.let { ids ->
                param("audio_ids", ids.joinToString(",") { it.removePrefix("vk_") })
            }
            noDiscover?.let { param("no_discover", it) }
        }
        return client.execute(method)
    }

    suspend fun createPlaylistByFilter(ownerId: Long, filter: String?): VkResult<Int> {
        require(filter == null || filter.length <= 256)
        val method = VkMethod("audio.createPlaylistByFilter", IntParser).apply {
            param("owner_id", ownerId)
            param("filter", filter)
        }
        return client.execute(method)
    }

    suspend fun editPlaylist(
        ownerId: Long,
        playlistId: Int,
        title: String,
        description: String? = null,
        noDiscover: Boolean,
    ): VkResult<Int> {
        require(title.length <= 1024)
        require(description == null || description.length <= 1024)
        val method = VkMethod("audio.editPlaylist", IntParser).apply {
            param("owner_id", ownerId)
            param("playlist_id", playlistId)
            param("title", title)
            param("description", description)
            param("no_discover", noDiscover)
        }
        return client.execute(method)
    }

    suspend fun reorderInPlaylist(
        ownerId: Long,
        playlistId: Int,
        actions: List<AudioPlaylistReorderAction>,
    ): VkResult<Int> {
        require(actions.isNotEmpty())
        val method = VkMethod("audio.reorderInPlaylist", IntParser).apply {
            param("owner_id", ownerId)
            param("playlist_id", playlistId)
            param("actions", AudioPlaylistReorderAction.encode(actions))
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

    suspend fun followArtist(artistId: String, ref: String = "banner"): VkResult<Unit> =
        followArtistMethod("audio.followArtist", artistId, ref)

    suspend fun unfollowArtist(artistId: String, ref: String = "banner"): VkResult<Unit> =
        followArtistMethod("audio.unfollowArtist", artistId, ref)

    private suspend fun followArtistMethod(
        name: String,
        artistId: String,
        ref: String,
    ): VkResult<Unit> {
        val method = VkMethod(name, UnitParser).apply {
            param("artist_id", artistId)
            param("ref", ref)
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

    suspend fun followCurator(curatorId: String): VkResult<Unit> =
        curatorMutation("audio.followCurator", curatorId)

    suspend fun unfollowCurator(curatorId: String): VkResult<Unit> =
        curatorMutation("audio.unfollowCurator", curatorId)

    private suspend fun curatorMutation(name: String, curatorId: String): VkResult<Unit> {
        val method = VkMethod(name, UnitParser).apply {
            param("curator_id", curatorId)
        }
        return client.execute(method)
    }

    suspend fun followPlaylist(
        playlistId: Int,
        ownerId: Long,
        accessKey: String? = null,
        ref: String? = null,
    ): VkResult<AudioPlaylistOriginalFollowedDto> {
        val method = VkMethod(
            "audio.followPlaylist",
            MoshiEnvelopeParser<AudioPlaylistOriginalFollowedDto>(
                AudioPlaylistOriginalFollowedDto::class.java,
            ),
        ).apply {
            param("playlist_id", playlistId)
            param("owner_id", ownerId)
            param("access_key", accessKey)
            param("ref", ref)
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
        ownerId: Long?,
        offset: Int,
        count: Int,
        playlistId: Int? = null,
        shuffleSeed: Int? = null,
        extended: Boolean? = true,
        accessKey: String? = null,
        ref: String? = null,
    ): VkResult<List<AudioTrack>> {
        val method = VkMethod("audio.get", AudioTrackListParser).apply {
            ownerId?.let { param("owner_id", it) }
            playlistId?.let { param("playlist_id", it) }
            shuffleSeed?.let { param("shuffle_seed", it) }
            param("offset", offset)
            param("count", count)
            extended?.let { param("extended", it) }
            param("access_key", accessKey)
            param("ref", ref)
        }
        return client.execute(method)
    }

    suspend fun getLegacyAudios(
        ownerId: Long?,
        offset: Int? = null,
        count: Int? = null,
        playlistId: Int? = null,
        extended: Boolean? = true,
        accessKey: String? = null,
        ref: String? = null,
    ): VkResult<List<AudioTrack>> {
        val method = VkMethod("getAudios", AudioTrackListParser).apply {
            ownerId?.let { param("owner_id", it) }
            playlistId?.let { param("playlist_id", it) }
            offset?.let { param("offset", it) }
            count?.let { param("count", it) }
            extended?.let { param("extended", it) }
            param("access_key", accessKey)
            param("ref", ref)
        }
        return client.execute(method)
    }

    /**
     * То же, что [getAudios], но с сохранением `count` из ответа VK.
     * Нужно там, где показывается реальное общее количество треков владельца,
     * а не длина выданной страницы.
     */
    suspend fun getAudiosPage(
        ownerId: Long?,
        offset: Int,
        count: Int,
        playlistId: Int? = null,
        shuffleSeed: Int? = null,
        extended: Boolean? = true,
        accessKey: String? = null,
        ref: String? = null,
    ): VkResult<VkItems<AudioTrack>> {
        val method = VkMethod("audio.get", AudioTrackPageParser).apply {
            ownerId?.let { param("owner_id", it) }
            playlistId?.let { param("playlist_id", it) }
            shuffleSeed?.let { param("shuffle_seed", it) }
            param("offset", offset)
            param("count", count)
            extended?.let { param("extended", it) }
            param("access_key", accessKey)
            param("ref", ref)
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

    suspend fun getPlaylistById(
        ownerId: Long,
        playlistId: Int,
        extraFields: List<String> = emptyList(),
        extended: Boolean? = true,
        accessKey: String? = null,
        trackCount: Int? = null,
        ref: String? = null,
    ): VkResult<AudioPlaylist> {
        require(trackCount == null || trackCount in 0..30)
        require(extraFields.isEmpty() || extended == true)
        val parser = if (extraFields.isEmpty()) PlaylistParser else ExtendedPlaylistParser
        val method = VkMethod("audio.getPlaylistById", parser).apply {
            param("owner_id", ownerId)
            param("playlist_id", playlistId)
            extended?.let { param("extended", it) }
            extraFields.takeIf { it.isNotEmpty() }
                ?.let { param("extra_fields", it.joinToString(",")) }
            param("access_key", accessKey)
            trackCount?.let { param("track_count", it) }
            param("ref", ref)
        }
        return client.execute(method)
    }

    suspend fun removeFromPlaylist(
        ownerId: Long,
        playlistId: Int,
        audioIds: Collection<String>,
    ): VkResult<Int> {
        require(audioIds.isNotEmpty())
        val method = VkMethod("audio.removeFromPlaylist", IntParser).apply {
            param("owner_id", ownerId)
            param("playlist_id", playlistId)
            param("audio_ids", audioIds.joinToString(",") { bareAudioFullId(it) })
        }
        return client.execute(method)
    }

    suspend fun removeKidsAudios(audioIds: Collection<String>): VkResult<List<String>> {
        require(audioIds.isNotEmpty())
        val method = VkMethod("kidsCollection.removeAudios", StringListParser).apply {
            param("audio_ids", audioIds.joinToString(",") { it.removePrefix("vk_") })
        }
        return client.execute(method)
    }

    suspend fun deletePlaylist(
        ownerId: Long,
        playlistId: Int,
    ): VkResult<Int> {
        val method = VkMethod("audio.deletePlaylist", IntParser).apply {
            param("playlist_id", playlistId)
            param("owner_id", ownerId)
        }
        return client.execute(method)
    }

    suspend fun addDislike(audioFullId: String): VkResult<Boolean> {
        val method = VkMethod(
            "audio.addDislike",
            MappingVkResponseParser(
                MoshiEnvelopeParser<Int>(Int::class.javaObjectType),
            ) { it != 0 },
        ).apply {
            param("audio_ids", listOf(audioFullId).joinToString(","))
        }
        return client.execute(method)
    }

    suspend fun removeDislike(audioFullId: String): VkResult<Boolean> {
        val method = VkMethod(
            "audio.removeDislike",
            MappingVkResponseParser(
                MoshiEnvelopeParser<Int>(Int::class.javaObjectType),
            ) { it != 0 },
        ).apply {
            param("audio_ids", listOf(audioFullId).joinToString(","))
        }
        return client.execute(method)
    }

    suspend fun add(
        audioFullId: String,
        accessKey: String? = null,
        ref: String? = null,
        trackCode: String? = null,
    ): VkResult<AudioAddResponse> {
        val normalized = audioFullId.removePrefix("vk_")
        val parts = normalized.split('_', limit = 3)
        val fullId = parts.take(2).joinToString("_")
        val resolvedAccessKey = parts.getOrNull(2)?.takeIf(String::isNotBlank) ?: accessKey
        val (ownerId, audioId) = parseAudioFullId(fullId)
        val method = VkMethod(
            "audio.add",
            MoshiEnvelopeParser<AudioAddResponse>(AudioAddResponse::class.java),
        ).apply {
            param("audio_id", audioId)
            param("owner_id", ownerId)
            param("ref", ref)
            param("access_key", resolvedAccessKey)
            param("track_code", trackCode)
        }
        return client.execute(method)
    }

    suspend fun delete(audioFullId: String): VkResult<AudioDeleteExtendedResponseDto> {
        val (ownerId, audioId) = parseAudioFullId(audioFullId)
        val method = VkMethod(
            "audio.delete",
            MoshiEnvelopeParser<AudioDeleteExtendedResponseDto>(
                AudioDeleteExtendedResponseDto::class.java,
            ),
        ).apply {
            param("audio_id", audioId)
            param("owner_id", ownerId)
        }
        return client.execute(method)
    }

    suspend fun restore(audioFullId: String): VkResult<AudioAudioDto> {
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

    suspend fun restoreDetailed(audioFullId: String): VkResult<AudioAudioDto> = restore(audioFullId)

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
        val parts = normalized.split('_', limit = 3)
        require(parts.size >= 2) {
            "Invalid VK audio id: $fullId"
        }
        return parts[0].toLong() to parts[1].toInt()
    }

    private fun bareAudioFullId(fullId: String): String {
        val (ownerId, audioId) = parseAudioFullId(fullId)
        return "${ownerId}_$audioId"
    }

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
            return VkParsedResponse(parsed.data?.let { Unit }, parsed.error, parsed.executeErrors)
        }
    }

    private object AudioIdListParser : VkResponseParser<List<AudioAudioIdDto>> {
        private val delegate = MoshiEnvelopeParser<List<AudioAudioIdDto>>(
            Types.newParameterizedType(List::class.java, AudioAudioIdDto::class.java),
        )

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<AudioAudioIdDto>> =
            delegate.parse(raw)
    }

    private object RestrictionInfoListParser : VkResponseParser<List<AudioRestrictionInfoDto>> {
        private val delegate = MoshiEnvelopeParser<List<AudioRestrictionInfoDto>>(
            Types.newParameterizedType(List::class.java, AudioRestrictionInfoDto::class.java),
        )

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<AudioRestrictionInfoDto>> =
            delegate.parse(raw)
    }

    private object RadioStationListParser : VkResponseParser<List<AudioRadioStationDto>> {
        private val delegate = MoshiEnvelopeParser<List<AudioRadioStationDto>>(
            Types.newParameterizedType(List::class.java, AudioRadioStationDto::class.java),
        )

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<AudioRadioStationDto>> =
            delegate.parse(raw)
    }

    private object ExtendedPlaylistParser : VkResponseParser<AudioPlaylist> {
        private val delegate = MappingVkResponseParser(
            MoshiEnvelopeParser<AudioGetPlaylistExtendedResponseDto>(
                AudioGetPlaylistExtendedResponseDto::class.java,
            ),
        ) { response ->
            response.playlist.copy(
                duration = response.duration ?: response.playlist.duration,
                audio_ids = response.audio_ids?.map { it.audio_id } ?: response.playlist.audio_ids,
            )
        }

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<AudioPlaylist> =
            delegate.parse(raw)
    }

    private object StringListParser : VkResponseParser<List<String>> {
        private val delegate = MoshiEnvelopeParser<List<String>>(
            Types.newParameterizedType(List::class.java, String::class.java),
        )

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<List<String>> =
            delegate.parse(raw)
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
