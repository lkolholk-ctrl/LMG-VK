package com.lmg.vk.network.methods

import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkParsedResponse
import com.lmg.vk.network.VkResponseParser
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.RawHttpResponse

/**
 * Реестр VK-методов LMG VK (v8.12.1) — автоэкстракция из всех точек вызова.
 * Каждый вызов: VkMethod(name, parser) + params + client.execute().
 *
 * Сгруппировано по доменам. Парсеры — Moshi-адаптеры соответствующих DTO
 * (см. dto/gen + vkapi2/methods/ * в jadx).
 */
class VkMethodsRegistry(private val client: VkApiClient) {

    // ======================= AUDIO (дополнение к VkAudioApi) =======================

    / ** audio.add — добавить трек к себе. */
    suspend fun audioAdd(audioId: Int, ownerId: Long, accessKey: String? = null) =
        execute<Unit>("audio.add") {
            param("audio_id", audioId); param("owner_id", ownerId); param("access_key", accessKey)
        }

    / ** audio.delete / audio.restore. */
    suspend fun audioDelete(audioId: Int, ownerId: Long) =
        execute<Unit>("audio.delete") { param("audio_id", audioId); param("owner_id", ownerId) }

    suspend fun audioRestore(audioId: Int, ownerId: Long) =
        execute<Unit>("audio.restore") { param("audio_id", audioId); param("owner_id", ownerId) }

    / ** audio.getAudioIdsBySource — resolve id треков по источнику (пост/аттач). */
    suspend fun getAudioIdsBySource(source: String, entityId: String) =
        execute<List<String>>("audio.getAudioIdsBySource") {
            param("source", source); param("entity_id", entityId)
        }

    / ** audio.getAudioPreviewUrl — превью-трек (обрезанный). */
    suspend fun getAudioPreviewUrl(audioId: String, previewType: String) =
        execute<String>("audio.getAudioPreviewUrl") {
            param("audio_id", audioId); param("preview_type", previewType)
        }

    / ** audio.getRelatedArtistsById. */
    suspend fun getRelatedArtists(artistId: String, offset: Int, count: Int) =
        execute<Any>("audio.getRelatedArtistsById") {
            param("artist_id", artistId); param("offset", offset); param("count", count)
        }

    / ** audio.getStreamMixSettings — настройки «микса» (волна/поток). */
    suspend fun getStreamMixSettings(mixId: String) =
        execute<Any>("audio.getStreamMixSettings") { param("mix_id", mixId) }

    / ** audio.reorderInPlaylist. */
    suspend fun reorderInPlaylist(playlistId: Int, ownerId: Long) =
        execute<Unit>("audio.reorderInPlaylist") {
            param("playlist_id", playlistId); param("owner_id", ownerId)
        }

    / ** audio.followRadioStation / unfollow. */
    suspend fun followRadioStation(stationId: Int) =
        execute<Unit>("audio.followRadioStation") { param("station_id", stationId) }

    suspend fun unfollowRadioStation(stationId: Int) =
        execute<Unit>("audio.unfollowRadioStation") { param("station_id", stationId) }

    / ** audio.searchArtists / searchMain. */
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
        execute<Unit>("audioBooks.addToFavorites") { param("audio_book_id", bookId) }

    suspend fun audioBookDeleteFromFavorites(bookId: Int) =
        execute<Unit>("audioBooks.deleteFromFavorites") { param("audio_book_id", bookId) }

    suspend fun audioBookGetById(bookId: Int) =
        execute<Any>("audioBooks.getAudioBookById") { param("audio_book_id", bookId) }

    suspend fun audioBookSetProgress(chapterId: String, timeFromStart: Int) =
        execute<Unit>("audioBooks.setProgress") {
            param("chapter_id", chapterId); param("time_from_start", timeFromStart)
        }

    // ======================= PODCASTS =======================

    suspend fun podcastSubscribe(ownerId: Long) =
        execute<Unit>("podcasts.subscribe") { param("owner_id", ownerId) }

    suspend fun podcastUnsubscribe(ownerId: Long) =
        execute<Unit>("podcasts.unsubscribe") { param("owner_id", ownerId) }

    // ======================= USERS / UTILS / STORAGE =======================

    suspend fun usersGet(userId: Long, fields: String = "photo_base,is_followed,can_follow") =
        execute<Any>("users.get") { param("fields", fields); param("user_id", userId) }

    suspend fun resolveScreenName(screenName: String) =
        execute<Any>("utils.resolveScreenName") { param("screen_name", screenName) }

