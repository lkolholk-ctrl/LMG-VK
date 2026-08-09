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
    /**
     * Фото, которое этот токен реально отдаёт.
     *
     * `photo_100`/`photo_200` приходили ПУСТЫМИ (лог: «friends: 0/40 с
     * ссылкой»), хотя запрашивались — токен официального клиента отдаёт аватар
     * через `photo_base`. Так же поступает и VK X (в его VKProfile ровно это
     * поле), и в нашем профиле аватар работал именно потому, что `photo_base`
     * есть в CURRENT_PROFILE_FIELDS.
     */
    @Json(name = "photo_base") val photoBase: String = "",
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
        get() = sequenceOf(photoBase, photo200, photo100)
            .firstOrNull(String::isNotBlank)
            .orEmpty()

    /**
     * Аватар для шапки во всю ширину. У друзей VK отдаёт одну ссылку,
     * но в новых ссылках размер задан параметром `cs`, и его можно поднять до
     * максимума из `as` (см. [withLargestVkSize]). Без этого шапка получила бы
     * 200px, растянутые на ~1080px ширины экрана.
     */
    val headerPhotoUrl: String
        get() = avatarUrl.withLargestVkSize()

    val isOnline: Boolean
        get() = onlineInfo?.isOnline ?: (online == 1)

    val isActive: Boolean
        get() = deactivated.isNullOrBlank()

    /** `false` только когда VK явно сказал, что аудио закрыто. */
    val audioProbablyVisible: Boolean
        get() = canSeeAudio != 0
}

