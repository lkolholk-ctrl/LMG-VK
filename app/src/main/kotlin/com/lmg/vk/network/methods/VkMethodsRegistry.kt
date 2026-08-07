package com.lmg.vk.network.methods

import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkEndpoint
import com.lmg.vk.network.VkHttpMethod
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkParsedResponse
import com.lmg.vk.network.VkResponseParser
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.RawHttpResponse
import com.lmg.vk.network.RecoveredServiceConfig
import com.lmg.vk.network.MoshiEnvelopeParser
import com.lmg.vk.network.MoshiDirectParser
import com.lmg.vk.network.MappingVkResponseParser
import com.lmg.vk.network.VkItems
import com.lmg.vk.network.VkJson
import com.lmg.vk.network.VkUserAgents
import com.lmg.vk.network.dto.SilentCredentials
import com.lmg.vk.network.dto.AnonymTokenResponse
import com.lmg.vk.network.dto.AuthGetAuthCodeStatusResponse
import com.lmg.vk.network.dto.AuthGetExchangeTokenResponse
import com.lmg.vk.network.dto.AuthProcessAuthCodeResponse
import com.lmg.vk.network.dto.AuthValidateAccountResponse
import com.lmg.vk.network.dto.BaseResult
import com.lmg.vk.network.dto.EcosystemCheckOtpResponse
import com.lmg.vk.network.dto.EcosystemGetVerificationMethodsResponse
import com.lmg.vk.network.dto.EcosystemSendOtpResponse
import com.lmg.vk.network.dto.RequestTokenResponse
import com.lmg.vk.network.dto.VKError
import com.lmg.vk.network.dto.VkAccountProfile
import com.lmg.vk.network.dto.VkFriend
import com.lmg.vk.network.dto.VkGroup
import com.lmg.vk.network.dto.gen.auth.ValidatePhoneResponse
import com.lmg.vk.network.dto.music.AudioAudioDto
import com.lmg.vk.network.dto.music.AudioPlaylistReorderAction
import com.lmg.vk.network.dto.music.ProfileLibrarySearchResponse
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types
import org.json.JSONArray
import org.json.JSONObject

/**
 * Реестр VK-методов LMG VK (v8.12.1) — автоэкстракция из всех точек вызова.
 * Каждый вызов: VkMethod(name, parser) + params + client.execute().
 *
 * Сгруппировано по доменам. Парсеры — Moshi-адаптеры соответствующих DTO
 * (см. dto/gen + vkapi2/methods в jadx).
 */
class VkMethodsRegistry(private val client: VkApiClient) {

    // ======================= AUDIO (дополнение к VkAudioApi) =======================

    /** audio.add — добавить трек к себе. */
    suspend fun audioAdd(audioId: Int, ownerId: Long, accessKey: String? = null) =
        executeUnit("audio.add") {
            param("audio_id", audioId); param("owner_id", ownerId); param("access_key", accessKey)
        }

    /** audio.delete / audio.restore. */
    suspend fun audioDelete(audioId: Int, ownerId: Long) =
        executeUnit("audio.delete") { param("audio_id", audioId); param("owner_id", ownerId) }

    suspend fun audioRestore(audioId: Int, ownerId: Long) =
        executeUnit("audio.restore") { param("audio_id", audioId); param("owner_id", ownerId) }

    /** Новый bruhcollective-контракт: `audio.restore` возвращает `AudioAudioDto`. */
    suspend fun audioRestoreDetailed(audioId: Int, ownerId: Long): VkResult<AudioAudioDto> {
        val method = VkMethod(
            "audio.restore",
            MoshiEnvelopeParser<AudioAudioDto>(AudioAudioDto::class.java),
        ).apply {
            param("audio_id", audioId)
            param("owner_id", ownerId)
        }
        return client.execute(method)
    }

    /** audio.getAudioIdsBySource — resolve id треков по источнику (пост/аттач). */
    suspend fun getAudioIdsBySource(source: String, entityId: String): VkResult<List<String>> {
        val method = VkMethod("audio.getAudioIdsBySource", AudioIdsParser).apply {
            param("source", source); param("entity_id", entityId)
        }
        return client.execute(method)
    }

    /** audio.getAudioPreviewUrl — превью-трек (обрезанный). */
    suspend fun getAudioPreviewUrl(audioId: String, previewType: String = "longtap"): VkResult<String> {
        val method = VkMethod("audio.getAudioPreviewUrl", PreviewUrlParser).apply {
            param("audio_id", audioId); param("preview_type", previewType)
        }
        return client.execute(method)
    }

    /** audio.getRelatedArtistsById. */
    suspend fun getRelatedArtists(artistId: String, offset: Int, count: Int) =
        execute<Any>("audio.getRelatedArtistsById") {
            param("artist_id", artistId); param("offset", offset); param("count", count)
        }

