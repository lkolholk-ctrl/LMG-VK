package com.lmg.vk.ui.lyrics

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lmg.vk.ui.player.SharedArtworkBackground

/** Standalone lyrics use the same renderer as FullPlayer's shared surface. */
@Composable
fun LyricsBackground(
    albumArtUri: Uri?,
    coverUrl: String? = null,
    audioFileUri: Uri? = null,
    albumId: Long = -1L,
    modifier: Modifier = Modifier
) {
    SharedArtworkBackground(albumArtUri, coverUrl, audioFileUri, albumId, modifier = modifier)
}