/**
 * Элемент `groups.get?extended=1` и `groups.getById`. В `owner_id` для аудио
 * сообщества подставляется `-id` — см. [audioOwnerId].
 *
 * Все поля сверх обязательных (`id`, `name`) nullable/с дефолтом намеренно:
 * `groups.get` запрашивает узкий набор `fields`, `groups.getById` — широкий
 * ([com.lmg.vk.network.methods.VkMethodsRegistry.GROUP_DETAIL_FIELDS]), и один
 * DTO обслуживает оба. Отсутствие поля тут означает «VK его не присылал», а не
 * «значение пустое» — экран обязан различать это и не рисовать нули вместо данных.
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
    /**
     * Аватар сообщества, который этот токен реально отдаёт — та же история, что
     * у [VkFriend.photoBase]: `photo_100`/`photo_200` приходили пустыми (лог:
     * «groups: 0/40 с ссылкой»).
     */
    @Json(name = "photo_base") val photoBase: String = "",
    @Json(name = "members_count") val membersCount: Int? = null,
    val verified: Int? = null,
    @Json(name = "is_member") val isMember: Int? = null,
    // ── Поля, приходящие только от `groups.getById` с широким `fields` ──
    /** Однострочное «чем занимается» сообщества (у VK MP3 Mod — `infoLine`). */
    val activity: String? = null,
    /** Полное описание сообщества; у многих сообществ пустое. */
    val description: String? = null,
    /**
     * Статус-строка. У VK это ОБЪЕКТ `{"text": "...", "audio": {...}}`, а не
     * строка (`GetFullProfile.java:466` — `getJSONObject("status").optString("text")`),
     * поэтому здесь вложенный DTO, а не `String` — иначе Moshi упал бы на разборе.
     */
    val status: VkGroupStatus? = null,
    /** Внешний сайт сообщества. */
    val site: String? = null,
    /** Уровень прав ТЕКУЩЕГО пользователя: 0 — не управляющий. */
    @Json(name = "admin_level") val adminLevel: Int? = null,
    /** Постить может текущий пользователь; для музыки не нужно, но входит в `fields`. */
    @Json(name = "can_post") val canPost: Int? = null,
    @Json(name = "can_message") val canMessage: Int? = null,
    /** Дата начала события (unix); только у `type == "event"`. */
    @Json(name = "start_date") val startDate: Long? = null,
    @Json(name = "finish_date") val finishDate: Long? = null,
    /** Крупные аватары — нужны шапке экрана, `photo_200` для неё мелковат. */
    @Json(name = "photo_400_orig") val photo400Orig: String? = null,
    @Json(name = "photo_max_orig") val photoMaxOrig: String? = null,
    /** Обложка сообщества (широкий баннер) — рисуется фоном шапки. */
    val cover: VkGroupCover? = null,
    /**
     * Счётчики разделов. VK отдаёт их ТОЛЬКО управляющим сообщества, поэтому
     * для обычного зрителя поле приходит пустым — число аудио берётся из
     * `audio.get`, а не отсюда (см. `AudioGet.java:36` в VK MP3 Mod: там счётчик
     * аудио сообщества тянут именно через `counters.audios`, когда он доступен).
     */
    val counters: VkGroupCounters? = null,
    /** `"deleted"`/`"banned"` — сообщество недоступно, показывать это честно. */
    val deactivated: String? = null,
) {
    /** Отрицательный id — то, что ждут музыкальные методы VK. */
    val audioOwnerId: Long
        get() = -id

    val avatarUrl: String
        get() = sequenceOf(photoBase, photo200, photo100)
            .firstOrNull(String::isNotBlank)
            .orEmpty()

    /**
     * Аватар для шапки: сначала самые большие варианты, затем подъём `cs` до
     * максимума из `as` ([withLargestVkSize]) — иначе даже photo_max_orig
     * приходит нарезкой 240px и на всю ширину экрана растягивается мылом.
     */
    val bigAvatarUrl: String
        get() = sequenceOf(photoMaxOrig, photo400Orig, photoBase, photo200, photo100)
            .firstOrNull { !it.isNullOrBlank() }
            .orEmpty()
            .withLargestVkSize()

    /** Широкая обложка; `null` — её у сообщества нет, шапка обойдётся аватаром. */
    val coverUrl: String?
        get() = cover?.bestUrl

    val isPublicPage: Boolean
        get() = type == "page"

    val isEvent: Boolean
        get() = type == "event"

    /** Закрытое или приватное: `1` — закрытое, `2` — приватное (см. `Group.java`). */
    val isPrivate: Boolean
        get() = (isClosed ?: 0) > 0

    val isMemberOrNull: Boolean?
        get() = isMember?.let { it == 1 }

    /** Сообщество удалено/заблокировано — контента у него не будет вообще. */
    val isDeactivated: Boolean
        get() = !deactivated.isNullOrBlank()

    /** Текст статуса без вложенности — удобнее для UI. */
    val statusText: String?
        get() = status?.text?.takeIf(String::isNotBlank)

    /**
     * Тип сообщества словами. Формулировки соответствуют `Group.typeString()`
     * VK MP3 Mod: у публичной страницы и события свои названия, у группы тип
     * зависит от закрытости.
     */
    val typeLabel: String
        get() = when {
            isEvent -> "Event"
            isPublicPage -> "Public page"
            isClosed == 2 -> "Private group"
            isClosed == 1 -> "Closed group"
            else -> "Community"
        }

    /** Адрес сообщества: своё короткое имя либо каноничное `club<id>`. */
    val addressSlug: String
        get() = screenName.ifBlank { "club$id" }
}

/**
 * `status` сообщества. `audio` (играющий в статусе трек) VK присылает тут же,
 * но разбирать его нечем без полного DTO трека — а гадать поля нельзя, поэтому
 * взято только то, что достоверно известно по реверсу: текст.
 */
@JsonClass(generateAdapter = true)
data class VkGroupStatus(
    val text: String? = null,
)

/**
 * Обложка сообщества: VK отдаёт набор размеров в `images`, из которого берём
 * самый широкий — шапка растянута во всю ширину экрана.
 */
@JsonClass(generateAdapter = true)
data class VkGroupCover(
    val enabled: Int? = null,
    val images: List<VkGroupCoverImage> = emptyList(),
) {
    val bestUrl: String?
        get() = images
            .filter { it.url.isNotBlank() }
            .maxByOrNull { it.width * it.height }
            ?.url
            ?.takeIf { enabled != 0 }
}

@JsonClass(generateAdapter = true)
data class VkGroupCoverImage(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * `counters` сообщества — только для управляющих. Поля, не относящиеся к
 * музыке, здесь не описаны: их значения экран всё равно не показывает, а
 * лишние поля в DTO создают ложное впечатление, что они где-то используются.
 */
@JsonClass(generateAdapter = true)
data class VkGroupCounters(
    val audios: Int? = null,
    val albums: Int? = null,
    val photos: Int? = null,
    val videos: Int? = null,
)
