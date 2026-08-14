package com.lmg.vk.network.dto

import com.lmg.vk.network.dto.music.AudioAudioDto
import com.lmg.vk.network.dto.music.BaseImageDto
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
    @Json(name = "status_audio") val statusAudio: AudioAudioDto? = null,
    @Json(name = "extended_status") val extendedStatus: VkExtendedStatus? = null,
    val bdate: String = "",
    val city: VkPlace? = null,
    val country: VkPlace? = null,
    @Json(name = "followers_count") val followersCount: Int? = null,
    @Json(name = "common_count") val commonCount: Int? = null,
    val counters: VkProfileCounters? = null,
    val online: Int? = null,
    @Json(name = "online_info") val onlineInfo: VkOnlineInfo? = null,
    @Json(name = "last_seen") val lastSeen: VkLastSeen? = null,
    val verified: Int? = null,
    val sex: Int? = null,
    val about: String? = null,
    val activities: String? = null,
    val interests: String? = null,
    val music: String? = null,
    val occupation: VkOccupation? = null,
    val site: String? = null,
    @Json(name = "home_town") val homeTown: String? = null,
    @Json(name = "is_friend") val isFriend: Int? = null,
    @Json(name = "friend_status") val friendStatus: Int? = null,
    @Json(name = "can_send_friend_request") val canSendFriendRequest: Int? = null,
    @Json(name = "can_see_audio") val canSeeAudio: Int? = null,
    @Json(name = "is_closed") val isClosed: Boolean? = null,
    @Json(name = "can_access_closed") val canAccessClosed: Boolean? = null,
    val deactivated: String? = null,
    val cover: VkOwnerCover? = null,
    @Json(name = "animated_avatar") val animatedAvatar: BaseImageDto? = null,
    @Json(name = "image_status") val imageStatus: VkImageStatus? = null,
    val description: String? = null,
    val descriptions: List<String> = emptyList(),
    val career: List<VkCareer> = emptyList(),
    val schools: List<VkSchool> = emptyList(),
    val universities: List<VkUniversity> = emptyList(),
    val relatives: List<VkRelative> = emptyList(),
    val relation: Int? = null,
    @Json(name = "relation_partner") val relationPartner: VkUserMin? = null,
    val personal: VkPersonal? = null,
    @Json(name = "mobile_phone") val mobilePhone: String? = null,
    @Json(name = "home_phone") val homePhone: String? = null,
    val skype: String? = null,
    @Json(name = "profile_buttons") val profileButtons: List<List<VkProfileButton>> = emptyList(),
    @Json(name = "friends") val friendsBlock: VkProfileFriendsBlock? = null,
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
        get() = onlineInfo?.let { info ->
            if (!info.visible) false else info.isOnline ?: (online == 1)
        } ?: (online == 1)

    val isVerified: Boolean
        get() = verified == 1

    /** `false` only when VK explicitly closed this user's audio. */
    val isAudioVisible: Boolean
        get() = canSeeAudio != 0

    val isAccessible: Boolean
        get() = isClosed != true || canAccessClosed == true

    val actualStatusAudio: AudioAudioDto?
        get() = extendedStatus?.audio ?: statusAudio

    val coverUrl: String?
        get() = cover?.bestUrl

    val animatedAvatarUrl: String?
        get() = animatedAvatar?.url?.takeIf(String::isNotBlank)

    /** `Город, Страна` — только из тех частей, что VK реально вернул. */
    val locationLabel: String
        get() = listOfNotNull(city?.title, country?.title)
            .filter(String::isNotBlank)
            .joinToString(", ")
}

/** Current job or study place from the official VK `users.get` profile model. */
@JsonClass(generateAdapter = true)
data class VkOccupation(
    val id: Long = 0L,
    val name: String = "",
    val type: String = "",
)

@JsonClass(generateAdapter = true)
data class VkExtendedStatus(
    val text: String? = null,
    val audio: AudioAudioDto? = null,
)

@JsonClass(generateAdapter = true)
data class VkOwnerCover(
    val enabled: Int? = null,
    val images: List<BaseImageDto> = emptyList(),
) {
    val bestUrl: String?
        get() = images
            .filter { it.url.isNotBlank() }
            .maxByOrNull { it.width * it.height }
            ?.url
            ?.takeIf { enabled != 0 }
}

@JsonClass(generateAdapter = true)
data class VkImageStatus(
    val id: Int? = null,
    val name: String? = null,
    val images: List<BaseImageDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class VkCareer(
    @Json(name = "city_id") val cityId: Int? = null,
    @Json(name = "city_name") val cityName: String? = null,
    val company: String? = null,
    val from: Int? = null,
    val position: String? = null,
    val until: Int? = null,
)

@JsonClass(generateAdapter = true)
data class VkSchool(
    val id: String? = null,
    val name: String? = null,
    val speciality: String? = null,
    @Json(name = "type_str") val typeLabel: String? = null,
    @Json(name = "year_from") val yearFrom: Int? = null,
    @Json(name = "year_to") val yearTo: Int? = null,
    @Json(name = "year_graduated") val yearGraduated: Int? = null,
)

@JsonClass(generateAdapter = true)
data class VkUniversity(
    val id: Int? = null,
    val name: String? = null,
    @Json(name = "faculty_name") val facultyName: String? = null,
    @Json(name = "chair_name") val chairName: String? = null,
    val graduation: Int? = null,
    @Json(name = "education_form") val educationForm: String? = null,
    @Json(name = "education_status") val educationStatus: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkRelative(
    val type: String = "",
    @Json(name = "birth_date") val birthDate: String? = null,
    val id: Long? = null,
    val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkUserMin(
    val id: Long = 0L,
    @Json(name = "first_name") val firstName: String = "",
    @Json(name = "last_name") val lastName: String = "",
) {
    val displayName: String get() = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")
}

@JsonClass(generateAdapter = true)
data class VkPersonal(
    val alcohol: Int? = null,
    @Json(name = "inspired_by") val inspiredBy: String? = null,
    val langs: List<String> = emptyList(),
    @Json(name = "life_main") val lifeMain: Int? = null,
    @Json(name = "people_main") val peopleMain: Int? = null,
    val political: Int? = null,
    val religion: String? = null,
    val smoking: Int? = null,
)

@JsonClass(generateAdapter = true)
data class VkProfileButton(
    val action: VkProfileButtonAction = VkProfileButtonAction(),
    val text: String = "",
    val uid: String? = null,
    @Json(name = "badge_counter") val badgeCounter: Int? = null,
)

@JsonClass(generateAdapter = true)
data class VkProfileButtonAction(
    val type: String? = null,
    val url: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkProfileFriendsBlock(
    val offset: Int? = null,
    val friends: List<VkFriend> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class VkSaveProfileInfoResponse(
    val changed: Int = 0,
)

@JsonClass(generateAdapter = true)
data class VkOwnerUploadServer(
    @Json(name = "upload_url") val uploadUrl: String,
    @Json(name = "fallback_upload_url") val fallbackUploadUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class VkSaveOwnerPhotoResponse(
    @Json(name = "photo_hash") val photoHash: String? = null,
    @Json(name = "photo_src") val photoSrc: String? = null,
    @Json(name = "photo_src_big") val photoSrcBig: String? = null,
    @Json(name = "photo_src_small") val photoSrcSmall: String? = null,
    val saved: Int? = null,
)

@JsonClass(generateAdapter = true)
data class VkSaveOwnerCoverResponse(
    val images: List<BaseImageDto> = emptyList(),
)

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
