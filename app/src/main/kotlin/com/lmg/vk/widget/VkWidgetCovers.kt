package com.lmg.vk.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Обложки для виджета.
 *
 * Виджет рисуется через `RemoteViews`, то есть каждая картинка уезжает в процесс
 * лончера как Bitmap внутри биндер-транзакции. Отсюда два жёстких требования,
 * которых нет у обычного Compose-экрана:
 *
 * 1. Никаких hardware-битмапов — они не парселятся, и попытка отдать такой
 *    битмап в RemoteViews валит лончер. Поэтому `allowHardware(false)`.
 * 2. Размер имеет значение. Лимит транзакции биндера порядка 1 МБ на всё
 *    обновление виджета сразу, а не на картинку. Шесть обложек по 1200px
 *    (~5.7 МБ в ARGB_8888 каждая) гарантированно дают TransactionTooLargeException,
 *    и виджет просто не отрисуется. Поэтому просим Coil сразу декодировать
 *    маленькое изображение.
 */
internal object VkWidgetCovers {

    /**
     * Сторона обложки в пикселях. 144px ≈ 48dp на xxhdpi — это фактический
     * размер картинки в вёрстке виджета. В ARGB_8888 это ~83 КБ на элемент,
     * то есть шесть штук укладываются в полмегабайта вместе с остальным
     * содержимым транзакции.
     */
    private const val COVER_SIZE_PX = 144

    /**
     * Потолок ожидания всех обложек. Виджет обязан отрисоваться даже на плохой
     * сети: лучше показать настоящие названия подборок без картинок, чем держать
     * пользователя на служебном экране загрузки Glance. Без картинок вёрстка
     * предусматривает заглушку-плейсхолдер.
     */
    private const val TOTAL_TIMEOUT_MS = 4_000L

    /**
     * Загрузить обложки для списка элементов.
     *
     * Возвращает map «URL -> Bitmap»; отсутствующий ключ означает «картинку взять
     * не удалось» и это нормальный, ожидаемый исход, а не ошибка виджета.
     * Ключ — именно URL, а не индекс: у элементов подборки обложки повторяются,
     * и по URL они склеиваются в одну загрузку и один битмап в транзакции.
     */
    suspend fun load(
        context: Context,
        elements: List<VkWidgetElement>,
    ): Map<String, Bitmap> {
        val urls = elements.map { it.coverUrl }.filter { it.isNotBlank() }.distinct()
        if (urls.isEmpty()) return emptyMap()

        // Coil берётся из приложения (LmgApplication — ImageLoaderFactory), так
        // что виджет попадает в тот же дисковый кэш обложек, что и экраны.
        // Второй ImageLoader завёл бы вторую копию кэша на диске.
        return withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            coroutineScope {
                urls.map { url -> async { url to loadOne(context, url) } }
                    .mapNotNull { deferred ->
                        val (url, bitmap) = deferred.await()
                        bitmap?.let { url to it }
                    }
                    .toMap()
            }
        } ?: emptyMap()
    }

    private suspend fun loadOne(context: Context, url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(COVER_SIZE_PX, COVER_SIZE_PX)
                    // Обязательно: hardware bitmap нельзя передать в RemoteViews.
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                (result.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
        }
}
