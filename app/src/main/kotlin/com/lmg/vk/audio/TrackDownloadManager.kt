package com.lmg.vk.audio

import android.content.Context
import com.lmg.vk.engine.AudioDownloadManager
import com.lmg.vk.engine.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Состояние загрузки одного трека. Ключ в [TrackDownloadManager.states] — `Track.id`.
 *
 * `Done` остаётся в состоянии и после того, как файл удалили, поэтому «скачан ли
 * трек» UI обязан спрашивать у реестра, а не у этого значения.
 */
sealed interface TrackDownloadState {
    data class Running(val percent: Int, val bytes: Long, val totalBytes: Long) : TrackDownloadState
    data class Done(val fileUri: String) : TrackDownloadState
    data class Failed(val message: String) : TrackDownloadState
}

/**
 * Фасад скачивания для UI — надстройка над [AudioDownloadManager].
 *
 * ПОЧЕМУ НАДСТРОЙКА, А НЕ СВОЙ ДВИЖОК. Скачивание в проекте уже работало:
 * [AudioDownloadManager] тянет файл, `PublicDownloads` кладёт его через
 * MediaStore в `Download/LMG-VK`, таблица `downloaded_tracks` хранит
 * реестр, в библиотеке есть готовый раздел «Downloaded», плюс отдельно
 * скачивание плейлистов и клипов. Второй параллельный движок означал бы два
 * реестра, две папки и два несогласованных списка скачанного — а ID3-теги и
 * обложки, ради которых всё затевалось, встраиваются в существующий путь одной
 * вставкой (`AudioDownloadManager.writeTagsForDownload`).
 *
 * Здесь только то, чего у старого менеджера не было: единый `StateFlow` с
 * процентами, отменой и текстом ошибки, удобный для Compose. Источник правды по
 * прогрессу один — [AudioDownloadManager.downloadProgress].
 */
object TrackDownloadManager {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _states = MutableStateFlow<Map<String, TrackDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, TrackDownloadState>> = _states.asStateFlow()

    /** Отменённые пользователем: их завершение игнорируем. */
    private val cancelled = mutableSetOf<String>()

    private var started = false

    /** Подписка на прогресс старого менеджера. Идемпотентно. */
    fun init(context: Context) {
        if (started) return
        started = true
        scope.launch {
            AudioDownloadManager.downloadProgress.collect { progress ->
                _states.value = buildMap {
                    // Финальные состояния сохраняем: старый менеджер убирает трек
                    // из прогресса по завершении, и без этого «Скачано» мигнуло бы
                    // и исчезло.
                    putAll(_states.value.filterValues { it !is TrackDownloadState.Running })
                    progress.forEach { (trackId, fraction) ->
                        put(
                            trackId,
                            TrackDownloadState.Running(
                                percent = (fraction.coerceIn(0f, 1f) * 100).toInt(),
                                // Старый менеджер отдаёт долю, а не байты. Ноль
                                // здесь означает «размер неизвестен» — UI по
                                // контракту показывает байты только когда
                                // totalBytes > 0, поэтому выдуманных чисел не будет.
                                bytes = 0L,
                                totalBytes = 0L,
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Поставить трек в очередь. Видеоклипы уходят своим путём старого менеджера:
     * у них контейнер mp4, и ID3 туда не пишется.
     */
    fun enqueue(context: Context, track: Track) {
        init(context)
        cancelled.remove(track.id)
        _states.value = _states.value + (track.id to TrackDownloadState.Running(0, 0L, 0L))
        AudioDownloadManager.downloadTrack(context, track) { ok ->
            if (cancelled.remove(track.id)) {
                _states.value = _states.value - track.id
                return@downloadTrack
            }
            _states.value = _states.value + (
                track.id to if (ok) {
                    // Путь файла хранит реестр, оттуда его и берёт экран загрузок;
                    // дублировать здесь нечего.
                    TrackDownloadState.Done(fileUri = "")
                } else {
                    TrackDownloadState.Failed("Не удалось скачать трек")
                }
                )
        }
    }

    /**
     * Отмена. Прервать саму закачку старый менеджер не умеет, поэтому помечаем
     * трек отменённым и убираем из состояния. Файл, если он всё же дойдёт,
     * появится в «Загрузках» и удаляется оттуда — это честнее, чем делать вид,
     * что поток оборван.
     */
    fun cancel(trackId: String) {
        cancelled.add(trackId)
        _states.value = _states.value - trackId
    }
}
