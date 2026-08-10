package com.lmg.vk.engine

import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.methods.VkAudioApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Трансляция играющего трека в статус ВКонтакте (`audio.setBroadcast`).
 *
 * Порт из VK X (деобфусцированный `C14680e` — «ad» ставит статус, «vip» снимает;
 * триггеры — `PlaybackService` на смене трека и `C14564e.return` на переключении
 * тумблера `broadcast_to_profile`).
 *
 * Почему отдельный объект, а не пара вызовов внутри плеера: статус — это ПОБОЧНЫЙ
 * эффект воспроизведения, у него своя приватность, свой тумблер и своя обработка
 * ошибок. Плеер не должен знать про профиль пользователя, поэтому здесь ровно одна
 * точка входа — [ensureStarted], а всё остальное менеджер выводит сам из
 * [PlayerController.currentTrack] и [AppSettings.broadcastToStatus].
 *
 * ПРИВАТНОСТЬ: включённая трансляция меняет статус в профиле — это видят друзья
 * пользователя. Поэтому тумблер по умолчанию выключен, и без него здесь не
 * уходит НИ ОДНОГО запроса.
 */
object VkBroadcastManager {

    /** Своя scope: статус живёт дольше любого экрана и не должен падать с UI. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val started = AtomicBoolean(false)

    /**
     * Сериализация запросов: смена трека может прилететь из нескольких мостов
     * подряд (см. идемпотентность в PlayerController.onTrackChanged), а порядок
     * «поставил новый / снял старый» на сервере важен — иначе в профиле может
     * залипнуть трек, который уже не играет.
     */
    private val mutex = Mutex()

    /**
     * Что реально лежит в статусе на сервере (полный VK id или null = снято).
     * Нужен, чтобы не дёргать API повторно одним и тем же значением: в VK X ту же
     * роль играл `lastWidgetTrackId`-подобный дедуп на уровне сервиса.
     */
    private var broadcastedId: String? = null

    private val api: VkAudioApi by lazy { VkAudioApi(VkApiLocator.apiClient()) }

    /**
     * Подписаться на плеер и тумблер. Идемпотентно — можно звать из любого места
     * на любой смене трека; реальная подписка поднимется один раз.
     */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // combine, а не два отдельных сборщика: нужно реагировать И на смену
            // трека, И на переключение тумблера — включили при играющем треке —
            // статус ставится сразу, выключили — сразу снимается (в VK X это два
            // разных вызова, C14564e.return; здесь одна реакция на пару значений).
            combine(
                PlayerController.currentTrack,
                AppSettings.broadcastToStatus,
            ) { track, enabled ->
                if (enabled) broadcastableId(track) else null
            }
                .distinctUntilChanged()
                .collect { targetId -> push(targetId) }
        }
    }

    /**
     * Полный VK id трека, который допустимо показать в статусе, иначе null.
     *
     * VK X транслирует только настоящие аудиозаписи VK: подкасты (`AudioTrack`
     * с PodcastInfo, `appmetrica() == 2`) там СНИМАЮТ статус вместо установки.
     * ОТСТУПЛЕНИЕ ОТ VK X: в модели [Track] признака подкаста нет, поэтому
     * подкасты здесь отфильтровать нечем — фильтруем по тому, что есть: трек
     * должен быть онлайновым VK-треком с валидным
     * `owner_id_audio_id[_access_key]`.
     * Локальные файлы и видеоклипы (`clip_<id>`) id-проверку не проходят.
     */
    private fun broadcastableId(track: Track?): String? {
        val id = track?.id ?: return null
        // access_key нужен только для получения потока. В статус отправляется
        // стабильный двухсегментный id, без раскрытия ключа доступа.
        return VkAudioIdentity.bareFullId(id)
    }

    /**
     * Один запрос к VK. `audioFullId == null` → `enabled=false`, статус снимается
     * (в VK X это `C14680e.vip()`).
     *
     * Ошибка НЕ пробрасывается наружу и никак не влияет на плеер: музыка важнее
     * статуса. Нет сети, истёк токен, VK ответил ошибкой приватности — молча
     * пишем в лог и живём дальше. VK X здесь делает то же самое: вызов обёрнут в
     * `try { ... } finally { return; }`, то есть исключение проглатывается.
     */
    private suspend fun push(audioFullId: String?) {
        // Незалогиненному ставить статус некуда. В VK X тот же гейт —
        // `C14027e.ad()` (user_id != 0) перед созданием запроса.
        val userId = MusicAuth.profileId.value ?: return
        if (userId == 0L) return
        mutex.withLock {
            if (broadcastedId == audioFullId) return@withLock
            try {
                when (val result = api.setBroadcast(audioFullId, userId)) {
                    is VkResult.Success -> {
                        broadcastedId = audioFullId
                        DebugLog.add(
                            if (audioFullId == null) "broadcast: статус снят"
                            else "broadcast: статус = $audioFullId"
                        )
                    }
                    is VkResult.Error -> {
                        // Локальную отметку НЕ двигаем: на следующей смене трека
                        // (или при повторном включении тумблера) попытка повторится.
                        DebugLog.add(
                            "broadcast: VK отказал (${result.code}) ${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                // Сюда попадают и «сервис-локатор ещё не поднят», и сетевые сбои.
                DebugLog.add("broadcast: ошибка — ${e.message}")
            }
        }
    }
}