    /** audio.getStreamMixSettings — настройки «микса» (волна/поток). */
    suspend fun getStreamMixSettings(mixId: String) =
        execute<Any>("audio.getStreamMixSettings") { param("mix_id", mixId) }

    /**
     * audio.reorderInPlaylist.
     *
     * `actions` — обязательный параметр (`C3294e.java:17-70`); без него VK не
     * знает, что и куда переместить, поэтому перегрузки без него нет.
     * Формат — позиционные тройки, см. [AudioPlaylistReorderAction].
     */
    suspend fun reorderInPlaylist(
        playlistId: Int,
        ownerId: Long,
        actions: List<AudioPlaylistReorderAction>,
    ) = executeUnit("audio.reorderInPlaylist") {
        param("playlist_id", playlistId)
        param("owner_id", ownerId)
        param("actions", AudioPlaylistReorderAction.encode(actions))
    }

    /**
     * audio.recommendationsOnboarding — исполнители для онбординга рекомендаций.
     * Параметров нет вовсе (`C14197e.java:104-109`).
     *
     * Форма ответа подтверждена лишь частично: место вызова
     * (`C14197e.java:115-124`) кастует результат к `RootItemsResponseDto` и
     * берёт `.items`, поэтому ждём `{count, items:[…]}`; `P1:241` при этом
     * обещает один объект артиста. До проверки на живом API парсер общий.
     */
    suspend fun recommendationsOnboarding() =
        execute<Any>("audio.recommendationsOnboarding") { }

    /** audio.followRadioStation / unfollow. */
    suspend fun followRadioStation(stationId: Int) =
        executeUnit("audio.followRadioStation") { param("station_id", stationId) }

    suspend fun unfollowRadioStation(stationId: Int) =
        executeUnit("audio.unfollowRadioStation") { param("station_id", stationId) }

    /** audio.searchArtists / searchMain. */
    suspend fun searchArtists(query: String, offset: Int, count: Int) =
        execute<Any>("audio.searchArtists") {
            param("q", query); param("offset", offset); param("count", count)
        }

    suspend fun searchMain(query: String, offset: Int, count: Int) =
        execute<Any>("audio.searchMain") {
            param("q", query); param("count", count); param("offset", offset)
        }

    // ======================= AUDIOBOOKS =======================

    suspend fun audioBookAddToFavorites(bookId: Int) =
        executeUnit("audioBooks.addToFavorites") { param("audio_book_id", bookId) }

    /** Wire-ответ C2610e: `{ result: "0"|"1" }`. */
    suspend fun audioBookAddToFavoritesDetailed(bookId: Int): VkResult<BaseResult> {
        val method = VkMethod(
            "audioBooks.addToFavorites",
            MoshiEnvelopeParser<BaseResult>(BaseResult::class.java),
        ).apply { param("audio_book_id", bookId) }
        return client.execute(method)
    }

    suspend fun audioBookDeleteFromFavorites(bookId: Int) =
        executeUnit("audioBooks.deleteFromFavorites") { param("audio_book_id", bookId) }

    suspend fun audioBookGetById(bookId: Int) =
        execute<Any>("audioBooks.getAudioBookById") { param("audio_book_id", bookId) }

    suspend fun audioBookSetProgress(chapterId: String, timeFromStart: Int) =
        executeUnit("audioBooks.setProgress") {
            param("chapter_id", chapterId); param("time_from_start", timeFromStart)
        }

    suspend fun audioBookSetProgressDetailed(
        chapterId: String,
        timeFromStart: Int,
    ): VkResult<BaseResult> {
        val method = VkMethod(
            "audioBooks.setProgress",
            MoshiEnvelopeParser<BaseResult>(BaseResult::class.java),
        ).apply {
            param("chapter_id", chapterId)
            param("time_from_start", timeFromStart)
        }
        return client.execute(method)
    }

    // ======================= PODCASTS =======================

    suspend fun podcastSubscribe(ownerId: Long) =
        executeUnit("podcasts.subscribe") { param("owner_id", ownerId) }

    suspend fun podcastUnsubscribe(ownerId: Long) =
        executeUnit("podcasts.unsubscribe") { param("owner_id", ownerId) }

    suspend fun podcastGetEpisode(ownerId: Long, episodeId: Int) =
        execute<Any>("podcasts.getEpisode") {
            param("owner_id", ownerId); param("episode_id", episodeId)
        }

    suspend fun podcastGetRandomEpisode() = execute<Any>("podcasts.getRandomEpisode") {}

    /** Stored execute-функция; в C4600e billing/yandex = execute/getPodcastEpisodesWithInfo. */
    suspend fun podcastGetEpisodesWithInfo(ownerId: Long) =
        execute<Any>("execute.getPodcastEpisodesWithInfo") {
            param("owner_id", ownerId)
            param("count", 100)
            param("offset", 0)
            param("func_v", 4)
        }

    // ======================= MESSAGES / PHOTOS / NEWSFEED =======================

