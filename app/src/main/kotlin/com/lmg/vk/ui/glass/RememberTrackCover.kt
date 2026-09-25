package com.lmg.vk.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lmg.vk.artwork.ArtworkLoadState
import com.lmg.vk.artwork.ArtworkSelection
import com.lmg.vk.artwork.ItunesArtworkMatcher
import com.lmg.vk.artwork.ArtworkQuery
import com.lmg.vk.artwork.ItunesArtworkRepository
import com.lmg.vk.engine.Track

private val LocalPlayingArtwork = compositionLocalOf<ArtworkSelection?> { null }

fun Track.artworkQuery() = ArtworkQuery(title, artist, durationMs, albumName)

@Composable
fun rememberTrackArtwork(query: ArtworkQuery?, fallback: String?): ArtworkLoadState {
    val playing = LocalPlayingArtwork.current
    if (query != null && playing?.matches(query) == true) {
        return ArtworkLoadState(playing.isReady, playing.coverUrl)
    }
    return rememberIndependentTrackArtwork(query, fallback)
}

@Composable
private fun rememberIndependentTrackArtwork(request: ArtworkQuery?, fallback: String?): ArtworkLoadState {
    val query = remember(request) { request?.let(ItunesArtworkMatcher::lookupQuery) }
    val context = LocalContext.current.applicationContext
    return key(query, fallback) {
        val initial = remember { ArtworkLoadState.initial(query, fallback, query?.let(ItunesArtworkRepository::cached)) }
        val result by produceState(initial) {
            if (!initial.isReady && query != null) {
                val cover = ItunesArtworkRepository.find(context, query)
                value = ArtworkLoadState(true, cover ?: fallback)
            }
        }
        result
    }
}

@Composable
fun rememberTrackArtwork(track: Track?): ArtworkLoadState =
    rememberTrackArtwork(track?.artworkQuery(), track?.coverUrl)

@Composable
fun rememberTrackCover(query: ArtworkQuery?, fallback: String?): String? =
    rememberTrackArtwork(query, fallback).coverUrl

@Composable
fun rememberTrackCover(track: Track?): String? = rememberTrackArtwork(track).coverUrl

@Composable
fun ProvidePlayingArtwork(track: Track?, content: @Composable () -> Unit) {
    val query = track?.artworkQuery()
    val artwork = rememberIndependentTrackArtwork(query, track?.coverUrl)
    CompositionLocalProvider(LocalPlayingArtwork provides query?.let {
        ArtworkSelection(it, artwork.coverUrl, artwork.isReady)
    }) {
        content()
    }
}
