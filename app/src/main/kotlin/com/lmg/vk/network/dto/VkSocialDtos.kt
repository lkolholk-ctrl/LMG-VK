package com.lmg.vk.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Элемент `friends.get` с `fields` — VK отдаёт полноценный объект пользователя,
 * а не голый id. Набор полей ограничен тем, что реально нужно списку друзей
 * и переходу в их аудиозаписи.
 */
@JsonClass(generateAdapter = true)
data class VkFriend(
    val id: Long = 0L,
    @Json(name = "first_name") val firstName: String = "",
    @Json(name = "last_name") val lastName: String = "",
    @Json(name = "photo_100") val photo100: String = "",
    @Json(name = "photo_200") val photo200: String = "",
    val domain: String = "",
    @Json(name = "screen_name") val screenName: String = "",
    val online: Int? = null,
    @Json(name = "online_info") val onlineInfo: VkOnlineInfo? = null,
    @Json(name = "last_seen") val lastSeen: VkLastSeen? = null,
    val verified: Int? = null,
    val sex: Int? = null,
    /** `"deleted"` или `"banned"`; у активных друзей поля нет. */
    val deactivated: String? = null,
    /**
     * Документированное поле `users.get`: доступны ли чужие аудиозаписи.
     * Официальный клиент его не запрашивает, поэтому здесь оно nullable —
     * `null` означает «неизвестно», и решение принимается по ответу `audio.get`.
     */
    @Json(name = "can_see_audio") val canSeeAudio: Int? = null,
) {
    val displayName: String
        get() = listOf(firstName, lastName)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { domain.ifBlank { "id$id" } }

    val avatarUrl: String
        get() = sequenceOf(photo200, photo100).firstOrNull(String::isNotBlank).orEmpty()

    val isOnline: Boolean
        get() = onlineInfo?.isOnline ?: (online == 1)

    val isActive: Boolean
        get() = deactivated.isNullOrBlank()

    /** `false` только когда VK явно сказал, что аудио закрыто. */
    val audioProbablyVisible: Boolean
        get() = canSeeAudio != 0
}

/**
 * Элемент `groups.get?extended=1`. В `owner_id` для аудио сообщества
 * подставляется `-id` — см. [audioOwnerId].
 */
@JsonClass(generateAdapter = true)
data class VkGroup(
    val id: Long = 0L,
    val name: String = "",
    @Json(name = "screen_name") val screenName: String = "",
    @Json(name = "is_closed") val isClosed: Int? = null,
    val type: String = "",
    @Json(name = "photo_100") val photo100: String = "",
    @Json(name = "photo_200") val photo200: String = "",
    @Json(name = "members_count") val membersCount: Int? = null,
    val verified: Int? = null,
    @Json(name = "is_member") val isMember: Int? = null,
) {
    /** Отрицательный id — то, что ждут музыкальные методы VK. */
    val audioOwnerId: Long
        get() = -id

    val avatarUrl: String
        get() = sequenceOf(photo200, photo100).firstOrNull(String::isNotBlank).orEmpty()

    val isPublicPage: Boolean
        get() = type == "page"

    val isEvent: Boolean
        get() = type == "event"
}
