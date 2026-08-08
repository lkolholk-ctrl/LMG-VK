package com.lmg.vk.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Профиль пользователя из `users.get`.
 *
 * Базовые поля `id`, `first_name`, `last_name`, `photo_base`, `name`,
 * `is_followed` и `can_follow` подтверждены восстановленным
 * `ua.itaysonlab.vkapi2.objects.users.VKProfile`.
 *
 * Остальные поля запрашиваются через `fields` и приходят только когда владелец
 * токена их открыл: имена сверены со строками официального клиента
 * (`online,sex,last_seen,online_info,is_dead`, `verified`, `city`, `country`,
 * `counters`). VK отдаёт `online`/`verified`/`sex` числами, поэтому здесь Int.
 */
@JsonClass(generateAdapter = true)
data class VkAccountProfile(
    val id: Long = 0L,
    @Json(name = "first_name") val firstName: String = "",
    @Json(name = "last_name") val lastName: String = "",
    @Json(name = "photo_base") val photoBase: String = "",
    val name: String = "",
    @Json(name = "is_followed") val isFollowed: Boolean? = null,
    @Json(name = "can_follow") val canFollow: Boolean? = null,
    @Json(name = "photo_100") val photo100: String = "",
    @Json(name = "photo_200") val photo200: String = "",
    @Json(name = "photo_200_orig") val photo200Orig: String = "",
    @Json(name = "photo_400_orig") val photo400Orig: String = "",
    @Json(name = "photo_max_orig") val photoMaxOrig: String = "",
    /**
     * Оригинал аватара во всю загруженную величину.
     *
     * Ни одно из `photo_*` полей большим не является: `photo_max_orig` — это
     * «максимум из НАРЕЗАННЫХ VK превью», на практике 400px по большей стороне.
     * Для кружка 100dp это незаметно, а для шапки во всю ширину экрана (~1080px)
     * даёт мыло — жалоба «качество аватарки плохое, нечёткое, как будто 360p».
     *
     * `crop_photo.photo.sizes` содержит настоящие размеры вплоть до `w`/`z`
     * (1080–2560px), потому что это исходная фотография из альбома, а не превью.
     */
    @Json(name = "crop_photo") val cropPhoto: VkCropPhoto? = null,
    val domain: String = "",
    @Json(name = "screen_name") val screenName: String = "",
    val status: String = "",
    val bdate: String = "",
    val city: VkPlace? = null,
    val country: VkPlace? = null,
    @Json(name = "followers_count") val followersCount: Int? = null,
    val counters: VkProfileCounters? = null,
    val online: Int? = null,
    @Json(name = "online_info") val onlineInfo: VkOnlineInfo? = null,
    @Json(name = "last_seen") val lastSeen: VkLastSeen? = null,
    val verified: Int? = null,
    val sex: Int? = null,
) {
    val displayName: String
        get() = name.ifBlank {
            listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")
        }.ifBlank { domain }

    /**
     * Фото для маленьких мест: список, строка, кружок. Здесь превью достаточно —
     * тянуть ради 100dp оригинал на 2 Мп незачем.
     */
    val bestPhotoUrl: String
        get() = sequenceOf(photoMaxOrig, photo400Orig, photo200Orig, photo200, photo100, photoBase)
            .firstOrNull(String::isNotBlank)
            .orEmpty()

    /**
     * Фото для шапки во всю ширину экрана.
     *
     * Порядок: оригинал из `crop_photo` → превью с поднятым до максимума `cs`.
     *
     * ПОЧЕМУ НУЖЕН ПОДЪЁМ `cs`. На живом аккаунте `crop_photo` не пришёл вовсе,
     * а `photo_max_orig` оказался ссылкой с `cs=240x240` (лог: «crop_photo=НЕТ
     * sizes=0 … cs=240x240»). 240px, растянутые на ~1080px ширины экрана, и есть
     * та самая жалоба «нечёткое, как 360p».
     *
     * Новые ссылки VK (`/s/v1/ig2/…`) несут размер не в имени файла, а в
     * параметрах: `as=` перечисляет ДОСТУПНЫЕ нарезки, `cs=` выбирает одну из
     * них. Подпись (`cs` входит в хеш) остаётся валидной для любого размера из
     * `as`, поэтому подмена размера — законная операция, а не обход защиты.
     */
    val largePhotoUrl: String
        get() = cropPhoto?.photo?.largestUrl().orEmpty()
            .ifBlank { bestPhotoUrl.withLargestVkSize() }

    /** Короткий адрес профиля: `screen_name`, иначе `domain`, иначе `idN`. */
    val addressSlug: String
        get() = screenName.ifBlank { domain }.ifBlank { if (id != 0L) "id$id" else "" }

    val isOnline: Boolean
        get() = onlineInfo?.isOnline ?: (online == 1)

    val isVerified: Boolean
        get() = verified == 1

    /** `Город, Страна` — только из тех частей, что VK реально вернул. */
    val locationLabel: String
        get() = listOfNotNull(city?.title, country?.title)
            .filter(String::isNotBlank)
            .joinToString(", ")
}

