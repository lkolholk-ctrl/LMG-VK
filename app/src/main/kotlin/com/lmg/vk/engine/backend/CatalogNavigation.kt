package com.lmg.vk.engine.backend

import java.net.URI

private val catalogVkHosts = setOf("vk.com", "vk.ru", "m.vk.com", "m.vk.ru", "vkontakte.ru", "m.vkontakte.ru")
private val catalogRootTabs = setOf("general", "explore", "podcasts", "audio_kids", "radiostations", "all")

/** Keep server destinations, including editorial pages and query-based VK links. */
internal fun catalogWebUrl(raw: String?): String? {
    val value = raw?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val absolute = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://vk.com$value"
        value.startsWith("vk.com/") || value.startsWith("vk.ru/") -> "https://$value"
        value.startsWith("vk://vk.com/") -> value.replaceFirst("vk://", "https://")
        else -> value
    }
    val uri = runCatching { URI(absolute) }.getOrNull() ?: return null
    return absolute.takeIf { uri.scheme?.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null }
}

internal fun catalogSectionFromUrl(raw: String?): String? {
    val uri = catalogWebUrl(raw)?.let(::URI) ?: return null
    if (uri.host.lowercase().removePrefix("www.") !in catalogVkHosts) return null
    if (uri.path?.trimEnd('/') != "/music") return null
    if (uri.rawQuery.orEmpty().split('&').any { it.substringBefore('=') == "z" }) return null
    return uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
        .firstOrNull { it.first() == "section" && it.size == 2 }?.get(1)
        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        ?.takeIf { it.isNotBlank() && it !in catalogRootTabs }
}

/** Music key_url is resolved by catalog.getAudio, never by an Android VIEW intent. */
internal fun catalogApiUrl(raw: String?): String? {
    val url = catalogWebUrl(raw) ?: return null
    val uri = URI(url)
    if (uri.host.lowercase().removePrefix("www.") !in catalogVkHosts) return null
    val path = uri.path.orEmpty().trimEnd('/')
    return url.takeIf {
        path == "/music" || path.startsWith("/music/") || path == "/audio" ||
            Regex("/audios-?\\d+").matches(path)
    }
}

/** Preserve collection/tab query semantics instead of opening the owner's whole library. */
internal fun catalogUrlNeedsApi(raw: String?): Boolean {
    val uri = catalogApiUrl(raw)?.let(::URI) ?: return false
    val keys = uri.rawQuery.orEmpty().split('&').map { it.substringBefore('=') }.toSet()
    return "z" !in keys && ("catalog" in keys || "section" in keys)
}

internal fun HomeItem.canPlayCatalogShortcut(): Boolean {
    if (!isCustom || isStreamMix || isRadio) return false
    if (musicOwnerId != null || !catalogSectionId.isNullOrBlank()) return true
    val uri = catalogWebUrl(catalogUrl)?.let(::URI) ?: return false
    if (uri.host.lowercase().removePrefix("www.") !in setOf("vk.com", "vk.ru", "m.vk.com", "m.vk.ru")) return false
    return Regex("/(?:music/(?:album|playlist)/|audio_playlist|audios)-?\\d+").containsMatchIn(uri.path.orEmpty()) ||
        uri.rawQuery.orEmpty().split('&').any { it.startsWith("z=audio_playlist") } || catalogSectionFromUrl(catalogUrl) != null
}
