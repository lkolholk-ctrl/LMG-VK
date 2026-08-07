package com.lmg.vk.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

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
 * Обновляем все экземпляры: тип контента в Glance один на приложение (отдельного
 * per-widget состояния без configuration-activity он не даёт), поэтому и
 * показывать разное в двух виджетах всё равно нечем — второй обязан
 * перерисоваться вместе с первым, иначе покажет чужой тип.
 *
 * `updateAll`, а не `update(context, glanceId)`: extension-функции `update` для
 * `GlanceAppWidget` в Glance 1.1.1 нет — на ней и упала сборка.
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
        runCatching { VkMusicWidget().updateAll(context) }
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
        runCatching { VkMusicWidget().updateAll(context) }
    }
}
