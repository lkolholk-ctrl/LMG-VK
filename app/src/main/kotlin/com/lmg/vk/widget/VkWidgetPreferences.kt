package com.lmg.vk.widget

import android.content.Context

/**
 * Мелкие настройки виджета: выбранный тип подборки и заявка на принудительное
 * обновление.
 *
 * Отдельно от `AppSettings` намеренно. Во-первых, `AppSettings` — зона другого
 * кода и его трогать нельзя. Во-вторых, это состояние читается из
 * `BroadcastReceiver` виджета, где нужен синхронный доступ без ожидания
 * DataStore-флоу: если чтение настройки задержится, Glance успеет отрисовать
 * виджет со значением по умолчанию, и пользователь увидит, как подборка
 * «перескакивает» обратно.
 *
 * ОТСТУПЛЕНИЕ от VK X: там тип подборки выбирается в отдельной
 * configuration-activity (`SmallPlayerGlanceConfigurationActivity` в дампе).
 * Здесь тип переключается тапом по заголовку — activity ради одного бинарного
 * выбора выглядит тяжелее самого виджета.
 */
internal object VkWidgetPreferences {

    private const val PREFERENCES_NAME = "lmg_vk_widget_prefs_v1"
    private const val KEY_CONTENT_TYPE = "content_type"
    private const val KEY_FORCE_REFRESH = "force_refresh_pending"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /**
     * Тип подборки. По умолчанию `recomms` — как и в VK X: у свежего аккаунта
     * своей музыки может не быть вовсе, а рекомендации есть всегда.
     */
    fun contentType(context: Context): VkWidgetContentType = runCatching {
        VkWidgetContentType.fromApiValue(prefs(context).getString(KEY_CONTENT_TYPE, null))
    }.getOrDefault(VkWidgetContentType.RECOMMS)

    fun setContentType(context: Context, type: VkWidgetContentType) {
        runCatching {
            prefs(context).edit().putString(KEY_CONTENT_TYPE, type.apiValue).apply()
        }
    }

    /**
     * Заявка «в следующую отрисовку сходи в сеть, не смотри на TTL».
     *
     * Почему флаг, а не просто очистка кэша при нажатии «обновить»: очищенный
     * кэш нечем подстелить, если сеть тут же откажет, и пользователь по нажатию
     * кнопки обновления получил бы вместо своих подборок сообщение об ошибке.
     * С флагом старые элементы остаются доступны как fallback.
     */
    fun consumeForceRefresh(context: Context): Boolean = runCatching {
        val pending = prefs(context).getBoolean(KEY_FORCE_REFRESH, false)
        if (pending) prefs(context).edit().putBoolean(KEY_FORCE_REFRESH, false).apply()
        pending
    }.getOrDefault(false)

    fun requestForceRefresh(context: Context) {
        runCatching {
            prefs(context).edit().putBoolean(KEY_FORCE_REFRESH, true).apply()
        }
    }
}
