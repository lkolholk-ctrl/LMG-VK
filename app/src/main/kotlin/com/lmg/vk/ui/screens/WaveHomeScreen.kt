package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.viewmodel.HomeViewModel

/**
 * Presentation-only shell for the future VK Mix screen.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun WaveHomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    onOpenAuth: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    animationsActive: Boolean = true,
) {
    val colors = com.lmg.vk.ui.theme.LiquidTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.settingsBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0077FF), Color(0xFF0044B3))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "VK Mix",
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = AppFontFamily,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Персональная волна музыки VK.",
                color = colors.textTertiary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = AppFontFamily,
                textAlign = TextAlign.Center
            )
        }
    }
}
