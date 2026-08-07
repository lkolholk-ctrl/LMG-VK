package com.lmg.vk.widget

import android.content.Context
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.network.VkApiLocator
import com.lmg.vk.network.VkResult
import com.lmg.vk.network.methods.VkAudioApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Загрузка элементов виджета с дисковым кэшем — порт `C16375e` из VK X.
 *
 * Ключевое требование оригинала: TTL один час. Причина не в экономии трафика,
 * а в том, как живут виджеты. Лончер перерисовывает виджет по своим поводам
 * (смена темы, разворот, возврат на домашний экран, пересоздание лончера), и
 * каждая такая перерисовка — новый вызов `provideGlance`. Без кэша это был бы
 * сетевой запрос на каждое движение пользователя по домашнему экрану.
 *
 * Кэш лежит в [android.content.SharedPreferences], а не в DataStore, которым
 * пользуется остальное приложение. Почему: код читается из
 * [android.appwidget.AppWidgetProvider], который может подняться в процессе без
 * прогретых синглтонов, и синхронное чтение prefs здесь надёжнее, чем ожидание
 * первой эмиссии DataStore-флоу. VK X по той же причине держит для виджета
 * отдельный «Widget DataStore» (`C12761e` в дампе).
 */
object VkWidgetRepository {

    /** VK X: TTL ровно 1 час (`C16375e.vip` — `Duration.hours(1)`). */
    private const val CACHE_TTL_MS = 60L * 60L * 1000L

    private const val PREFERENCES_NAME = "lmg_vk_widget_cache_v1"

    /**
     * Сетевые запросы виджета сериализуем: лончер умеет попросить обновить
     * сразу несколько экземпляров виджета, и без мьютекса они пошли бы в VK
     * параллельно за одними и теми же данными.
     */
    private val loadMutex = Mutex()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Запись кэша: сами элементы + момент загрузки для проверки TTL. */
    @Serializable
    private data class CacheEntry(
        val elements: List<VkWidgetElement>,
        val loadedAtMs: Long,
    )

    /**
     * Отдать состояние виджета: кэш, если он свежий, иначе сеть.
     *
     * Никогда не бросает — исключение здесь означало бы крэш в лончере, который
     * пользователь видит на домашнем экране до самой перезагрузки. Любая
     * проблема превращается в [VkWidgetState].
     */
    suspend fun load(
        context: Context,
        size: VkWidgetSize,
        contentType: VkWidgetContentType,
        forceRefresh: Boolean = false,
    ): VkWidgetState = runCatching {
        // Проверка сессии — ПЕРВОЙ, до чтения кэша. Иначе после выхода из
        // аккаунта виджет ещё час показывал бы подборки предыдущего
        // пользователя: кэш-то остался свежим. Данные чужого аккаунта на
        // домашнем экране — это утечка, а не просто неточность.
        val userId = currentUserId()
            ?: return@runCatching VkWidgetState.NotLoggedIn

        val cached = readCache(context, size, contentType, userId)

        // Свежий кэш — сразу отдаём, в сеть не идём вообще: это и есть смысл TTL.
        if (!forceRefresh && cached != null && isFresh(cached)) {
            return@runCatching cached.elements.toState(stale = false)
        }

        when (val fetched = fetch(size, contentType, userId)) {
            is VkResult.Success -> {
                val elements = fetched.data
                    .mapNotNull { it.toWidgetElement() }
                    .take(size.maxElements)

                if (elements.isEmpty()) {
                    // Пустой ответ не кэшируем: у нового аккаунта подборки
                    // появятся через несколько прослушиваний, и держать «пусто»
                    // час означало бы показывать пустой виджет и после того, как
                    // данные уже есть. При этом старый непустой кэш лучше пустоты.
                    cached?.elements?.takeIf { it.isNotEmpty() }
                        ?.let { return@runCatching it.toState(stale = true) }
                    VkWidgetState.Empty
                } else {
                    writeCache(context, size, contentType, userId, elements)
                    elements.toState(stale = false)
                }
            }

            is VkResult.Error -> {
                // Сеть/VK отказали. Просроченный кэш всё равно полезнее ошибки:
                // подборки меняются медленно, и час-два давности пользователю
                // не мешают. Но помечаем stale — врать про свежесть не будем.
                cached?.elements?.takeIf { it.isNotEmpty() }
                    ?.let { return@runCatching it.toState(stale = true) }
                VkWidgetState.Error(
                    fetched.message.ifBlank { "VK вернул ошибку ${fetched.code}" },
                )
            }
        }
    }.getOrElse { error ->
        // Сюда попадают падения ниже уровня VkResult: нет сети совсем,
        // не поднято сетевое ядро, битый кэш. Для виджета всё это — одна
        // ситуация «показать нечего, вот причина».
        currentUserId()
            ?.let { readCache(context, size, contentType, it) }
            ?.elements
            ?.takeIf { it.isNotEmpty() }
            ?.toState(stale = true)
            ?: VkWidgetState.Error(error.message?.takeIf { it.isNotBlank() } ?: "Нет соединения с VK")
    }

