package com.lmg.vk.audio

import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.dto.VkErrorCodes
import com.lmg.vk.network.methods.VkAudioApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Скрытый механизм `audio_rip` из VK MP3 MOD — ФОЛБЭК получения ссылки на трек,
 * когда обычный путь (`audio.getById → url`) отказал по доступу/ограничениям.
 *
 * ## Что именно происходит с аккаунтом пользователя
 *
 * Это не read-only запрос. Механизм ПУБЛИКУЕТ КОММЕНТАРИЙ ОТ ИМЕНИ ПОЛЬЗОВАТЕЛЯ
 * в тему [TOPIC_ID] сообщества [GROUP_ID], вложением к которому идёт нужный трек.
 * Трюк в том, что VK, отдавая вложение в ответе `board.getComments`, кладёт в него
 * прямой mp3-URL — уже без ограничений на прослушивание, наложенных на сам трек.
 *
 * Побочные эффекты для аккаунта, которые надо знать:
 *  - в теме сообщества остаётся публичный комментарий пользователя с этим треком;
 *  - подписка на тему, которую VK создаёт автоматически при комментировании,
 *    снимается через `newsfeed.unsubscribe` — иначе пользователь начал бы получать
 *    уведомления обо всей чужой активности в этой служебной теме.
 *
 * ## Комментарий НАМЕРЕННО НЕ УДАЛЯЕТСЯ
 *
 * Это не забытая уборка и не баг — НЕ НАДО «исправлять» это добавлением
 * `board.deleteComment`. Оригинальный скрипт мода тоже не удаляет комментарий,
 * и в этом смысл конструкции: служебная тема работает как ОБЩИЙ ПУБЛИЧНЫЙ КЭШ
 * вложений. Если трек уже опубликован кем-то раньше, работа фактически сделана,
 * а удаление выкидывало бы из общего кэша запись, которая ещё пригодится другим
 * пользователям мода. Поэтому здесь ровно та же последовательность, что в
 * оригинале: `createComment` → `getComments` → `unsubscribe`.
 *
 * Возможное улучшение (СОЗНАТЕЛЬНО НЕ РЕАЛИЗОВАНО): переиспользовать чужой уже
 * существующий комментарий с тем же треком вместо публикации своего. В оригинале
 * такого поиска нет, и не зря — тема накопила десятки тысяч комментариев, их
 * перебор через `board.getComments` дороже, чем повторная публикация.
 *
 * ## Правила вызова
 *
 * Из-за записи от имени пользователя путь допустим ТОЛЬКО как фолбэк:
 *  - никогда «на всякий случай» и никогда параллельно основному пути;
 *  - только после отказа основного API и только если [shouldFallback] вернул true.
 *
 * Источник: `com/vkmp3mod/android/api/audio/AudioGetLink.java`, ветка prefs-флага
 * `audio_rip`; `execute`-код расшифрован из 3DES (ключ — `AudioPlayerService`),
 * значения группы/темы взяты из него же, а не подобраны.
 */
object AudioRipFallback {

    private const val TAG = "AudioRip"

    /**
     * Служебное сообщество и тема из расшифрованного кода мода (`var g=…,t=…`).
     * Захардкожены осознанно: это конкретная тема конкретной группы, на которой
     * держится весь трюк, — параметризовать её нечем.
     */
    private const val GROUP_ID = 154571586L
    private const val TOPIC_ID = 46369607L

    /** «Доступ к методу ограничен для приложения» — основной триггер в моде. */
    private const val APP_ACCESS_RESTRICTED = 25

    /** «Flood control»: VK отдаёт его в том числе при срезании доступа к музыке. */
    private const val FLOOD_CONTROL = 9

    /**
     * Фолбэк пишет в аккаунт, поэтому одновременные вызовы недопустимы: два
     * параллельных резолва оставили бы два комментария вместо одного. Mutex
     * сериализует их, а не отбрасывает — второму треку ссылка тоже нужна.
     */
    private val ripMutex = Mutex()

    /**
     * Коды ошибок VK, при которых обычный API отказал ИМЕННО по доступу или
     * ограничению, — то есть случаи, где обход осмыслен.
     *
     * Откуда взяты (не догадки):
     *  - 25, 9 — ровно эта пара включает `audio_rip` в моде
     *    (`AudioListFragment`, строки ~1500 и ~1930: `if (var1.code != 25 && var1.code != 9)`
     *    … иначе `prefs.putBoolean("audio_rip", true)`). 25 — «доступ к методу
     *    ограничен для приложения», 9 — «слишком много однотипных действий»
     *    (в контексте музыки VK отдаёт его при срезании доступа к аудио).
     *  - 15/30/201/203 — уже объявлены в проекте как [VkErrorCodes.CLOSED_CONTENT]
     *    (закрытая музыка/приватный профиль/закрытое сообщество). Мод обрабатывает
     *    201 отдельной веткой «hidden_audio», но для нас это тот же класс отказа:
     *    трек есть, а доступа к ссылке нет.
     *
     * Сознательно НЕ включены: 0 (сетевой сбой/исключение — код 0 ставит
     * `VkApiClient` в catch), HTTP-коды, 14 (капча), 17 (валидация) и 1117
     * (протухший токен) — последние три чинятся ретраем в самом `VkApiClient`,
     * и подменять их обходом значило бы скрывать настоящую причину.
     */
    private val ACCESS_ERROR_CODES: Set<Int> = buildSet {
        add(APP_ACCESS_RESTRICTED)
        add(FLOOD_CONTROL)
        addAll(VkErrorCodes.CLOSED_CONTENT)
    }