/**
 * Поднять `cs` в ссылке VK до самого крупного размера из `as`.
 *
 * Ссылки нового формата (`/s/v1/ig2/…`) выглядят так:
 *
 *     …ig2/<hash>.jpg?quality=95&as=32x32,48x48,72x72,108x108,160x160,240x240,
 *     360x360,480x480,540x540,640x640,720x720,1080x1080&ava=1&cs=240x240
 *
 * `as` — что CDN умеет отдать, `cs` — что он отдаст. VK-клиент подставляет в
 * `cs` размер под конкретное место в вёрстке; нам для шапки нужен максимум.
 *
 * КОНСЕРВАТИВНО ПО УМОЛЧАНИЮ. Любая неожиданность (нет `as`, нет `cs`, размеры
 * не парсятся, максимум не больше текущего) → возвращаем ссылку БЕЗ изменений.
 * Испорченный URL здесь означал бы пустую шапку, а это хуже нерезкой.
 */
internal fun String.withLargestVkSize(): String {
    if (isBlank() || !contains("cs=") || !contains("as=")) return this

    fun paramValue(name: String): String? =
        Regex("""[?&]$name=([^&]+)""").find(this)?.groupValues?.getOrNull(1)

    val available = paramValue("as") ?: return this
    val current = paramValue("cs") ?: return this

    // "1080x1080" → 1080. Берём меньшую сторону: у VK нарезки квадратные, но
    // если попадётся прямоугольная, сравнение по меньшей стороне не завысит.
    fun sideOf(size: String): Int {
        val parts = size.split('x')
        if (parts.size != 2) return 0
        val w = parts[0].toIntOrNull() ?: return 0
        val h = parts[1].toIntOrNull() ?: return 0
        return minOf(w, h)
    }

    val largest = available.split(',')
        .filter { sideOf(it) > 0 }
        .maxByOrNull { sideOf(it) }
        ?: return this

    // Уже максимум (или в as нет ничего крупнее) — не трогаем.
    if (sideOf(largest) <= sideOf(current)) return this

    return replace("cs=$current", "cs=$largest")
}

/** Город/страна из `users.get`: VK отдаёт их объектом `{id, title}`. */
@JsonClass(generateAdapter = true)
data class VkPlace(
    val id: Int = 0,
    val title: String = "",
)

/**
 * `crop_photo` из `users.get` — исходная фотография, из которой сделан аватар.
 *
 * Нужна ровно за одним: в `photo.sizes` лежат НАСТОЯЩИЕ размеры (до 2560px), а
 * все поля `photo_*` профиля — это нарезанные превью не крупнее 400px.
 * Прямоугольники обрезки (`crop`/`rect`) нам не нужны: шапка кадрирует сама
 * через ContentScale.Crop.
 */
@JsonClass(generateAdapter = true)
data class VkCropPhoto(
    val photo: VkCropPhotoImage? = null,
)

@JsonClass(generateAdapter = true)
data class VkCropPhotoImage(
    val sizes: List<VkPhotoSize> = emptyList(),
) {
    /**
     * Ссылка на самый крупный размер.
     *
     * Выбор по ПЛОЩАДИ, а не по буквенному типу: у VK порядок типов
     * (`s m x o p q r y z w`) не совпадает с порядком величины — `w` больше `z`,
     * но `o…r` вклиниваются между ними. Сравнение чисел не зависит от того,
     * добавит ли VK новые буквы.
     */
    fun largestUrl(): String =
        sizes.filter { it.url.isNotBlank() }
            .maxByOrNull { it.width.toLong() * it.height.toLong() }
            ?.url
            .orEmpty()
}

@JsonClass(generateAdapter = true)
data class VkPhotoSize(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val type: String = "",
)

/**
 * `counters` из `users.get` — приходит только для владельца токена.
 * Здесь оставлены счётчики, которые есть смысл показать в музыкальном профиле.
 */
@JsonClass(generateAdapter = true)
data class VkProfileCounters(
    val audios: Int? = null,
    val friends: Int? = null,
    val groups: Int? = null,
    val followers: Int? = null,
    val subscriptions: Int? = null,
    @Json(name = "online_friends") val onlineFriends: Int? = null,
)

/** `online_info` — точный статус присутствия, приоритетнее числового `online`. */
@JsonClass(generateAdapter = true)
data class VkOnlineInfo(
    val visible: Boolean = true,
    @Json(name = "is_online") val isOnline: Boolean? = null,
    @Json(name = "is_mobile") val isMobile: Boolean? = null,
    @Json(name = "last_seen") val lastSeen: Long? = null,
    @Json(name = "app_id") val appId: Int? = null,
)

/** `last_seen` — время последнего визита и платформа. */
@JsonClass(generateAdapter = true)
data class VkLastSeen(
    val time: Long = 0L,
    val platform: Int? = null,
)