    suspend fun messagesGetHistoryAttachments(
        peerId: Long,
        count: Int,
        startFrom: String? = null,
    ) = execute<Any>("messages.getHistoryAttachments") {
        param("peer_id", peerId)
        param("media_type", "audio")
        param("count", count)
        param("start_from", startFrom)
    }

    suspend fun messagesGetConversations(count: Int, offset: Int) =
        execute<Any>("messages.getConversations") {
            param("count", count)
            param("offset", offset)
            param("extended", 1)
            param("fields", PROFILE_FIELDS)
        }

    suspend fun messagesSearchConversations(query: String) =
        execute<Any>("messages.searchConversations") {
            param("q", query)
            param("count", 40)
            param("extended", 1)
            param("fields", PROFILE_FIELDS)
        }

    suspend fun getAudioPlaylistCoverUploadServer(playlistId: Int, ownerId: Long) =
        execute<Any>("photos.getAudioPlaylistCoverUploadServer") {
            param("playlist_id", playlistId); param("owner_id", ownerId)
        }

    suspend fun getAudioUploadServer() = execute<Any>("audio.getUploadServer") {}

    suspend fun newsfeedGet(
        count: Int,
        startFrom: String? = null,
        listId: Int? = null,
    ) = execute<Any>("newsfeed.get") {
        param("count", count)
        param("extended", 1)
        param("start_from", startFrom)
        if (listId != null) param("source_ids", "list$listId")
        else param("filters", "audio,audio_playlist")
    }

    suspend fun newsfeedGetDiscoverCustom(count: Int, startFrom: String? = null) =
        execute<Any>("newsfeed.getDiscoverCustom") {
            param("count", count)
            param("extended", 1)
            param("start_from", startFrom)
            param("discover_id", "discover_category_full/16")
        }

    suspend fun newsfeedGetLists() = execute<Any>("newsfeed.getLists") {}

    // ======================= USERS / UTILS / STORAGE =======================

    suspend fun usersGet(userId: Long, fields: String = "photo_base,is_followed,can_follow") =
        execute<Any>("users.get") { param("fields", fields); param("user_id", userId) }

    /**
     * Профиль владельца текущего access token после успешного OAuth-входа.
     *
     * `counters` VK возвращает только для самого себя, поэтому расширенный набор
     * полей запрашивается именно здесь, а не в [usersGet].
     */
    suspend fun usersGetCurrent(): VkResult<List<VkAccountProfile>> {
        val listType = Types.newParameterizedType(List::class.java, VkAccountProfile::class.java)
        val method = VkMethod(
            "users.get",
            MoshiEnvelopeParser<List<VkAccountProfile>>(listType),
        ).apply {
            param("fields", CURRENT_PROFILE_FIELDS)
        }
        return client.execute(method)
    }

    /** Профиль другого пользователя с тем же набором полей, что и у себя. */
    suspend fun usersGetProfile(userId: Long): VkResult<List<VkAccountProfile>> {
        val listType = Types.newParameterizedType(List::class.java, VkAccountProfile::class.java)
        val method = VkMethod(
            "users.get",
            MoshiEnvelopeParser<List<VkAccountProfile>>(listType),
        ).apply {
            param("fields", CURRENT_PROFILE_FIELDS)
            param("user_ids", userId)
        }
        return client.execute(method)
    }

    /** Точный вариант AbstractC15297e.license. */
    suspend fun usersGetPhoto(userId: Long) = execute<Any>("users.get") {
        param("fields", "photo_100")
        param("user_id", userId)
    }

    /** Точный вариант AbstractC15297e.vip; токен передаётся явно параметром. */
    suspend fun usersGetPhoto(accessToken: String) = execute<Any>("users.get") {
        param("fields", "photo_100")
        param("access_token", accessToken)
    }

    /**
     * Друзья владельца токена. `count` из ответа сохраняется — это реальное
     * общее число друзей, а не размер выданной страницы.
     */
    suspend fun friendsGet(
        offset: Int,
        count: Int = 40,
        order: String = "hints",
    ): VkResult<VkItems<VkFriend>> {
        val itemsType = Types.newParameterizedType(VkItems::class.java, VkFriend::class.java)
        val method = VkMethod(
            "friends.get",
            MoshiEnvelopeParser<VkItems<VkFriend>>(itemsType),
        ).apply {
            param("extended", 1)
            param("offset", offset)
            param("count", count)
            param("fields", FRIEND_FIELDS)
            param("order", order)
        }
        return client.execute(method)
    }

    /** Сообщества владельца токена; для аудио нужен `-id` (см. `VkGroup.audioOwnerId`). */
    suspend fun groupsGet(offset: Int, count: Int = 40): VkResult<VkItems<VkGroup>> {
        val itemsType = Types.newParameterizedType(VkItems::class.java, VkGroup::class.java)
        val method = VkMethod(
            "groups.get",
            MoshiEnvelopeParser<VkItems<VkGroup>>(itemsType),
        ).apply {
            param("extended", 1)
            param("offset", offset)
            param("count", count)
            param("fields", GROUP_FIELDS)
        }
        return client.execute(method)
    }

