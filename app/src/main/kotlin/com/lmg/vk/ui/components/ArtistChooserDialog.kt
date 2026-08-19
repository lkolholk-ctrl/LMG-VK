package com.lmg.vk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.backend.MiniArtist
import com.lmg.vk.ui.glass.GlassCustomDialog
import com.lmg.vk.ui.glass.GlassDialogButton
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText

/**
 * Выбор конкретного исполнителя у совместного альбома/плейлиста.
 * Показывается только когда у сущности действительно больше одного artist id.
 */
@Composable
internal fun ArtistChooserDialog(
    artists: List<MiniArtist>,
    onSelect: (MiniArtist) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val isDark = colors.isDark
    val choices = artists
        .filter { !it.id.isNullOrBlank() }
        .distinctBy { it.id }

    GlassCustomDialog(
        visible = true,
        onDismiss = onDismiss,
        icon = lmgVector(LmgDrawables.Users3Outline28),
        iconTint = colors.accent,
        title = "Choose artist",
        subtitle = "Select an artist to view full profile",
        primaryButton = GlassDialogButton(
            text = "Cancel",
            backgroundColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF2F2F7),
            textColor = colors.textPrimary,
            onClick = onDismiss,
        ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(choices, key = { it.id ?: it.displayName }) { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(LiquidSurfaces.card(isDark))
                        .border(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f),
                            RoundedCornerShape(16.dp),
                        )
                        .liquidClickable(
                            pressedScale = LiquidMotion.PressButton,
                            onClick = { onSelect(artist) },
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = lmgVector(LmgDrawables.UserOutline28),
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = artist.displayName,
                        fontFamily = VkSansText,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
