package com.lmg.vk.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.glass.GlassCustomDialog
import com.lmg.vk.ui.components.TrackActionsSheet
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.viewmodel.VkHistoryViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lmg.vk.R
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.network.dto.VkAccountProfile
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.AudioTrack
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.theme.LiquidMetrics
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText

internal fun playProfileTracks(context: Context, tracks: List<AudioTrack>, selectedId: String? = null): Boolean {
    val playable = MusicBackend.adoptTracks(tracks).map { it.toTrack() }.filter { it.isAvailable }
    if (playable.isEmpty()) return false
    val selected = selectedId?.let { id -> playable.indexOfFirst { it.id == id } } ?: 0
    if (selected < 0) return false
    PlayerController.play(context, playable, selected)
    return true
}

@Composable
internal fun MusicProfileToolbar(onBack: () -> Unit, actions: List<Pair<String, () -> Unit>>) {
    val colors = LiquidTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(LiquidSurfaces.sheet(colors.isDark))
            .height(44.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).liquidClickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(LmgGlyphs.ArrowLeftOutline28, stringResource(R.string.action_back), tint = colors.textPrimary)
        }
        Text(stringResource(R.string.profile_title), Modifier.weight(1f), textAlign = TextAlign.Center,
            fontFamily = VkSansDisplay, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Box {
            Box(Modifier.size(44.dp).clip(CircleShape).liquidClickable { expanded = true }, contentAlignment = Alignment.Center) {
                Icon(lmgVector(LmgDrawables.MoreVertical28), stringResource(R.string.profile_actions), tint = colors.textPrimary)
            }

        }
    }
    GlassCustomDialog(
        visible = expanded,
        onDismiss = { expanded = false },
        title = stringResource(R.string.profile_actions),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { (title, action) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(colors.textPrimary.copy(alpha = 0.05f))
                    .liquidClickable { expanded = false; action() }
                    .padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), color = colors.textPrimary, fontFamily = VkSansText, fontSize = 15.sp)
                    Icon(LmgGlyphs.ChevronRightOutline24, null, tint = colors.iconMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

}

