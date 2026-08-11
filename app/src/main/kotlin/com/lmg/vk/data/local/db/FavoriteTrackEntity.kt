package com.lmg.vk.data.local.db

/**
 * Entity for storing liked/favorite tracks locally.
 * Fields mirror LibraryTrack for seamless mapping.
 */
data class FavoriteTrackEntity(
    val id: Long = 0,
    val trackId: String,
    val title: String,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val durationMs: Long = 0,
    val genre: String? = null,
    val imageUrl: String? = null,
    val streamUrl: String? = null,
    val artistId: String? = null,
    val collectionId: String? = null,
    val isExplicit: Boolean = false,
    val source: String? = null,
    val isAvailable: Boolean = true,
    /**
     * `access_key` записи VK — третий сегмент полного id.
     *
     * ЗАЧЕМ ХРАНИТЬ. Без него `audio.getById` возвращает трек, но БЕЗ поля
     * `url`: VK считает запись ограниченной. Раньше ключ брался только из
     * `trackCache` в памяти, поэтому музыка из библиотеки играла лишь пока трек
     * лежал там от поиска или каталога, а после перезапуска приложения кэш пуст
     * — и та же самая песня отвечала «трек не найден». Отсюда и жалоба
     * «сначала работало, потом резко перестало».
     */
    val accessKey: String? = null,
    /** Timestamp when the track was liked locally */
    val likedAt: Long = System.currentTimeMillis(),
    /** true = synced with cloud, false = pending sync */
    val isSynced: Boolean = false,
    /** true = pending deletion on cloud */
    val pendingDelete: Boolean = false,
    /** Реальный owner_audio id копии, которую `audio.add` создал в My Audio. */
    val cloudTrackId: String? = null,
)
