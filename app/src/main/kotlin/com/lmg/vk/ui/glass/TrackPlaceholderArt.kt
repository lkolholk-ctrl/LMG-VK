package com.lmg.vk.ui.glass

import android.content.Context
import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import com.lmg.vk.R

/**
 * Кастомные обложки для треков, у которых обложки нет.
 *
 * ЗАЧЕМ ОТДЕЛЬНЫЙ ФАЙЛ. Раньше выбор заглушки жил ВНУТРИ отрисовки
 * (`PlaceholderArt` брал `painterResource` по хешу), а `Track.coverUrl` при этом
 * оставался пустым. Из-за этого заглушка существовала только на экране:
 *
 *  - в уведомлении и на экране блокировки обложки не было вовсе
 *    (`MediaMetadata.artworkUri` получал `content://…/albumart/-1`, которого
 *    не существует);
 *  - экстрактор цветов получал `null` и отдавал серый фолбэк, поэтому фон
 *    плеера у таких треков не окрашивался;
 *  - поиск и прочие списки, которые строят свои модели, заглушку не видели.
 *
 * Теперь выбор — чистая функция от ключа трека, и её результат можно получить
 * в трёх видах: ресурс (для Compose), `android.resource://`-URI (для
 * MediaSession, media3 умеет его читать) и битмап (для палитры).
 *
 * ВАЖНО: выбор ДЕТЕРМИНИРОВАН по ключу. Один трек обязан получать одну и ту же
 * картинку в списке, в мини-плеере, в очереди и в уведомлении — иначе обложка
 * «мигает» при переходе между экранами.
 */
object TrackPlaceholderArt {

    /**
     * Legacy-заглушка, которая встречается в старых ответах/кэшах VK, когда
     * настоящей обложки у трека нет. Новый `AudioTrack.coverUrl()` отсекает её
     * ещё на DTO-уровне, но UI сохраняет проверку для старых сохранённых данных.
     *
     * ЭТО КОРЕНЬ ДВУХ БАГОВ. `Track.coverUrl` у трека без обложки НЕ пустой —
     * в нём лежит этот URL. Поэтому проверки вида `coverUrl != null` считали,
     * что обложка есть:
     *  - MediaSession ставил artworkUri на эту серую картинку VK вместо нашей;
     *  - экстрактор цветов честно качал её и получал серую палитру.
     * На экранах всё выглядело правильно только потому, что `AlbumArtImage`
     * знал про заглушку и отдельно её отсекал.
     *
     * Проверять этим методом ОБЯЗАНЫ все, кто решает «есть ли обложка».
     */
    private const val VK_PLACEHOLDER_URL = "https://vk.com/images/audio_row_placeholder.png"

    /** true — обложки на самом деле нет, это заглушка VK. */
    fun isVkPlaceholder(url: String?): Boolean =
        url != null && url.startsWith(VK_PLACEHOLDER_URL)

    /** Настоящая обложка: непустая и не заглушка VK. Иначе null. */
    fun realCoverOrNull(url: String?): String? =
        url?.takeIf { it.isNotBlank() && !isVkPlaceholder(it) }

    /**
     * Набор готовых обложек VK. Порядок менять НЕЛЬЗЯ: индекс считается от
     * хеша, и перестановка сменила бы картинки у всех треков разом.
     */
    val resources = intArrayOf(
        R.drawable.default_track_cover_01,
        R.drawable.default_track_cover_02,
        R.drawable.default_track_cover_03,
        R.drawable.default_track_cover_04,
        R.drawable.default_track_cover_05,
        R.drawable.default_track_cover_06,
        R.drawable.default_track_cover_07,
        R.drawable.default_track_cover_08,
        R.drawable.default_track_cover_09,
        R.drawable.default_track_cover_10,
    )

    /**
     * Ресурс обложки для [key] — обычно это `Track.id`.
     *
     * `floorMod`, а не `%`: у отрицательных хешей остаток отрицателен, и голый
     * `%` дал бы IndexOutOfBounds на половине треков.
     */
    fun resourceFor(key: String?): Int =
        resources[Math.floorMod(key?.hashCode() ?: 0, resources.size)]

    /**
     * Лёгкая ссылка на локальную заглушку для MediaSession.
     *
     * В метаданные кладётся только URI, а не содержимое WebP. Поэтому полная
     * очередь больше не содержит сотни копий по 30–180 КБ и не раздувает Binder.
     * Декодирование выполняет ограниченный `BoundedArtworkBitmapLoader` только
     * для текущего элемента уведомления.
     */
    fun uriFor(context: Context, key: String?): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath(resourceFor(key).toString())
            .build()

    /**
     * Битмап обложки — для извлечения палитры.
     *
     * `inSampleSize = 4`: палитре хватает уменьшенной копии, а полноразмерный
     * декод ради усреднения цвета — лишние миллисекунды и мегабайты.
     */
    fun bitmapFor(context: Context, key: String?, sampleSize: Int = 4) =
        runCatching {
            BitmapFactory.decodeResource(
                context.resources,
                resourceFor(key),
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        }.getOrNull()

}