    /**
     * Одно сообщество по id — вход для экрана сообщества и для ссылок
     * `vk.com/club<id>` / `vk.com/public<id>`.
     *
     * [groupId] можно передавать и отрицательным (owner_id, как в ссылках):
     * `groups.getById` ждёт id сообщества БЕЗ знака, поэтому знак снимается —
     * на `group_ids=-123` VK ответил бы ошибкой.
     *
     * Возвращается контейнер, а не `VkGroup?`, СПЕЦИАЛЬНО: `VkApiClient.execute`
     * на паре «нет ошибки, но data == null» бросает исключение с внутренним
     * текстом «needs investigating» (см. его `unboxVkResponse`). А пустой ответ
     * здесь — штатный случай (несуществующий id), и он обязан дойти до экрана
     * как «сообщество не найдено», а не как невнятная ошибка. Контейнер всегда
     * не-null, поэтому этой ветки не возникает.
     */
    suspend fun groupsGetById(groupId: Long): VkResult<GroupsByIdResponse> {
        val method = VkMethod("groups.getById", GroupByIdParser).apply {
            param("group_ids", kotlin.math.abs(groupId))
            param("fields", GROUP_DETAIL_FIELDS)
        }
        return client.execute(method)
    }

    /**
     * Участники сообщества — только аватары для строки «кто подписан».
     * `fields` узкий намеренно: экрану нужна лишь картинка и имя, а каждое
     * лишнее поле здесь умножается на count.
     */
    suspend fun groupsGetMembers(
        groupId: Long,
        offset: Int = 0,
        count: Int = 20,
    ): VkResult<VkItems<VkFriend>> {
        val itemsType = Types.newParameterizedType(VkItems::class.java, VkFriend::class.java)
        val method = VkMethod(
            "groups.getMembers",
            MoshiEnvelopeParser<VkItems<VkFriend>>(itemsType),
        ).apply {
            param("group_id", kotlin.math.abs(groupId))
            param("offset", offset)
            param("count", count)
            param("fields", GROUP_MEMBER_FIELDS)
        }
        return client.execute(method)
    }

    /**
     * Подписка на сообщество. Пара `groups.join`/`groups.leave` — ровно то, чем
     * подписывается официальный клиент (реверс VK MP3 Mod:
     * `api/groups/GroupsJoin.java`, `api/groups/GroupsLeave.java`); оба метода
     * принимают ПОЛОЖИТЕЛЬНЫЙ `group_id` и возвращают `1`, а не объект.
     *
     * Это не `audio.followOwner`: тот подписывает на МУЗЫКУ владельца и в
     * `is_member` сообщества не отражается, так что кнопка «Subscribed» после
     * него врала бы.
     */
    suspend fun groupsJoin(groupId: Long) =
        executeUnit("groups.join") { param("group_id", kotlin.math.abs(groupId)) }

    suspend fun groupsLeave(groupId: Long) =
        executeUnit("groups.leave") { param("group_id", kotlin.math.abs(groupId)) }

    suspend fun resolveScreenName(screenName: String) =
        execute<Any>("utils.resolveScreenName") { param("screen_name", screenName) }

    /** storage.get/set — KV-хранилище VK (app_id = LMG VK). */
    suspend fun storageGet(
        keys: Collection<String>,
        appId: Int = VKX_STORAGE_APP_ID,
    ): VkResult<Map<String, String>> {
        val method = VkMethod("storage.get", StorageParser).apply {
            param("keys", keys.joinToString(","))
            param("app_id", appId)
        }
        return client.execute(method)
    }

    suspend fun storageSet(key: String, value: String, appId: Int) =
        executeUnit("storage.set") {
            param("key", key); param("value", value); param("app_id", appId)
        }

    // ======================= STATS / MISC =======================

    suspend fun statsTrackEvents(eventsJson: String) =
        executeUnit("stats.trackEvents") { param("events", eventsJson) }

    suspend fun musicStatCreatePlaylist(title: String) =
        execute<Any>("musicStatResults.createPlaylist") { param("title", title) }

    suspend fun musicStatGetMetrics() = execute<Any>("musicStatResults.getMetrics") {}

    suspend fun studioGetArtistYearRecap(artistId: String) =
        execute<Any>("studio.getArtistYearRecapData") { param("artist_id", artistId) }

    suspend fun getAudioPrivacySetting() = execute<Any>("execute") {
        param("code", AUDIO_PRIVACY_EXECUTE_CODE)
    }

    suspend fun searchInProfile(ownerId: Long, query: String): VkResult<ProfileLibrarySearchResponse> {
        val method = VkMethod(
            "execute",
            MoshiEnvelopeParser<ProfileLibrarySearchResponse>(ProfileLibrarySearchResponse::class.java),
        ).apply {
            param("owner_id", ownerId)
            param("query", query)
            param("p_count", 10)
            param("a_count", 30)
            param("code", SEARCH_IN_PROFILE_EXECUTE_CODE)
        }
        return client.execute(method)
    }

