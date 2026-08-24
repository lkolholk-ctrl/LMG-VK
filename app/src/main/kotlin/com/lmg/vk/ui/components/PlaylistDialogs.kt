package com.lmg.vk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.engine.PlaylistManager
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

@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val isDark = colors.isDark
    var name by remember(initialName) { mutableStateOf(initialName) }
    val normalized = name.trim()
    val subtitleText = stringResource(R.string.playlist_name_hint)
    val cancelText = stringResource(R.string.action_cancel)
    val nameLabel = stringResource(R.string.field_name)

    GlassCustomDialog(
        visible = true,
        onDismiss = onDismiss,
        icon = lmgVector(LmgDrawables.PlaylistOutline28),
        iconTint = colors.accent,
        title = title,
        subtitle = subtitleText,
        primaryButton = GlassDialogButton(
            text = confirmLabel,
            backgroundColor = colors.accent,
            enabled = normalized.isNotBlank(),
            onClick = { onConfirm(normalized) },
        ),
        secondaryButton = GlassDialogButton(
            text = cancelText,
            onClick = onDismiss,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text(nameLabel, fontFamily = VkSansText) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f),
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedContainerColor = LiquidSurfaces.card(isDark),
                    unfocusedContainerColor = LiquidSurfaces.card(isDark),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "${name.length}/80",
                    fontFamily = VkSansText,
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(
    playlists: List<PlaylistManager.Playlist>,
    onSelect: (PlaylistManager.Playlist) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val addToPlaylistText = stringResource(R.string.add_to_playlist)
    val createFirstText = stringResource(R.string.create_playlist_first)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LiquidSurfaces.sheet(colors.isDark),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                addToPlaylistText,
                color = LiquidSurfaces.textPrimary(colors.isDark),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = VkSansDisplay,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    createFirstText,
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
                                    stringResource(
                                        R.string.playlist_track_status,
                                        pluralStringResource(R.plurals.track_count, playlist.tracks.size, playlist.tracks.size),
                                        stringResource(if (playlist.remoteId != null) R.string.playlist_synced else R.string.playlist_local),
                                    ),
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
