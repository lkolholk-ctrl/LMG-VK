package com.lmg.vk.ui.player

import android.net.Uri
import androidx.compose.runtime.Composable
import com.lmg.vk.ui.glass.AlbumColors
import com.lmg.vk.ui.lyrics.LyricsScreen

@Composable
fun LyricsSheet(
    audioFileUri: Uri?,
    lrcText: String?,
    currentPositionMs: Long,
    trackTitle: String = "",
    trackArtist: String = "",
    trackDurationMs: Long = 0L,
    albumArtUri: Uri? = null,
    coverUrl: String? = null,
    albumId: Long = -1L,
    trackId: String? = null,
    albumColors: AlbumColors,
    onRequestControls: () -> Unit
) {
    LyricsScreen(
        audioFileUri = audioFileUri, lrcText = lrcText, currentPositionMs = currentPositionMs,
        trackTitle = trackTitle, trackArtist = trackArtist, trackDurationMs = trackDurationMs,
        albumArtUri = albumArtUri, coverUrl = coverUrl, albumId = albumId, trackId = trackId,
        albumColors = albumColors, onClose = onRequestControls,
    )
}
