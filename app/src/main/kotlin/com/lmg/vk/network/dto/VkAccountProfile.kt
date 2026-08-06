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

    /** Самая качественная доступная фотография профиля с безопасными fallback. */
    val bestPhotoUrl: String
        get() = sequenceOf(photoMaxOrig, photo400Orig, photo200Orig, photo200, photo100, photoBase)
            .firstOrNull(String::isNotBlank)
            .orEmpty()

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

/** Город/страна из `users.get`: VK отдаёт их объектом `{id, title}`. */
@JsonClass(generateAdapter = true)
data class VkPlace(
    val id: Int = 0,
    val title: String = "",
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