    /**
     * Стоит ли пробовать обход при такой ошибке основного API.
     *
     * Отличает «нет доступа/ограничение» от «нет сети»: сетевой сбой обходом не
     * лечится, а лишний комментарий в аккаунте оставит. Поэтому решение по коду,
     * и лишь при его отсутствии — по тексту.
     *
     * @param errorCode код VK (`VkResult.Error.code`); `null`, если кода нет.
     * @param errorMessage текст ошибки — используется только когда кода нет.
     */
    fun shouldFallback(errorCode: Int?, errorMessage: String?): Boolean {
        // Код 0 в этом проекте означает пойманное исключение (см. VkApiClient.execute):
        // таймаут, DNS, обрыв TLS. Обход тут бесполезен.
        if (errorCode != null && errorCode != 0) {
            return errorCode in ACCESS_ERROR_CODES
        }

        // Кода нет (или он 0) — путь резолва мог принести только строку. Ловим
        // формулировки, которые однозначно про доступ/ограничение, и НЕ трогаем
        // сетевые: "timeout", "unable to resolve host", "connection reset".
        val message = errorMessage?.lowercase() ?: return false
        val accessMarkers = listOf(
            // Плейсхолдер VK вместо ссылки — трек отдан, но слушать не даёт.
            "audio_api_unavailable",
            "content_restricted",
            "source_not_allowed",
            "access denied",
            "access to audio",
            "restricted",
            "403",
        )
        return accessMarkers.any { message.contains(it) }
    }

    /**
     * Ссылка на поток в обход ограничений либо `null`, если обход не удался.
     *
     * Ничего не выдумывает: при любом отказе возвращает `null`, а причину пишет в
     * лог — выше должна остаться честная ошибка основного пути.
     *
     * @param fullId `owner_id_audio_id` (допустим префикс `vk_`), опционально с
     *   `_access_key` третьим сегментом — формат `AudioFile.asIdWithKey()` из мода.
     */
    suspend fun resolveUrl(fullId: String): String? {
        val idWithKey = normalizeIdWithKey(fullId)
        if (idWithKey == null) {
            android.util.Log.w(TAG, "resolveUrl: некорректный id трека, обход не выполняется: $fullId")
            return null
        }

        val client = runCatching { VkApiLocator.apiClient() }.getOrNull()
        if (client == null) {
            // Сеть ещё не поднята (LmgApplication не инициализировал ядро).
            // Свой HttpClient здесь создавать нельзя — уйдёт мимо обхода блокировок.
            android.util.Log.w(TAG, "resolveUrl: VkApiClient недоступен, обход невозможен")
            return null
        }

        return ripMutex.withLock {
            android.util.Log.i(
                TAG,
                "resolveUrl: обычный API отказал, публикуем трек $idWithKey " +
                    "комментарием в тему $GROUP_ID/$TOPIC_ID от имени пользователя",
            )
            try {
                when (val result = VkAudioApi(client).getLinkViaBoardComment(
                    audioIdWithKey = idWithKey,
                    groupId = GROUP_ID,
                    topicId = TOPIC_ID,
                )) {
                    is VkResult.Success -> {
                        val url = result.data.trim()
                        // Обход, вернувший тот же плейсхолдер, — это не успех.
                        if (url.isEmpty() || url.contains("audio_api_unavailable", ignoreCase = true)) {
                            android.util.Log.w(TAG, "resolveUrl: обход вернул пустую ссылку/плейсхолдер")
                            null
                        } else {
                            android.util.Log.i(TAG, "resolveUrl: ссылка получена в обход ограничений")
                            url
                        }
                    }

                    is VkResult.Error -> {
                        // Явно, а не «проглотив»: если сюда дошло, у пользователя
                        // мог остаться комментарий, но ссылку получить не удалось.
                        android.util.Log.e(
                            TAG,
                            "resolveUrl: обход не удался (code=${result.code}, msg=${result.message}). " +
                                "Комментарий мог остаться опубликованным — это ожидаемо, " +
                                "тема служит общим кэшем вложений и не чистится.",
                        )
                        null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "resolveUrl: исключение при обходе: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Приводит id к формату `owner_id_audio_id[_access_key]` и валидирует его.
     *
     * Валидация здесь обязательна и не косметическая: значение подставляется
     * ВНУТРЬ текста `execute`-скрипта, поэтому любой символ вне цифр/подчёркивания
     * (кавычка, скобка, `\u0000`) стал бы инъекцией в код VK. Возвращает `null`,
     * если формат не подошёл.
     */
    private fun normalizeIdWithKey(fullId: String): String? {
        val normalized = fullId.removePrefix("vk_").trim()
        val parts = normalized.split('_')
        if (parts.size !in 2..3) return null

        // owner_id может быть отрицательным (аудио сообщества), audio_id — нет.
        val ownerId = parts[0].toLongOrNull() ?: return null
        val audioId = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val accessKey = parts.getOrNull(2)

        // access_key у VK — hex-подобная строка; допускаем только буквы и цифры.
        if (accessKey != null && (accessKey.isEmpty() || !accessKey.all(Char::isLetterOrDigit))) {
            return null
        }

        val base = "${ownerId}_$audioId"
        return if (accessKey != null) "${base}_$accessKey" else base
    }
}