@Composable
internal fun MusicProfileHeader(
    profile: VkAccountProfile?,
    musicTotal: Int?,
    playlistTotal: Int?,
    friendsTotal: Int?,
    friendshipLabel: String,
    friendshipEnabled: Boolean,
    onFriendship: () -> Unit,
    fallbackName: String = "",
    fallbackAvatar: String? = null,
) {
    val colors = LiquidTheme.colors
    val activeAccountId by com.lmg.vk.engine.backend.MusicAuth.profileId.collectAsState()
    val name = profile?.displayName?.takeIf(String::isNotBlank) ?: fallbackName
    val avatar = profile?.animatedAvatarUrl ?: profile?.largePhotoUrl?.takeIf(String::isNotBlank) ?: fallbackAvatar
    Column(
        Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp)).background(LiquidSurfaces.card(colors.isDark)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                Text(name.split(' ').filter(String::isNotBlank).take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                    color = Color.White, fontFamily = VkSansDisplay, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (!avatar.isNullOrBlank()) AsyncImage(avatar, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(name, color = colors.textPrimary, fontFamily = VkSansDisplay, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                profile?.addressSlug?.takeIf(String::isNotBlank)?.let {
                    Text("@$it", color = colors.textSecondary, fontFamily = VkSansText, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.12f))
                .liquidClickable(enabled = friendshipEnabled, onClick = onFriendship), contentAlignment = Alignment.Center) {
                Icon(
                    when {
                        profile != null && profile.id == activeAccountId -> lmgVector(LmgDrawables.UserPenOutline28)
                        profile?.friendStatus == 3 || profile?.isFriend == 1 -> lmgVector(LmgDrawables.UserAddedOutline28)
                        else -> lmgVector(LmgDrawables.UserAddOutline28)
                    },
                    friendshipLabel, tint = if (friendshipEnabled) colors.accent else colors.textTertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        val counts = listOfNotNull(
            musicTotal?.let { pluralStringResource(R.plurals.track_count, it, it) },
            playlistTotal?.let { pluralStringResource(R.plurals.playlist_count, it, it) },
            friendsTotal?.let { pluralStringResource(R.plurals.profile_friends_count, it, it) },
        ).joinToString(" · ")
        if (counts.isNotEmpty()) Text(counts, color = colors.textSecondary, fontFamily = VkSansText, fontSize = 12.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }

}

@Composable
internal fun MusicProfileTabs(selected: Int, onSelect: (Int) -> Unit) {
    val colors = LiquidTheme.colors
    val labels = listOf(stringResource(R.string.music_label), stringResource(R.string.playlists_title), stringResource(R.string.information))
    Row(Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 6.dp)
        .clip(RoundedCornerShape(18.dp)).background(LiquidSurfaces.card(colors.isDark)).padding(3.dp).selectableGroup()) {
        labels.forEachIndexed { index, label ->
            Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(15.dp))
                .background(if (selected == index) colors.accent else Color.Transparent)
                .selectable(selected = selected == index, role = Role.Tab, onClick = { onSelect(index) }), contentAlignment = Alignment.Center) {
                Text(label, color = if (selected == index) Color.White else colors.textSecondary,
                    fontFamily = VkSansText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun MusicProfileHero(
    title: String,
    musicTotal: Int?,
    playlistTotal: Int?,
    enabled: Boolean,
    onOpenMusic: () -> Unit,
    onPlay: () -> Unit,
) {
    val colors = LiquidTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding).padding(top = 6.dp, bottom = 12.dp)
        .clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(colors.accent, Color(0xFF4D63B1))))
        .liquidClickable(enabled = enabled, onClick = onOpenMusic).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(LmgGlyphs.MusicNote24, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.White, fontFamily = VkSansDisplay, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(
                musicTotal?.let { pluralStringResource(R.plurals.track_count, it, it) },
                playlistTotal?.let { pluralStringResource(R.plurals.playlist_count, it, it) },
            ).joinToString(" · "), color = Color.White.copy(alpha = 0.8f), fontFamily = VkSansText, fontSize = 12.sp)
        }
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = if (enabled) 0.95f else 0.3f))
            .liquidClickable(enabled = enabled, onClick = onPlay), contentAlignment = Alignment.Center) {
            Icon(LmgGlyphs.Play28, stringResource(R.string.action_listen), tint = Color(0xFF4D63B1), modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
internal fun MusicProfileLinks(friendCount: Int?, onFriends: () -> Unit, onDetails: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MusicProfileTile(stringResource(R.string.friends_title), friendCount?.toString() ?: "…", LmgGlyphs.UsersOutline28, Modifier.weight(1f), onFriends)
        MusicProfileTile(stringResource(R.string.profile_about_title), stringResource(R.string.more_information), LmgGlyphs.InfoCircleOutline28, Modifier.weight(1f), onDetails)
    }
}

@Composable
private fun MusicProfileTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(LiquidSurfaces.card(colors.isDark)).liquidClickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = colors.iconMuted, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = colors.textPrimary, fontFamily = VkSansText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = colors.textSecondary, fontFamily = VkSansText, fontSize = 11.sp, maxLines = 1)
        }
        Icon(LmgGlyphs.ChevronRightOutline24, null, tint = colors.iconMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun MusicProfileSection(title: String, action: String, onAction: () -> Unit) {
    val colors = LiquidTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding).padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), color = colors.textPrimary, fontFamily = VkSansDisplay, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.clip(CircleShape).liquidClickable(onClick = onAction).padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(action, color = colors.accent, fontFamily = VkSansText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(LmgGlyphs.ChevronRightOutline24, null, Modifier.size(18.dp), tint = colors.accent)
        }
    }
}

@Composable
internal fun MusicProfilePlaylists(playlists: List<AudioPlaylist>, onOpenPlaylist: (String) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = LiquidMetrics.ScreenPadding), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(playlists, key = { it.fullId }) { playlist ->
            MusicProfilePlaylistCard(playlist, Modifier.width(136.dp), onOpenPlaylist)
        }
    }
}

