package com.lmg.vk.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.update

/**
 * Приёмник виджета — то, что регистрируется в манифесте.
 *
 * Glance сам обрабатывает весь протокол `AppWidgetProvider` (onUpdate,
 * onAppWidgetOptionsChanged и прочее), от нас нужен только экземпляр виджета.
 * Аналог `SmallPlaylistsGlanceWidgetReceiver` из VK X.
 */
class VkMusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VkMusicWidget()
}

/**
 * Тап по заголовку: переключить `recomms` <-> `mymusic`.
 *
 * Обновляем только этот экземпляр (`update(context, glanceId)`), а не все:
 * пользователь мог поставить два виджета, и переключение одного не должно
 * дёргать второй. Сам тип при этом общий — отдельного per-widget состояния
 * Glance без configuration-activity не даёт.
 */
class VkWidgetToggleTypeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val next = VkWidgetPreferences.contentType(context).next()
        VkWidgetPreferences.setContentType(context, next)
        // Новый тип — новый ключ кэша, поэтому запрос при следующей отрисовке
        // уйдёт сам; принудительное обновление здесь не нужно.
        runCatching { VkMusicWidget().update(context, glanceId) }
    }
}

/**
 * Тап по «Обновить»: сходить в VK, не дожидаясь истечения часового TTL.
 *
 * Флаг ставится в preferences, а не передаётся параметром: между нажатием и
 * отрисовкой Glance успевает пересоздать виджет, и параметр до `provideGlance`
 * не доезжает.
 */
class VkWidgetRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        VkWidgetPreferences.requestForceRefresh(context)
        runCatching { VkMusicWidget().update(context, glanceId) }
    }
}
