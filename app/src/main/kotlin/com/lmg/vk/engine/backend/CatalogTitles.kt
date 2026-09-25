package com.lmg.vk.engine.backend

import com.lmg.vk.R

internal fun catalogTitleResource(title: String, type: String): Int? = when (title) {
    "catalog_banners", "content_cards", "audio_content_cards", "links", "longreads" -> R.string.catalog_featured
    "music_audios", "audios", "tracks" -> R.string.catalog_tracks
    "music_playlists", "playlists" -> R.string.playlists_title
    "music_recommended_playlists", "recommended_playlists", "recommendations" -> R.string.vk_recommendations
    "artists" -> R.string.section_artists
    "albums" -> R.string.section_albums
    "audio_stream_mixes", "stream_mixes" -> R.string.catalog_mixes
    "curators", "music_owners", "groups", "profiles" -> R.string.catalog_curators
    "radiostations", "radio", "radio_stations" -> R.string.radio_stations
    "podcasts", "podcast_episodes", "podcast_slider_items" -> R.string.podcasts_section
    "audiobooks", "audio_books" -> R.string.audiobooks_section
    "audio_books_persons" -> R.string.audiobook_authors_title
    "artist_videos", "videos" -> R.string.music_videos
    "following_updates", "audio_followings_update_info" -> R.string.catalog_following_updates
    else -> if (title.isNotBlank() && title == type) R.string.catalog_featured else null
}
