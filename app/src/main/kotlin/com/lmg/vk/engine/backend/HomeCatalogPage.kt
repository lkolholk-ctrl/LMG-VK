package com.lmg.vk.engine.backend

enum class HomeCatalogPage(val section: String) {
    MAIN("general"),
    EXPLORE("explore");

    fun url(accountId: Long): String {
        require(accountId > 0) { "A signed-in VK account is required" }
        return "https://vk.com/audios$accountId?section=$section"
    }
}

internal fun homeCatalogCacheKey(key: String, accountId: Long, page: HomeCatalogPage? = null): String =
    "${key}_account_$accountId" + (page?.let { "_${it.section}" } ?: "")
