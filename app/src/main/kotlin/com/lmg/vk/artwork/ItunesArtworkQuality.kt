package com.lmg.vk.artwork

import java.net.URI

internal object ItunesArtworkQuality {
    fun urls(url: String): List<String> {
        val uri = runCatching { URI(url) }.getOrNull() ?: return listOf(url)
        if (uri.scheme != "https" || uri.host?.endsWith(".mzstatic.com") != true ||
            !uri.rawPath.startsWith("/image/thumb/")) return listOf(url)
        val source = uri.rawPath.removePrefix("/image/thumb/").substringBeforeLast('/')
        if (source.isBlank()) return listOf(url)
        return listOf(
            "https://a5.mzstatic.com/us/r1000/0/$source",
            "https://${uri.host}/image/thumb/$source/10000x10000bb.jpg",
            url,
        ).distinct()
    }
}