    // ======================= AUTH FLOW =======================

    /**
     * `auth.getCredentialsForService` (C4600e, branch 14).
     * Это отдельная UMA-авторизация; её app_id/app_secret не относятся к VK OAuth.
     */
    suspend fun getCredentialsForService(
        uuid: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): VkResult<List<SilentCredentials>> {
        val listType = Types.newParameterizedType(List::class.java, SilentCredentials::class.java)
        val method = VkMethod(
            "auth.getCredentialsForService",
            MoshiEnvelopeParser<List<SilentCredentials>>(listType),
        ).apply {
            param("uuid", uuid)
            param("timestamp", timestampMs)
            param("digest_hash", RecoveredServiceConfig.UMA_DIGEST_HASH)
            param("package", RecoveredServiceConfig.UMA_PACKAGE)
            param("app_id", RecoveredServiceConfig.UMA_APP_ID)
            param("app_secret", RecoveredServiceConfig.UMA_APP_SECRET)
        }
        return client.execute(method)
    }

    /** Старый отдельный `auth.refreshToken` из C4600e, не refreshTokens Android OAuth. */
    suspend fun refreshLegacyServiceToken(
        receipt: String,
        receipt2: String,
        nonce: String,
        timestampMs: Long = System.currentTimeMillis(),
    ) = execute<Any>("auth.refreshToken") {
        param("receipt", receipt)
        param("receipt2", receipt2)
        param("timestamp", timestampMs)
        param("nonce", nonce)
    }

    /** auth.validateAccount — первый шаг логина. */
    suspend fun validateAccount(
        login: String,
        anonymousToken: String,
        trustedHash: String?,
    ): VkResult<AuthValidateAccountResponse> {
        val method = VkMethod(
            "auth.validateAccount",
            MoshiEnvelopeParser<AuthValidateAccountResponse>(AuthValidateAccountResponse::class.java),
        ).apply {
            param("login", login)
            param("force_password", false)
            param("passkey_supported", 0)
            param("supported_ways", "callreset,codegen,email,reserve_code,password,push,sms")
            param("flow_type", "auth_without_password")
            param("sak_version", "1.112")
            param("access_token", anonymousToken)
            param("accounts_trusted_hashes", trustedHash)
            userAgent = VkUserAgents.auth
        }
        return client.execute(method)
    }

    /** `auth.validatePhone` из C16600e/C16628e. */
    suspend fun validatePhone(sid: String): VkResult<ValidatePhoneResponse> {
        val method = VkMethod(
            "auth.validatePhone",
            MoshiEnvelopeParser<ValidatePhoneResponse>(ValidatePhoneResponse::class.java),
        ).apply {
            param("sid", sid)
            param("client_id", VkApiClient.VK_ANDROID_CLIENT_ID)
            param("client_secret", RecoveredServiceConfig.VK_ANDROID_CLIENT_SECRET)
            param("libverify_support", "0")
            param("allow_callreset", "0")
            param("disable_partial", "0")
            param("supported_ways", "push,email")
        }
        return client.execute(method)
    }

    /** `auth.getExchangeToken`, подтверждённый вариант API 5.180. */
    suspend fun getExchangeToken(): VkResult<AnonymTokenResponse> {
        val method = VkMethod(
            "auth.getExchangeToken",
            MoshiEnvelopeParser<AnonymTokenResponse>(AnonymTokenResponse::class.java),
        ).apply {
            apiVersion = "5.180"
            param("create_common_token", true)
            param("create_tier_tokens", "0")
        }
        return client.execute(method)
    }

    /** Вторая ветка того же метода: список exchange-токенов пользователей. */
    suspend fun getUserExchangeTokens(): VkResult<AuthGetExchangeTokenResponse> {
        val method = VkMethod(
            "auth.getExchangeToken",
            MoshiEnvelopeParser<AuthGetExchangeTokenResponse>(AuthGetExchangeTokenResponse::class.java),
        ).apply {
            param("create_common_token", true)
            param("create_tier_tokens", "0")
            userAgent = VkUserAgents.auth
        }
        return client.execute(method)
    }

    /** Получить common exchange token сразу после OAuth-входа. */
    suspend fun getUserExchangeTokens(accessToken: String): VkResult<AuthGetExchangeTokenResponse> {
        val method = VkMethod(
            "auth.getExchangeToken",
            MoshiEnvelopeParser<AuthGetExchangeTokenResponse>(AuthGetExchangeTokenResponse::class.java),
        ).apply {
            param("create_common_token", true)
            param("create_tier_tokens", "0")
            param("access_token", accessToken)
            userAgent = VkUserAgents.auth
        }
        return client.execute(method)
    }

