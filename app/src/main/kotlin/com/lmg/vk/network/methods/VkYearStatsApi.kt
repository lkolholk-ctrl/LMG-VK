package com.lmg.vk.network.methods

import com.lmg.vk.network.MoshiEnvelopeParser
import com.lmg.vk.network.VkApiClient
import com.lmg.vk.network.VkJson
import com.lmg.vk.network.VkMethod
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.music.AudioGetAnnualResultBlocksDto
import com.lmg.vk.network.dto.music.Y25JsonStorageValue
import com.lmg.vk.network.dto.music.Y25PlaylistCreateAction
import com.lmg.vk.network.dto.music.Y25Response
import com.lmg.vk.network.dto.music.Y25_STATUS_PENDING
import kotlinx.coroutines.delay

/**
 * Типизированные вызовы «Итогов года» ВКонтакте.
 * Спека: `docs/vkx-port/04-periphery.md` §4/§5/§6.
 *
 * Почему отдельный класс, а не правки в [VkMethodsRegistry]: там эти три метода
 * заведены как `execute<Any>` и (для двух из них) без обязательного
 * `access_token`, то есть в рабочем виде их всё равно нужно писать заново.
 * Отдельный файл к тому же не конфликтует с параллельными правками реестра.
 *
 * Ключевое отличие от остальных методов приложения: `musicStatResults.*` ходят
 * НЕ под основным токеном, а под токеном мини-приложения (см.
 * [VkMiniAppTokenProvider]).
 */
class VkYearStatsApi(
    private val client: VkApiClient,
    private val miniAppTokens: VkMiniAppTokenProvider = VkMiniAppTokenProvider(client),
) {
    private val registry = VkMethodsRegistry(client)

    /**
     * §4. Итоги года по артисту. Единственный параметр — `artist_id`; токен здесь
     * обычный, основной (`FRESH C4271e.java:812-822`).
     */
    suspend fun getArtistYearRecap(artistId: String): VkResult<AudioGetAnnualResultBlocksDto> {
        val method = VkMethod(
            "studio.getArtistYearRecapData",
            MoshiEnvelopeParser<AudioGetAnnualResultBlocksDto>(
                AudioGetAnnualResultBlocksDto::class.java,
            ),
        ).apply { param("artist_id", artistId) }
        return client.execute(method)
    }

    /**
     * §5. Музыкальные метрики пользователя. Требует токен мини-приложения; если
     * его получить не удалось, возвращается [VkResult.Error] с кодом
     * [ERROR_NO_MINI_APP_TOKEN] — вызывающий показывает честный текст, а не
     * пустой экран.
     */
    suspend fun getMetrics(): VkResult<Y25Response> {
        val token = miniAppTokens.yearStatsToken()
            ?: return VkResult.Error(ERROR_NO_MINI_APP_TOKEN, MSG_NO_MINI_APP_TOKEN)

        val method = VkMethod(
            "musicStatResults.getMetrics",
            MoshiEnvelopeParser<Y25Response>(Y25Response::class.java),
        ).apply { param("access_token", token) }
        return client.execute(method)
    }

    /**
     * §6. Создание плейлиста по результатам метрик.
     *
     * Повторяет поведение оригинала (`FRESH C4673e.java:226-256`): пока VK
     * отвечает `status == "pending"`, клиент опрашивает метод заново, до 5 попыток
     * всего. Дефолт заголовка `"My 2025"` — тоже из оригинала, он применяется
     * вызывающим, когда у блока нет своего `playlist.title`.
     *
     * ОТСТУПЛЕНИЕ: между попытками добавлена пауза [POLL_DELAY_MS]. В
     * декомпиляте задержку разобрать не удалось, а без неё пять запросов уходят
     * мгновенно и смысл опроса теряется.
     *
     * После успеха id плейлиста пишется в `storage` тем же ключом, что у VK X —
     * чтобы повторный вход не создавал второй такой же плейлист.
     */
    suspend fun createPlaylist(title: String): VkResult<Y25PlaylistCreateAction> {
        val token = miniAppTokens.yearStatsToken()
            ?: return VkResult.Error(ERROR_NO_MINI_APP_TOKEN, MSG_NO_MINI_APP_TOKEN)

        var last: VkResult<Y25PlaylistCreateAction> =
            VkResult.Error(ERROR_STILL_PENDING, MSG_STILL_PENDING)

        repeat(MAX_CREATE_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(POLL_DELAY_MS)

            val method = VkMethod(
                "musicStatResults.createPlaylist",
                MoshiEnvelopeParser<Y25PlaylistCreateAction>(Y25PlaylistCreateAction::class.java),
            ).apply {
                param("title", title)
                param("access_token", token)
            }

            last = client.execute(method)
            val data = (last as? VkResult.Success)?.data ?: return@repeat

            // Готово, только когда статус перестал быть "pending" и пришёл id.
            if (data.status != Y25_STATUS_PENDING && data.id != 0) {
                rememberCreatedPlaylist(data.id)
                return last
            }
        }

        // Пять попыток прошло, а плейлист всё ещё готовится — это не успех.
        val data = (last as? VkResult.Success)?.data
        if (data != null && (data.status == Y25_STATUS_PENDING || data.id == 0)) {
            return VkResult.Error(ERROR_STILL_PENDING, MSG_STILL_PENDING)
        }
        return last
    }

    /**
     * Id плейлиста, созданного ранее (тот же ключ `storage`, что у VK X, §2.2).
     * `null` — записи нет либо её не удалось прочитать.
     */
    suspend fun createdPlaylistId(): Int? {
        val stored = registry.storageGet(listOf(CREATED_PLAYLIST_KEY), VKX_STORAGE_APP_ID)
        val value = (stored as? VkResult.Success)?.data
            ?.get(CREATED_PLAYLIST_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching {
            VkJson.moshi.adapter(Y25JsonStorageValue::class.java).fromJson(value)?.id
        }.getOrNull()?.takeIf { it != 0 }
    }

    private suspend fun rememberCreatedPlaylist(id: Int) {
        val payload = runCatching {
            VkJson.moshi.adapter(Y25JsonStorageValue::class.java).toJson(Y25JsonStorageValue(id))
        }.getOrNull() ?: return
        // Результат намеренно игнорируется: плейлист уже создан, и неудачная
        // запись памятки не должна выглядеть как провал всей операции.
        registry.storageSet(CREATED_PLAYLIST_KEY, payload, VKX_STORAGE_APP_ID)
    }

    companion object {
        /** Токен мини-приложения получить не удалось (нет прав/сети/редиректа). */
        const val ERROR_NO_MINI_APP_TOKEN = -8001
        const val MSG_NO_MINI_APP_TOKEN =
            "ВКонтакте не выдал доступ к «Итогам года». Попробуйте позже."

        /** VK всё ещё готовит плейлист (`status = pending`). */
        const val ERROR_STILL_PENDING = -8002
        const val MSG_STILL_PENDING =
            "ВКонтакте ещё готовит плейлист. Попробуйте через минуту."

        /** Дефолтное имя плейлиста из оригинала (`FRESH C4673e.java:248`). */
        const val DEFAULT_PLAYLIST_TITLE = "My 2025"

        private const val MAX_CREATE_ATTEMPTS = 5
        private const val POLL_DELAY_MS = 1_500L

        private const val CREATED_PLAYLIST_KEY = "annual_result_2025_created_playlists_id"

        /** app_id хранилища VK X (`FRESH C4673e.java:220`). */
        private const val VKX_STORAGE_APP_ID = 52384530
    }
}
