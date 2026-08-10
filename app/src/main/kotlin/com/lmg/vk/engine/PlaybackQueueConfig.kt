package com.lmg.vk.engine

/**
 * Queue capabilities recovered from the official VK 8.185 player
 * (`com.vk.music.player.playback.PlaybackQueueConfig`).
 *
 * VK keeps the queue source and its capabilities separate: a regular music
 * source may load its own tracks, while only VK Mix has endless listening and
 * may request another batch at the end of the queue.
 */
enum class PlaybackQueueConfig(
    val loadingAvailable: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val loopEnabled: Boolean = false,
    val prefetchEnabled: Boolean = false,
    val endlessListeningEnabled: Boolean = false,
    val loadTracksDirectly: Boolean = loadingAvailable,
    val loadMoreIfEndOfQueue: Boolean = endlessListeningEnabled,
) {
    DEFAULT,
    MUSIC_WITHOUT_SOURCE_CONFIG(
        shuffleEnabled = true,
        loopEnabled = true,
    ),
    MUSIC_CONFIG(
        loadingAvailable = true,
        shuffleEnabled = true,
        loopEnabled = true,
        loadTracksDirectly = false,
    ),
    PODCAST_CONFIG(
        loadingAvailable = true,
        prefetchEnabled = true,
    ),
    VK_MIX_CONFIG(
        loadingAvailable = true,
        prefetchEnabled = true,
        endlessListeningEnabled = true,
    ),
    RADIO_CONFIG(
        loadingAvailable = true,
        loadTracksDirectly = false,
    ),
}