    /** auth.processAuthCode(Multi) — подтверждение кода 2FA. */
    suspend fun processAuthCode(
        action: Int,
        authCode: String,
    ): VkResult<AuthProcessAuthCodeResponse> {
        val method = VkMethod(
            "auth.processAuthCode",
            MoshiEnvelopeParser<AuthProcessAuthCodeResponse>(AuthProcessAuthCodeResponse::class.java),
        ).apply {
            param("action", action)
            param("auth_code", authCode)
        }
        return client.execute(method)
    }

    suspend fun processAuthCodeMulti(action: Int, authCode: String, accessTokens: String) =
        execute<Any>("auth.processAuthCodeMulti") {
            param("action", action); param("auth_code", authCode); param("access_tokens", accessTokens)
        }

    /** ecosystem.* — OTP-флоу (новая авторизация VK ID). */
    suspend fun ecosystemCheckOtp(
        sid: String,
        code: String,
        verificationMethod: String,
        anonymousToken: String,
    ): VkResult<EcosystemCheckOtpResponse> {
        val method = VkMethod(
            "ecosystem.checkOtp",
            MoshiEnvelopeParser<EcosystemCheckOtpResponse>(EcosystemCheckOtpResponse::class.java),
        ).apply {
            param("sid", sid)
            param("code", code)
            param("verification_method", verificationMethod.ifBlank { "codegen" })
            param("access_token", anonymousToken)
            userAgent = VkUserAgents.auth
        }
        return client.execute(method)
    }

    suspend fun ecosystemSendOtp(
        kind: OtpKind,
        sid: String,
        anonymousToken: String,
    ): VkResult<EcosystemSendOtpResponse> {
        val method = VkMethod(
            "ecosystem.sendOtp${kind.wireName}",
            MoshiEnvelopeParser<EcosystemSendOtpResponse>(EcosystemSendOtpResponse::class.java),
        ).apply {
            param("sid", sid)
            param("access_token", anonymousToken)
            userAgent = VkUserAgents.auth
        }
        return client.execute(method)
    }

    suspend fun ecosystemGetVerificationMethods(
        sid: String,
        anonymousToken: String,
    ): VkResult<EcosystemGetVerificationMethodsResponse> {
        val method = VkMethod(
            "ecosystem.getVerificationMethods",
            MoshiEnvelopeParser<EcosystemGetVerificationMethodsResponse>(
                EcosystemGetVerificationMethodsResponse::class.java,
            ),
        ).apply {
            param("sid", sid)
            param("access_token", anonymousToken)
            userAgent = VkUserAgents.auth
        }
        return client.execute(method)
    }

    suspend fun getAuthCodeStatus(
        authCode: String,
        accessToken: String,
    ): VkResult<AuthGetAuthCodeStatusResponse> {
        val method = VkMethod(
            "auth.getAuthCodeStatus",
            MoshiEnvelopeParser<AuthGetAuthCodeStatusResponse>(AuthGetAuthCodeStatusResponse::class.java),
        ).apply {
            param("auth_code", authCode)
            param("access_token", accessToken)
        }
        return client.execute(method)
    }

    suspend fun setAuthCodeStatus(authCode: String, accessToken: String) =
        executeUnit("auth.setAuthCodeStatus") {
            param("auth_code", authCode)
            param("access_token", accessToken)
        }

    enum class OtpKind(val wireName: String) { Sms("Sms"), Email("Email"), Push("Push"), CallReset("CallReset") }

    /** get_anonym_token — анонимный токен (client credentials). */
    suspend fun getAnonymToken(): VkResult<AnonymTokenResponse> {
        val method = VkMethod(
            "get_anonym_token",
            MoshiDirectParser<AnonymTokenResponse>(AnonymTokenResponse::class.java),
        ).apply {
            endpoint = VkEndpoint.API_OAUTH
            param("client_id", VkApiClient.VK_ANDROID_CLIENT_ID)
            param("client_secret", RecoveredServiceConfig.VK_ANDROID_CLIENT_SECRET)
            param("app_id", VkApiClient.VK_ANDROID_CLIENT_ID)
            userAgent = VkUserAgents.api
        }
        return client.execute(method)
    }

    /** OAuth token официального Android-клиента после validateAccount/checkOtp. */
    suspend fun oauthToken(
        username: String,
        password: String,
        sid: String,
        anonymousToken: String,
        grantType: String,
        extraParams: Map<String, String> = emptyMap(),
    ): VkResult<RequestTokenResponse> {
        val method = VkMethod("token", RequestTokenParser).apply {
            endpoint = VkEndpoint.OAUTH
            httpMethod = VkHttpMethod.GET
            userAgent = VkUserAgents.api
            param("grant_type", grantType)
            param("username", username)
            param("password", password)
            param("scope", "all")
            param("client_id", VkApiClient.VK_ANDROID_CLIENT_ID)
            param("client_secret", RecoveredServiceConfig.VK_ANDROID_CLIENT_SECRET)
            param("2fa_supported", true)
            param("vk_connect_auth", true)
            param("libverify_support", false)
            param("sid", sid)
            param("anonymous_token", anonymousToken)
            params.putAll(extraParams)
        }
        return client.execute(method)
    }

