package com.lmg.vk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.engine.PlaylistManager
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay

@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LiquidTheme.colors
    var name by remember(initialName) { mutableStateOf(initialName) }
    val normalized = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LiquidSurfaces.sheet(colors.isDark),
        title = {
            Text(
                title,
                color = LiquidSurfaces.textPrimary(colors.isDark),
                fontWeight = FontWeight.SemiBold,
                fontFamily = VkSansDisplay,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = normalized.isNotBlank(),
                onClick = { onConfirm(normalized) },
            ) {
                Text(confirmLabel, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LiquidSurfaces.textSecondary(colors.isDark))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
    playlists: List<PlaylistManager.Playlist>,
    onSelect: (PlaylistManager.Playlist) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LiquidTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LiquidSurfaces.sheet(colors.isDark),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "Add to playlist",
                color = LiquidSurfaces.textPrimary(colors.isDark),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = VkSansDisplay,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    "Create a playlist first",
                    color = LiquidSurfaces.textSecondary(colors.isDark),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 28.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(
                                    LiquidSurfaces.card(colors.isDark),
                                    RoundedCornerShape(16.dp),
                                )
                                .liquidClickable(
                                    pressedScale = LiquidMotion.PressButton,
                                    onClick = { onSelect(playlist) },
                                )
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                com.lmg.vk.ui.icons.LmgGlyphs.PlaylistOutline28,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    color = LiquidSurfaces.textPrimary(colors.isDark),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${playlist.tracks.size} tracks · ${if (playlist.remoteId != null) "Synced" else "Local"}",
                                    color = LiquidSurfaces.textSecondary(colors.isDark),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