@Composable
internal fun MusicProfilePlaylistPair(playlists: List<AudioPlaylist>, onOpenPlaylist: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        playlists.forEach { MusicProfilePlaylistCard(it, Modifier.weight(1f), onOpenPlaylist) }
        if (playlists.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MusicProfilePlaylistCard(playlist: AudioPlaylist, modifier: Modifier, onOpenPlaylist: (String) -> Unit) {
    val colors = LiquidTheme.colors
    Column(modifier.clip(RoundedCornerShape(14.dp)).liquidClickable { onOpenPlaylist(playlist.fullId) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(colors.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(LmgGlyphs.MusicNote24, null, tint = colors.iconMuted, modifier = Modifier.size(32.dp))
            AsyncImage(playlist.photo?.bestUrl ?: playlist.thumbs?.maxByOrNull { it.width * it.height }?.bestUrl,
                null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(playlist.title.ifBlank { stringResource(R.string.playlist_fallback) }, Modifier.padding(top = 7.dp),
            color = colors.textPrimary, fontFamily = VkSansText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(pluralStringResource(R.plurals.track_count, playlist.count, playlist.count), Modifier.padding(top = 2.dp),
            color = colors.textSecondary, fontFamily = VkSansText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun MusicProfileRecentHistory(onOpenAll: () -> Unit) {
    val viewModel: VkHistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "profile-listening-history")
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    var actionsTrack by remember { mutableStateOf<Track?>(null) }
    Column {
        MusicProfileSection(stringResource(R.string.profile_recently_listened), stringResource(R.string.profile_all), onOpenAll)
        when {
            state.isLoading -> Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = colors.accent)
            }
            state.error != null -> Text(state.error.orEmpty(), Modifier.padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 12.dp),
                color = colors.textSecondary, fontFamily = VkSansText, fontSize = 12.sp)
            state.tracks.isEmpty() -> Text(stringResource(R.string.history_empty), Modifier.padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 12.dp),
                color = colors.textSecondary, fontFamily = VkSansText, fontSize = 12.sp)
            else -> state.tracks.take(2).forEach { track ->
                MusicPreviewRow(track.title, track.artist, track.coverUrl, lmgVector(LmgDrawables.MoreVertical24),
                    onClick = {
                        val tracks = state.tracks.filter { it.isAvailable }
                        val index = tracks.indexOfFirst { it.id == track.id }
                        if (index >= 0) PlayerController.play(context, tracks, index)
                    },
                    onAction = { actionsTrack = track },
                )
            }
        }
    }
    actionsTrack?.let { TrackActionsSheet(track = it, onDismiss = { actionsTrack = null }) }
}

@Composable
internal fun MusicProfileInfoRow(title: String, onClick: () -> Unit, icon: ImageVector = LmgGlyphs.InfoCircleOutline28) {
    val colors = LiquidTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 6.dp)
        .clip(RoundedCornerShape(20.dp)).background(LiquidSurfaces.card(colors.isDark)).liquidClickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = colors.iconMuted)
        Text(title, Modifier.weight(1f), color = colors.textPrimary, fontFamily = VkSansText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Icon(LmgGlyphs.ChevronRightOutline24, null, Modifier.size(20.dp), tint = colors.iconMuted)
    }
}

internal data class MusicProfileFact(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val onClick: (() -> Unit)? = null,
)

@Composable
internal fun MusicProfileInformation(rows: List<MusicProfileFact>) {
    if (rows.isEmpty()) return
    val colors = LiquidTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 6.dp)
        .clip(RoundedCornerShape(20.dp)).background(LiquidSurfaces.card(colors.isDark))) {
        rows.forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().padding(start = 54.dp, end = 16.dp).height(1.dp)
                .background(colors.textPrimary.copy(alpha = 0.06f)))
            Row(Modifier.fillMaxWidth().then(row.onClick?.let { Modifier.liquidClickable(onClick = it) } ?: Modifier)
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(row.icon, null, tint = colors.iconMuted, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.title, color = colors.textSecondary, fontFamily = VkSansText, fontSize = 12.sp)
                    Text(row.value, color = colors.textPrimary, fontFamily = VkSansText, fontSize = 15.sp,
                        fontWeight = FontWeight.Medium)
                }
                if (row.onClick != null) Icon(LmgGlyphs.ChevronRightOutline24, null, tint = colors.iconMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}
