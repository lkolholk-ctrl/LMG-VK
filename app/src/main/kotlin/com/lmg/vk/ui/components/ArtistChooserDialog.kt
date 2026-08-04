package com.lmg.vk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lmg.vk.engine.backend.MiniArtist
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme

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
    val choices = artists
        .filter { !it.id.isNullOrBlank() }
        .distinctBy { it.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose artist",
                color = LiquidSurfaces.textPrimary(colors.isDark),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(LiquidSurfaces.card(colors.isDark))
                            .liquidClickable(
                                pressedScale = LiquidMotion.PressButton,
                                onClick = { onSelect(artist) },
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = artist.displayName,
                            color = LiquidSurfaces.textPrimary(colors.isDark),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = LiquidSurfaces.sheet(colors.isDark),
    )
}
