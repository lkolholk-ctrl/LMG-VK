package com.lmg.vk.ui.glass

import android.content.Context
import android.graphics.BitmapFactory
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

    /** Кэш байтов: файл один и тот же, а метаданные строятся на каждый трек. */
    private val bytesCache = java.util.concurrent.ConcurrentHashMap<Int, ByteArray>()

    /**
     * Сырые байты WebP-файла обложки — для `MediaMetadata.setArtworkData`.
     *
     * ПОЧЕМУ БАЙТЫ, А НЕ URI. `artworkUri` в media3 читает `BitmapLoader`, и
     * дефолтный (`DataSourceBitmapLoader` поверх `DefaultDataSource`) схему
     * `android.resource://` НЕ поддерживает — уведомление и экран блокировки
     * оставались без картинки, хотя URI был выставлен верно. `artworkData`
     * загрузчика не требует вовсе: media3 декодирует байты сам.
     *
     * Читаем файл ресурса как есть, без декода в Bitmap и повторного сжатия:
     * WebP уже сжат (~30-180 КБ), и лишний цикл decode/compress только тратил
     * бы время и память на каждом переключении трека.
     */
    fun artworkBytes(context: Context, key: String?): ByteArray? {
        val id = resourceFor(key)
        bytesCache[id]?.let { return it }
        return runCatching {
            context.resources.openRawResource(id).use { it.readBytes() }
        }.getOrNull()?.also { bytesCache[id] = it }
    }
}