    /** Сбросить кэш подборок — вызывается при выходе из аккаунта. */
    fun clear(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    // ─────────────────────────────── сеть ───────────────────────────────

    private suspend fun fetch(
        size: VkWidgetSize,
        contentType: VkWidgetContentType,
        userId: Long,
    ): VkResult<List<com.lmg.vk.network.dto.gen.music.AudioWidgetItem>> = loadMutex.withLock {
        val client = VkApiLocator.apiClient()
        VkAudioApi(client).getWidgetElements(
            size = size.apiValue,
            type = contentType.apiValue,
            userId = userId,
        )
    }

    /**
     * user_id для метода. VK X подставляет id текущего аккаунта
     * (`C14027e.metrica()` в дампе) — берём его из того же места, что и
     * остальное приложение, чтобы не завести второй источник правды о сессии.
     */
    private fun currentUserId(): Long? = runCatching {
        MusicAuth.partnerUserId.value?.takeIf { it != 0L }
    }.getOrNull()

    // ─────────────────────────────── кэш ───────────────────────────────

    private fun isFresh(entry: CacheEntry): Boolean {
        val age = System.currentTimeMillis() - entry.loadedAtMs
        // Отрицательный возраст = пользователь перевёл часы назад. Считаем такой
        // кэш просроченным: иначе он «завис» бы свежим на произвольное время.
        return age in 0 until CACHE_TTL_MS
    }

    /**
     * Ключ включает размер, тип и id аккаунта.
     *
     * Размер и тип — потому что у medium и large разное число элементов, и
     * подмена одного другим показала бы в маленьком виджете обрезанный список от
     * большого. user_id — вторая линия защиты от показа подборок предыдущего
     * аккаунта после смены пользователя (первая — проверка сессии в [load]).
     */
    private fun cacheKey(
        size: VkWidgetSize,
        contentType: VkWidgetContentType,
        userId: Long,
    ): String = "elements_${userId}_${size.apiValue}_${contentType.apiValue}"

    private fun readCache(
        context: Context,
        size: VkWidgetSize,
        contentType: VkWidgetContentType,
        userId: Long,
    ): CacheEntry? = runCatching {
        val raw = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(cacheKey(size, contentType, userId), null)
            ?: return null
        json.decodeFromString<CacheEntry>(raw)
    }.getOrNull()

    private fun writeCache(
        context: Context,
        size: VkWidgetSize,
        contentType: VkWidgetContentType,
        userId: Long,
        elements: List<VkWidgetElement>,
    ) {
        runCatching {
            val payload = json.encodeToString(
                CacheEntry(elements = elements, loadedAtMs = System.currentTimeMillis()),
            )
            context.applicationContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(cacheKey(size, contentType, userId), payload)
                .apply()
        }
    }

    private fun List<VkWidgetElement>.toState(stale: Boolean): VkWidgetState =
        if (isEmpty()) VkWidgetState.Empty else VkWidgetState.Content(this, stale)
}