    / ** storage.get/set — KV-хранилище VK (app_id = LMG VK). */
    suspend fun storageGet(appId: Int) =
        execute<Map<String, String>>("storage.get") { param("app_id", appId) }

    suspend fun storageSet(key: String, value: String, appId: Int) =
        execute<Unit>("storage.set") {
            param("key", key); param("value", value); param("app_id", appId)
        }

    // ======================= STATS / MISC =======================

    suspend fun statsTrackEvents(eventsJson: String) =
        execute<Unit>("stats.trackEvents") { param("events", eventsJson) }

    suspend fun musicStatCreatePlaylist(title: String) =
        execute<Any>("musicStatResults.createPlaylist") { param("title", title) }

    suspend fun musicStatGetMetrics() = execute<Any>("musicStatResults.getMetrics") {}

    suspend fun studioGetArtistYearRecap(artistId: String) =
        execute<Any>("studio.getArtistYearRecapData") { param("artist_id", artistId) }

    // ======================= AUTH FLOW =======================

    / ** auth.validateAccount — первый шаг логина. */
    suspend fun validateAccount(
        login: String,
        supportedWays: String = "sms,push,email,callreset",
        flowType: String = "password",
    ) = execute<Any>("auth.validateAccount") {
        param("login", login)
        param("force_password", false)
        param("passkey_supported", 1)
        param("supported_ways", supportedWays)
        param("flow_type", flowType)
        param("sak_version", "1")
    }

    / ** auth.processAuthCode(Multi) — подтверждение кода 2FA. */
    suspend fun processAuthCode(action: Int, authCode: String) =
        execute<Any>("auth.processAuthCode") { param("action", action); param("auth_code", authCode) }

    suspend fun processAuthCodeMulti(action: Int, authCode: String, accessTokens: String) =
        execute<Any>("auth.processAuthCodeMulti") {
            param("action", action); param("auth_code", authCode); param("access_tokens", accessTokens)
        }

    / ** ecosystem.* — OTP-флоу (новая авторизация VK ID). */
    suspend fun ecosystemCheckOtp(sid: String, code: String) =
        execute<Any>("ecosystem.checkOtp") { param("sid", sid); param("code", code) }

    suspend fun ecosystemSendOtp(kind: OtpKind, sid: String) =
        execute<Any>("ecosystem.sendOtp${kind.wireName}") {
            param("sid", sid); param("flow_type", ""); param("sak_version", "1")
        }

    enum class OtpKind(val wireName: String) { Sms("Sms"), Email("Email"), Push("Push"), CallReset("CallReset") }

    / ** get_anonym_token — анонимный токен (client credentials). */
    suspend fun getAnonymToken() = execute<Any>("get_anonym_token") {
        param("client_id", VkApiClient.VK_ANDROID_CLIENT_ID.toLong())
        param("client_secret", "hHbZxrka2uZ6jB1inYsH")
    }

    / ** OAuth token — прямой логин по логину/паролю (oauth-хост). */
    suspend fun oauthToken(
        username: String,
        password: String,
        sid: String? = null,
        code: String? = null,
        anonymousToken: String? = null,
        scope: String = "all",
    ) = execute<Any>("token") {
        useOAuth = true
        param("grant_type", "password")
        param("username", username); param("password", password)
        param("scope", scope)
        param("client_id", VkApiClient.VK_ANDROID_CLIENT_ID.toLong())
        param("client_secret", "hHbZxrka2uZ6jB1inYsH")
        param("libverify_support", true)
        param("device_trusted_hash_support", true)
        param("2fa_supported", true)
        param("supported_ways", "sms,push,email,callreset")
        sid?.let { param("sid", it) }
        code?.let { param("code", it) }
        anonymousToken?.let { param("anonymous_token", it) }
    }

    // ======================= engine =======================

    private suspend fun <T> execute(name: String, block: VkMethod<T>.() -> Unit): VkResult<T> {
        @Suppress("UNCHECKED_CAST")
        val method = VkMethod(name, GenericParser as VkResponseParser<T>).apply {
            isContentMethod = true
            block()
        }
        return client.execute(method)
    }

    / ** Универсальный парсер конверта (в оригинале — per-method синглтоны). */
    private object GenericParser : VkResponseParser<Any> {
        override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<Any> {
            TODO("Moshi per-method adapters из vkapi2/methods/ * — механическая генерация")
        }
    }
}