    // ======================= engine =======================

    private suspend fun <T> execute(name: String, block: VkMethod<T>.() -> Unit): VkResult<T> {
        @Suppress("UNCHECKED_CAST")
        val method = VkMethod(name, GenericParser as VkResponseParser<T>).apply {
            block()
        }
        return client.execute(method)
    }

    private suspend fun executeUnit(name: String, block: VkMethod<Unit>.() -> Unit): VkResult<Unit> {
        val method = VkMethod(name, UnitParser).apply(block)
        return client.execute(method)
    }

    /** Универсальный парсер конверта (в оригинале — per-method синглтоны). */
    private object GenericParser : VkResponseParser<Any> {
        private val delegate = MoshiEnvelopeParser<Any>(Any::class.java)

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<Any> {
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

    /** C15802e: OAuth `token` возвращает одну из шести sealed-веток прямо в корне JSON. */
    private object RequestTokenParser : VkResponseParser<RequestTokenResponse> {
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<RequestTokenResponse> {
            val body = raw.bodyText()
            val json = JSONObject(body)
            if (json.has("processing")) {
                return VkParsedResponse(RequestTokenResponse.Processing, null)
            }
            val nestedError = json.optJSONObject("error")
            val type = when {
                nestedError?.optInt("error_code") == 14 -> RequestTokenResponse.CaptchaRequired::class.java
                nestedError != null -> RequestTokenResponse.NestedApiError::class.java
                json.has("validation_type") || json.has("validation_sid") ->
                    RequestTokenResponse.TwoFactorRequired::class.java
                json.has("captcha_sid") || json.has("captcha_img") ->
                    RequestTokenResponse.CaptchaRequired::class.java
                json.has("access_token") -> RequestTokenResponse.Success::class.java
                json.has("error_type") -> RequestTokenResponse.ClientError::class.java
                else -> RequestTokenResponse.UnknownError::class.java
            }
            val jsonToParse = if (type == RequestTokenResponse.CaptchaRequired::class.java && nestedError != null) {
                nestedError.toString()
            } else {
                body
            }
            val data = requireNotNull(VkJson.moshi.adapter(type).fromJson(jsonToParse)) {
                "Empty OAuth token response from ${raw.url}"
            }
            return VkParsedResponse(data, null)
        }
    }

    @JsonClass(generateAdapter = true)
    data class AudioRawId(
        @Json(name = "audio_id") val audioId: String,
        @Json(name = "track_code") val trackCode: String,
    )

    @JsonClass(generateAdapter = true)
    data class AudioIdsResponse(val audios: List<AudioRawId> = emptyList())

    @JsonClass(generateAdapter = true)
    data class PreviewUrlResponse(
        val url: String,
        @Json(name = "clip_from") val clipFrom: Int = 0,
        @Json(name = "clip_to") val clipTo: Int = 0,
        @Json(name = "streamDuration") val streamDuration: Int = (clipTo - clipFrom) / 1000,
    )

    @JsonClass(generateAdapter = true)
    data class StorageItem(val key: String, val value: String)

    private object AudioIdsParser : VkResponseParser<List<String>> {
        private val delegate = MappingVkResponseParser(
            MoshiEnvelopeParser<AudioIdsResponse>(AudioIdsResponse::class.java),
        ) { response -> response.audios.map(AudioRawId::audioId) }

        override suspend fun parse(raw: RawHttpResponse) = delegate.parse(raw)
    }

    private object PreviewUrlParser : VkResponseParser<String> {
        private val delegate = MappingVkResponseParser(
            MoshiEnvelopeParser<PreviewUrlResponse>(PreviewUrlResponse::class.java),
            PreviewUrlResponse::url,
        )

        override suspend fun parse(raw: RawHttpResponse) = delegate.parse(raw)
    }

    private object StorageParser : VkResponseParser<Map<String, String>> {
        private val delegate = MappingVkResponseParser(
            MoshiEnvelopeParser<List<StorageItem>>(
                Types.newParameterizedType(List::class.java, StorageItem::class.java),
            ),
        ) { items -> items.associate { it.key to it.value } }

        override suspend fun parse(raw: RawHttpResponse) = delegate.parse(raw)
    }

    /**
     * Результат `groups.getById`. Обёртка нужна, чтобы «сообщества нет» дошло до
     * вызывающего как успех с пустым [group], а не как исключение из
     * `VkApiClient.execute` на null-данных.
     */
    data class GroupsByIdResponse(val group: VkGroup?)

    /**
     * `groups.getById` меняла форму ответа между версиями API: до v5.130
     * `response` — плоский массив (так его и читает `GroupsGetById.java` в
     * реверсе VK MP3 Mod: `response[0]`), с v5.130+ — объект `{"groups": [...]}`
     * (VK X: `API.groups.getById({...}).groups[0]`, `C13029e.java:41`). Проект
     * ходит с `v=5.272`, но поддерживаются обе формы: различаем по фактическому
     * типу узла, а не по версии в запросе — версию задаёт [VkMethod], и
     * молчаливая рассинхронизация с ней стоила бы пустого экрана.
     *
     * Тело читается РОВНО ОДИН раз: `bodyText()` у Ktor-обёртки читает канал
     * ответа, и на второй вызов для того же ответа полагаться нельзя. Поэтому
     * обе формы разбираются из уже полученной строки, а не двумя проходами
     * готовых парсеров по [RawHttpResponse].
     */
    private object GroupByIdParser : VkResponseParser<GroupsByIdResponse> {
        private val groupAdapter = VkJson.moshi.adapter(VkGroup::class.java)

        // Ошибку разбираем тем же адаптером, что и остальные методы: собирать
        // VKError вручную значило бы потерять captcha_sid/redirect_uri, на
        // которых держится ретрай капчи и валидации в VkApiClient.
        private val errorAdapter = VkJson.moshi.adapter(VKError::class.java)

        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<GroupsByIdResponse> {
            val json = JSONObject(raw.bodyText())

            // Ошибку смотрим ДО ветвления по форме: при ошибке `response` в
            // ответе отсутствует вовсе.
            json.optJSONObject("error")?.let { error ->
                return VkParsedResponse(null, errorAdapter.fromJson(error.toString()))
            }

            val first = when (val response = json.opt("response")) {
                is JSONObject -> response.optJSONArray("groups")?.optJSONObject(0)
                is JSONArray -> response.optJSONObject(0)
                else -> null
            }

            return VkParsedResponse(
                GroupsByIdResponse(first?.let { groupAdapter.fromJson(it.toString()) }),
                null,
            )
        }
    }

    private companion object {
        const val VKX_STORAGE_APP_ID = 52384530
        const val PROFILE_FIELDS = "first_name,last_name,name,photo_100,photo_200"

        /**
         * Полный набор `fields` для карточки профиля. Имена сверены со строками
         * официального клиента; `counters` VK отдаёт только владельцу токена.
         */
        const val CURRENT_PROFILE_FIELDS = "photo_base,name,is_followed,can_follow," +
            "photo_100,photo_200,photo_200_orig,photo_400_orig,photo_max_orig," +
            "domain,screen_name,status,bdate,city,country,followers_count,counters," +
            "online,online_info,last_seen,verified,sex"

        const val FRIEND_FIELDS =
            "photo_100,photo_200,domain,screen_name,online,online_info,last_seen,verified,sex,can_see_audio"

        const val GROUP_FIELDS = "photo_100,photo_200,members_count,verified,is_member"

        /**
         * `fields` для карточки сообщества. Имена НЕ выдуманы — каждое реально
         * читается из ответа `groups.getById` в реверсе VK MP3 Mod
         * (`api/users/GetFullProfile.java`, ветка `uid < 0`, строки 462-596):
         * `name`, `activity` → infoLine, `status.text`, `description`, `site`,
         * `start_date`/`finish_date`, `admin_level`, `can_post`, `can_message`,
         * `is_closed`, `is_member`, `members_count`, `type`, `deactivated`.
         * `counters` — оттуда же (`AudioGet.java:36` берёт `counters.audios`),
         * но VK отдаёт их только управляющим сообщества.
         *
         * `cover` и крупные аватары в `fields` перечислены отдельно: без запроса
         * VK их не присылает, а шапке нужен кадр шире `photo_200`.
         */
        const val GROUP_DETAIL_FIELDS = "activity,description,status,site," +
            "members_count,counters,verified,is_member,is_closed,type,deactivated," +
            "admin_level,can_post,can_message,start_date,finish_date," +
            "screen_name,photo_100,photo_200,photo_400_orig,photo_max_orig,cover"

        /** Участникам в списке нужны только аватар и имя — остальное лишний трафик. */
        const val GROUP_MEMBER_FIELDS = "photo_100,photo_200,screen_name,domain,verified"

        const val AUDIO_PRIVACY_EXECUTE_CODE = """var settings = API.account.getPrivacySettings();
var i = 0;
while (i != settings.settings.length) {
    if (settings.settings[i].key == "audios") { return settings.settings[i]; }
    i = i + 1;
};
return null;"""
        const val SEARCH_IN_PROFILE_EXECUTE_CODE = """return {"playlists": API.audio.searchPlaylists({"owner_id": Args.owner_id, "q": Args.query, "count": Args.p_count, "filters": "owned", "extended": 1}), "audios": API.audio.search({"search_own": 1, "owner_id": Args.owner_id, "q": Args.query, "count": Args.a_count}).items};"""
    }
}
