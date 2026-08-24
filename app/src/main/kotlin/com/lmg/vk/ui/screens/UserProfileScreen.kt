package com.lmg.vk.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.lmg.vk.ui.glass.GlassCustomDialog
import com.lmg.vk.ui.glass.GlassDialog
import com.lmg.vk.ui.glass.GlassDialogButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lmg.vk.R
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.ProfileImageKind
import com.lmg.vk.engine.backend.MusicBackend
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.network.dto.VkAccountProfile
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.network.dto.music.coverUrl
import com.lmg.vk.ui.components.DetailTopBar
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidMetrics
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText
import com.lmg.vk.ui.viewmodel.UserProfileViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Public VK user profile based on the official client's `users.get` model.
 * The visual language remains LMG VK; no wall, messaging or synthetic data is
 * copied into the music client.
 */
@Composable
fun UserProfileScreen(
    userId: Long,
    onBack: () -> Unit,
    onOpenMusic: (Long) -> Unit,
    onOpenConnections: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenDetails: () -> Unit,
    viewModel: UserProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val state by viewModel.state.collectAsState()
    val activeAccountId by com.lmg.vk.engine.backend.MusicAuth.profileId.collectAsState()
    val listState = rememberLazyListState()
    val compact = com.lmg.vk.ui.rememberWindowInfo().useSideBySide
    var showRemoveFriendConfirm by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var editStatus by remember { mutableStateOf("") }
    var editAbout by remember { mutableStateOf("") }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadOwnProfileImage(context, it, ProfileImageKind.AVATAR) }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadOwnProfileImage(context, it, ProfileImageKind.COVER) }
    }

    LaunchedEffect(userId, activeAccountId) {
        viewModel.load(userId, force = true)
    }

    val showTopTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(LiquidSurfaces.sheet(colors.isDark))) {
        when {
            state.isLoading && state.profile == null -> UserProfileLoading()
            state.notFound -> UserProfileMessage(
                title = stringResource(R.string.profile_not_found),
                message = stringResource(R.string.profile_not_found_message, userId),
            )
            state.error != null && state.profile == null -> UserProfileMessage(
                title = stringResource(R.string.profile_open_failed),
                message = state.error!!,
                actionLabel = stringResource(R.string.action_retry),
                onAction = { viewModel.load(userId, force = true) },
            )
            state.profile != null -> {
                val profile = state.profile!!
                val profileUrl = "https://vk.com/${profile.addressSlug.ifBlank { "id$userId" }}"
                val friendLabel = when {
                    state.isOwnProfile && state.isSavingProfile -> stringResource(R.string.status_saving_short)
                    state.isOwnProfile -> stringResource(R.string.edit_profile)
                    state.isFriendActionLoading -> stringResource(R.string.working_ellipsis)
                    profile.friendStatus == 1 -> stringResource(R.string.friend_request_sent)
                    profile.friendStatus == 2 -> stringResource(R.string.accept_request)
                    profile.friendStatus == 3 || profile.isFriend == 1 -> stringResource(R.string.friends_title)
                    else -> stringResource(R.string.add_friend)
                }
                val friendIcon = when {
                    state.isOwnProfile -> lmgVector(LmgDrawables.UserPenOutline28)
                    profile.friendStatus == 1 -> lmgVector(LmgDrawables.UserMinusOutline28)
                    profile.friendStatus == 2 || profile.friendStatus == 3 || profile.isFriend == 1 ->
                        lmgVector(LmgDrawables.UserAddedOutline28)
                    else -> lmgVector(LmgDrawables.UserAddOutline28)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(bottom = 140.dp),
                ) {
                    item {
                        UserProfileHeader(
                            profile = profile,
                            compact = compact,
                            canOpenMusic = profile.isAccessible && profile.isAudioVisible &&
                                profile.deactivated.isNullOrBlank(),
                            friendshipLabel = friendLabel,
                            friendshipIcon = friendIcon,
                            friendshipEnabled = !state.isFriendActionLoading &&
                                !state.isSavingProfile && profile.deactivated.isNullOrBlank() &&
                                (state.isOwnProfile || profile.friendStatus != 0 ||
                                    profile.canSendFriendRequest != 0),
                            onOpenMusic = { onOpenMusic(profile.id) },
                            onOpenDetails = onOpenDetails,
                            onFriendship = {
                                if (state.isOwnProfile) {
                                    editStatus = profile.status
                                    editAbout = profile.about.orEmpty()
                                    showEditProfile = true
                                } else if (profile.friendStatus == 3 || profile.isFriend == 1) {
                                    showRemoveFriendConfirm = true
                                } else {
                                    viewModel.changeFriendship()
                                }
                            },
                            onShare = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, profileUrl)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_vk_profile)))
                                }
                            },
                        )
                    }

                    profile.deactivated?.takeIf(String::isNotBlank)?.let { reason ->
                        item {
                            UserProfileNotice(
                                if (reason == "banned") stringResource(R.string.profile_blocked) else stringResource(R.string.profile_deleted),
                            )
                        }
                    }
                    if (!profile.isAccessible && profile.deactivated.isNullOrBlank()) {
                        item { UserProfileNotice(stringResource(R.string.profile_private_notice)) }
                    }
                    state.friendActionError?.let { error ->
                        item { UserProfileNotice(error) }
                    }
                    state.saveProfileError?.let { error ->
                        item { UserProfileNotice(error) }
                    }
                    state.imageUploadError?.let { error ->
                        item { UserProfileNotice(error) }
                    }

                    val friendPreview = profile.friendsBlock?.friends.orEmpty()
                    val friendCount = profile.counters?.friends ?: friendPreview.size
                    if (friendCount > 0 || friendPreview.isNotEmpty()) {
                        item {
                            CompactFriendsCard(
                                count = friendCount,
                                mutualCount = profile.commonCount ?: 0,
                                friends = friendPreview,
                                onOpenAll = { onOpenConnections("friends") },
                            )
                        }
                    }

                    profile.actualStatusAudio?.let { statusAudio ->
                        item { UserProfileSectionTitle(stringResource(R.string.section_status_track)) }
                        item {
                            MusicPreviewRow(
                                title = statusAudio.title,
                                subtitle = statusAudio.artist,
                                imageUrl = statusAudio.album?.thumb?.bestUrl
                                    ?: statusAudio.thumb?.bestUrl,
                                icon = LmgGlyphs.Play28,
                                onClick = {
                                    val tracks = MusicBackend.adoptAudioDtos(listOf(statusAudio))
                                        .map { it.toTrack() }
                                        .filter { it.isAvailable }
                                    if (tracks.isNotEmpty()) PlayerController.play(context, tracks, 0)
                                },
                            )
                        }
                    }

                    if (state.isMusicPreviewLoading || state.musicTracks.isNotEmpty() ||
                        state.musicPlaylists.isNotEmpty()
                    ) {
                        item { UserProfileSectionTitle(stringResource(R.string.section_music)) }
                        if (state.isMusicPreviewLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = colors.iconMuted, modifier = Modifier.size(24.dp))
                                }
                            }
                        } else {
                            state.musicTracks.forEach { track ->
                                item(key = "profile-track:${track.fullId}") {
                                    MusicPreviewRow(
                                        title = track.title,
                                        subtitle = track.artist,
                                        imageUrl = track.coverUrl(),
                                        icon = LmgGlyphs.Play28,
                                        onClick = {
                                            val tracks = MusicBackend.adoptTracks(state.musicTracks)
                                                .map { it.toTrack() }
                                                .filter { it.isAvailable }
                                            val selectedId = track.fullId
                                            val selected = tracks.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                                            if (tracks.isNotEmpty()) PlayerController.play(context, tracks, selected)
                                        },
                                    )
                                }
                            }
                            state.musicPlaylists.forEach { playlist ->
                                item(key = "profile-playlist:${playlist.fullId}") {
                                    MusicPreviewRow(
                                        title = playlist.title.ifBlank { stringResource(R.string.playlist_fallback) },
                                        subtitle = stringResource(R.plurals.track_count, playlist.count),
                                        imageUrl = playlistPreviewUrl(playlist),
                                        icon = LmgGlyphs.ChevronRightOutline24,
                                        onClick = { onOpenPlaylist(playlist.fullId) },
                                    )
                                }
                            }
                            if (state.musicTotal > state.musicTracks.size ||
                                state.playlistTotal > state.musicPlaylists.size
                            ) {
                                item {
                                    UserProfileLinkRow(
                                        title = stringResource(R.string.all_music),
                                        value = stringResource(
                                            R.string.tracks_playlists_summary,
                                            formatProfileCount(state.musicTotal),
                                            formatProfileCount(state.playlistTotal),
                                        ),
                                        onClick = { onOpenMusic(profile.id) },
                                    )
                                }
                            }
                        }
                    }
                    state.musicPreviewError?.let { error ->
                        item { UserProfileNotice(error) }
                    }
                }
            }
        }

        DetailTopBar(
            title = state.profile?.displayName.orEmpty(),
            showTitle = showTopTitle,
            isDark = colors.isDark,
            onBack = onBack,
        )
    }

    if (showRemoveFriendConfirm) {
        GlassDialog(
            visible = showRemoveFriendConfirm,
            onDismiss = { showRemoveFriendConfirm = false },
            icon = lmgVector(LmgDrawables.UserOutline28),
            iconTint = Color(0xFFFC3C44),
            title = stringResource(R.string.remove_friend_question),
            message = stringResource(R.string.remove_friend_message),
            primaryButton = GlassDialogButton(
                text = stringResource(R.string.action_remove),
                backgroundColor = Color(0xFFFC3C44),
                onClick = {
                    showRemoveFriendConfirm = false
                    viewModel.changeFriendship()
                },
            ),
            secondaryButton = GlassDialogButton(
                text = stringResource(R.string.action_cancel),
                onClick = { showRemoveFriendConfirm = false },
            ),
        )
    }

    if (showEditProfile) {
        val colors = LiquidTheme.colors
        val isDark = colors.isDark

        GlassCustomDialog(
            visible = showEditProfile,
            onDismiss = {
                if (!state.isSavingProfile && !state.isUploadingImage) showEditProfile = false
            },
            icon = lmgVector(LmgDrawables.EditOutline28),
            iconTint = colors.accent,
            title = stringResource(R.string.edit_vk_profile),
            subtitle = stringResource(R.string.edit_profile_subtitle),
            dismissible = !state.isSavingProfile && !state.isUploadingImage,
            primaryButton = GlassDialogButton(
                text = stringResource(R.string.action_save),
                backgroundColor = colors.accent,
                enabled = !state.isSavingProfile && !state.isUploadingImage,
                onClick = {
                    viewModel.saveOwnProfile(editStatus.trim(), editAbout.trim())
                    showEditProfile = false
                },
            ),
            secondaryButton = GlassDialogButton(
                text = stringResource(R.string.action_cancel),
                enabled = !state.isSavingProfile && !state.isUploadingImage,
                onClick = { showEditProfile = false },
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = editStatus,
                    onValueChange = { editStatus = it },
                    label = { Text(stringResource(R.string.field_status), fontFamily = VkSansText) },
                    maxLines = 3,
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

                OutlinedTextField(
                    value = editAbout,
                    onValueChange = { editAbout = it },
                    label = { Text(stringResource(R.string.detail_about), fontFamily = VkSansText) },
                    minLines = 3,
                    maxLines = 7,
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF2F2F7))
                            .border(
                                1.dp,
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                RoundedCornerShape(16.dp),
                            )
                            .liquidClickable(
                                pressedScale = LiquidMotion.PressButton,
                                enabled = !state.isUploadingImage,
                                onClick = { avatarPicker.launch("image/*") },
                            )
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = lmgVector(LmgDrawables.PictureOutline28),
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.change_photo),
                                fontFamily = VkSansText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF2F2F7))
                            .border(
                                1.dp,
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                                RoundedCornerShape(16.dp),
                            )
                            .liquidClickable(
                                pressedScale = LiquidMotion.PressButton,
                                enabled = !state.isUploadingImage,
                                onClick = { coverPicker.launch("image/*") },
                            )
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = lmgVector(LmgDrawables.PictureStackOutline28),
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.change_cover),
                                fontFamily = VkSansText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                            )
                        }
                    }
                }

                if (state.isUploadingImage) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.uploading_image),
                            fontFamily = VkSansText,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.cover_format_hint),
                    color = colors.textTertiary,
                    fontFamily = VkSansText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

/** Separate information sheet matching the original VK profile structure. */
@Composable
fun UserProfileDetailsScreen(
    userId: Long,
    onBack: () -> Unit,
    onOpenConnections: (String) -> Unit,
    viewModel: UserProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val state by viewModel.state.collectAsState()
    val activeAccountId by com.lmg.vk.engine.backend.MusicAuth.profileId.collectAsState()
    LaunchedEffect(userId, activeAccountId) {
        viewModel.load(userId, force = true, loadMusic = false)
    }

    Box(Modifier.fillMaxSize().background(LiquidSurfaces.sheet(colors.isDark))) {
        when {
            state.isLoading && state.profile == null -> UserProfileLoading()
            state.notFound -> UserProfileMessage(
                title = stringResource(R.string.profile_not_found),
                message = stringResource(R.string.profile_not_found_message, userId),
            )
            state.error != null && state.profile == null -> UserProfileMessage(
                title = stringResource(R.string.info_open_failed),
                message = state.error!!,
                actionLabel = stringResource(R.string.action_retry),
                onAction = { viewModel.load(userId, force = true, loadMusic = false) },
            )
            state.profile != null -> {
                val profile = state.profile!!
                val facts = remember(profile) { profileFacts(context, profile) }
                val details = remember(profile) { profileDetails(context, profile) }
                val profileUrl = "https://vk.com/${profile.addressSlug.ifBlank { "id$userId" }}"
                LazyColumn(
                    modifier = Modifier.fillMaxSize().widthIn(max = 640.dp).align(Alignment.TopCenter),
                    contentPadding = PaddingValues(top = 68.dp, bottom = 120.dp),
                ) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 12.dp)) {
                            profile.status.takeIf(String::isNotBlank)?.let {
                                Text(it, fontFamily = VkSansText, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                "@${profile.addressSlug.ifBlank { "id${profile.id}" }}",
                                fontFamily = VkSansText,
                                color = Color(0xFF2787F5),
                                fontSize = 14.sp,
                            )
                        }
                    }
                    if (facts.isNotEmpty()) {
                        item { UserProfileSectionTitle(stringResource(R.string.section_profile)) }
                        item { UserProfileFacts(facts) }
                    }
                    item { UserProfileSectionTitle(stringResource(R.string.section_social)) }
                    item {
                        UserProfileLinkRow(
                            title = stringResource(R.string.friends_title),
                            value = formatProfileCount(profile.counters?.friends ?: profile.friendsBlock?.friends?.size ?: 0),
                            onClick = { onOpenConnections("friends") },
                        )
                    }
                    profile.commonCount?.takeIf { it > 0 }?.let { count ->
                        item {
                            UserProfileLinkRow(
                                title = stringResource(R.string.mutual_friends),
                                value = formatProfileCount(count),
                                onClick = { onOpenConnections("mutual") },
                            )
                        }
                    }
                    item {
                        UserProfileLinkRow(
                            title = stringResource(R.string.followers_title),
                            value = profile.followersCount?.let(::formatProfileCount) ?: stringResource(R.string.view_list),
                            onClick = { onOpenConnections("followers") },
                        )
                    }
                    item {
                        UserProfileLinkRow(
                            title = stringResource(R.string.subscriptions_title),
                            value = stringResource(R.string.people_and_communities),
                            onClick = { onOpenConnections("subscriptions") },
                        )
                    }
                    if (details.isNotEmpty()) {
                        item { UserProfileSectionTitle(stringResource(R.string.information)) }
                        item { UserProfileDetails(details) }
                    }
                    val serverActions = profile.profileButtons.flatten().filter { button ->
                        button.text.isNotBlank() && isSupportedProfileActionUrl(button.action.url)
                    }
                    if (serverActions.isNotEmpty()) {
                        item { UserProfileSectionTitle(stringResource(R.string.section_actions)) }
                        serverActions.forEach { button ->
                            item(key = "details-action:${button.uid}:${button.text}") {
                                UserProfileLinkRow(
                                    title = button.text,
                                    value = button.action.type.orEmpty(),
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(button.action.url.orEmpty())),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    item { UserProfileSectionTitle(stringResource(R.string.links)) }
                    item {
                        UserProfileLinkRow(
                            title = stringResource(R.string.open_in_vk),
                            value = profileUrl.removePrefix("https://"),
                            onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl))) }
                            },
                        )
                    }
                    profile.site?.takeIf(String::isNotBlank)?.let { site ->
                        item {
                            UserProfileLinkRow(
                                title = stringResource(R.string.website),
                                value = site,
                                onClick = {
                                    val normalized = if (site.startsWith("http")) site else "https://$site"
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        DetailTopBar(
            title = stringResource(R.string.more_information),
            showTitle = true,
            isDark = colors.isDark,
            onBack = onBack,
        )
    }
}

private data class ProfileFact(val label: String, val value: String)
private data class ProfileDetail(val label: String, val value: String)

private fun profileFacts(context: Context, profile: VkAccountProfile): List<ProfileFact> = buildList {
    profile.locationLabel.takeIf(String::isNotBlank)?.let { add(ProfileFact(context.getString(R.string.fact_location), it)) }
    profile.homeTown?.takeIf(String::isNotBlank)?.let { add(ProfileFact(context.getString(R.string.fact_hometown), it)) }
    profile.bdate.takeIf(String::isNotBlank)?.let { add(ProfileFact(context.getString(R.string.fact_birthday), it)) }
    profile.occupation?.name?.takeIf(String::isNotBlank)?.let { add(ProfileFact(context.getString(R.string.fact_occupation), it)) }
    profile.imageStatus?.name?.takeIf(String::isNotBlank)?.let { add(ProfileFact(context.getString(R.string.fact_image_status), it)) }
}

private fun profileDetails(context: Context, profile: VkAccountProfile): List<ProfileDetail> = buildList {
    val c = context
    profile.description?.takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_description), it)) }
    profile.descriptions.filter(String::isNotBlank).forEach { add(ProfileDetail(c.getString(R.string.detail_profile), it)) }
    profile.about?.takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_about), it)) }
    profile.activities?.takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_activities), it)) }
    profile.interests?.takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_interests), it)) }
    profile.music?.takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_favorite_music), it)) }
    profile.career.forEach { career ->
        val work = listOfNotNull(
            career.position?.takeIf(String::isNotBlank),
            career.company?.takeIf(String::isNotBlank),
        ).joinToString(" ${c.getString(R.string.career_at_join)} ")
        val extra = listOfNotNull(
            career.cityName?.takeIf(String::isNotBlank),
            career.from?.let { from -> career.until?.let { c.getString(R.string.career_years, from, it.toString()) } ?: c.getString(R.string.career_present, from) },
        ).joinToString(" · ")
        listOf(work, extra).filter(String::isNotBlank).joinToString("\n")
            .takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_career), it)) }
    }
    profile.universities.forEach { university ->
        val value = listOfNotNull(
            university.name?.takeIf(String::isNotBlank),
            university.facultyName?.takeIf(String::isNotBlank),
            university.chairName?.takeIf(String::isNotBlank),
            university.graduation?.takeIf { it > 0 }?.toString(),
        ).joinToString(" · ")
        value.takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_university), it)) }
    }
    profile.schools.forEach { school ->
        val value = listOfNotNull(
            school.name?.takeIf(String::isNotBlank),
            school.speciality?.takeIf(String::isNotBlank),
            school.yearGraduated?.takeIf { it > 0 }?.toString(),
        ).joinToString(" · ")
        value.takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_school), it)) }
    }
    profile.relation?.takeIf { it in 1..8 }?.let { relation ->
        val label = relationLabel(c, relation)
        val partner = profile.relationPartner?.displayName?.takeIf(String::isNotBlank)
        add(ProfileDetail(c.getString(R.string.detail_relationship), listOfNotNull(label, partner).joinToString(" · ")))
    }
    profile.relatives.forEach { relative ->
        relative.name?.takeIf(String::isNotBlank)?.let {
            add(ProfileDetail(relative.type.replaceFirstChar { char -> char.uppercase() }, it))
        }
    }
    profile.personal?.langs?.takeIf { it.isNotEmpty() }?.let {
        add(ProfileDetail(c.getString(R.string.detail_languages), it.joinToString(", ")))
    }
    profile.personal?.religion?.takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_worldview), it)) }
    profile.personal?.lifeMain?.let { lifePriorityLabel(c, it) }?.takeIf(String::isNotBlank)?.let {
        add(ProfileDetail(c.getString(R.string.detail_life_main), it))
    }
    profile.personal?.peopleMain?.let { peoplePriorityLabel(c, it) }?.takeIf(String::isNotBlank)?.let {
        add(ProfileDetail(c.getString(R.string.detail_people_main), it))
    }
    profile.personal?.smoking?.let { habitLabel(c, it) }?.takeIf(String::isNotBlank)?.let {
        add(ProfileDetail(c.getString(R.string.detail_smoking), it))
    }
    profile.personal?.alcohol?.let { habitLabel(c, it) }?.takeIf(String::isNotBlank)?.let {
        add(ProfileDetail(c.getString(R.string.detail_alcohol), it))
    }
    profile.mobilePhone?.takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_mobile_phone), it)) }
    profile.homePhone?.takeIf(String::isNotBlank)?.let { add(ProfileDetail(c.getString(R.string.detail_home_phone), it)) }
    profile.skype?.takeIf(String::isNotBlank)?.let { add(ProfileDetail("Skype", it)) }
}

@Composable
private fun UserProfileHeader(
    profile: VkAccountProfile,
    compact: Boolean,
    canOpenMusic: Boolean,
    friendshipLabel: String?,
    friendshipIcon: ImageVector,
    friendshipEnabled: Boolean,
    onOpenMusic: () -> Unit,
    onOpenDetails: () -> Unit,
    onFriendship: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val coverPhoto = profile.coverUrl
    val avatarPhoto = profile.animatedAvatarUrl ?: profile.largePhotoUrl.takeIf(String::isNotBlank)
    val presence = profilePresence(context, profile)
    val bannerHeight = if (coverPhoto != null) 132.dp else 70.dp
    val avatarSize = if (compact) 82.dp else 92.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LiquidSurfaces.sheet(colors.isDark)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(bannerHeight + avatarSize / 2)) {
            Box(modifier = Modifier.fillMaxWidth().height(bannerHeight).clipToBounds()) {
                if (coverPhoto != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(coverPhoto).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.38f)),
                            ),
                        ),
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                listOf(
                                    colors.textTertiary.copy(alpha = 0.12f),
                                    colors.textTertiary.copy(alpha = 0.04f),
                                ),
                            ),
                        ),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(colors.textTertiary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarPhoto != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(avatarPhoto).crossfade(true).build(),
                        contentDescription = profile.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(LmgGlyphs.UserOutline28, null, tint = colors.iconMuted, modifier = Modifier.size(36.dp))
                }
                if (profile.isOnline) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4BB34B)),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.displayName.ifBlank { "id${profile.id}" },
                    color = colors.textPrimary,
                    fontFamily = VkSansDisplay,
                    fontSize = if (compact) 22.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.isVerified) {
                    Spacer(Modifier.width(7.dp))
                    Icon(
                        lmgVector(LmgDrawables.CheckCircleOutline28),
                        contentDescription = stringResource(R.string.verified_badge),
                        tint = Color(0xFF2787F5),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            profile.status.takeIf(String::isNotBlank)?.let { status ->
                Spacer(Modifier.height(7.dp))
                Text(
                    text = status,
                    color = colors.textPrimary,
                    fontFamily = VkSansText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val subtitle = listOfNotNull(
                profile.addressSlug.takeIf(String::isNotBlank)?.let { "@$it" },
                presence,
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    subtitle,
                    color = colors.textSecondary,
                    fontFamily = VkSansText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .liquidClickable(onClick = onOpenDetails)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LmgGlyphs.InfoCircleOutline28, null, tint = colors.iconMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.more_information), color = colors.textSecondary, fontFamily = VkSansText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LiquidMetrics.ScreenPadding)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserProfileActionButton(
                label = if (profile.canSeeAudio == 0) stringResource(R.string.music_closed_label) else stringResource(R.string.music_label),
                icon = if (profile.canSeeAudio == 0) LmgGlyphs.LockOutline28 else LmgGlyphs.MusicNote24,
                enabled = canOpenMusic,
                filled = true,
                modifier = Modifier.weight(1f),
                onClick = onOpenMusic,
            )
            UserProfileActionButton(
                label = friendshipLabel ?: stringResource(R.string.action_share),
                icon = friendshipIcon.takeIf { friendshipLabel != null } ?: LmgGlyphs.ShareOutline28,
                enabled = if (friendshipLabel != null) friendshipEnabled else true,
                filled = false,
                modifier = Modifier.weight(1f),
                onClick = if (friendshipLabel != null) onFriendship else onShare,
            )
        }
    }
}

@Composable
private fun UserProfileActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    filled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val contentColor = when {
        !enabled -> colors.textTertiary
        filled -> Color.White
        else -> colors.textPrimary
    }
    Row(
        modifier = modifier
            .height(40.dp)
            .shadow(
                elevation = if (filled) LiquidMetrics.ButtonElevation else 2.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> colors.textTertiary.copy(alpha = 0.08f)
                    filled -> Color(0xFF2787F5)
                    else -> colors.textTertiary.copy(alpha = 0.12f)
                },
            )
            .liquidClickable(
                enabled = enabled,
                pressedScale = LiquidMotion.PressButton,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontFamily = VkSansText,
            color = contentColor,
            fontSize = LiquidMetrics.ActionLabel,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactFriendsCard(
    count: Int,
    mutualCount: Int,
    friends: List<com.lmg.vk.network.dto.VkFriend>,
    onOpenAll: () -> Unit,
) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.textTertiary.copy(alpha = 0.08f))
            .liquidClickable(onClick = onOpenAll)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${formatProfileCount(count)} friends",
                fontFamily = VkSansText,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (mutualCount > 0) "${formatProfileCount(mutualCount)} mutual"
                else stringResource(R.string.no_mutual_friends),
                fontFamily = VkSansText,
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            friends.take(3).forEach { friend ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.textTertiary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (friend.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(friend.avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = friend.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(LmgGlyphs.UserOutline28, null, tint = colors.iconMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Icon(
                LmgGlyphs.ChevronRightOutline24,
                null,
                tint = colors.textTertiary,
                modifier = Modifier.align(Alignment.CenterVertically).size(18.dp),
            )
        }
    }
}

@Composable
private fun UserProfileFacts(facts: List<ProfileFact>) {
    val colors = LiquidTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.textTertiary.copy(alpha = 0.08f))
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        facts.forEachIndexed { index, fact ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.textTertiary.copy(alpha = 0.12f)))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fact.label,
                    fontFamily = VkSansText,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = fact.value,
                    fontFamily = VkSansText,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

@Composable
private fun UserProfileDetails(details: List<ProfileDetail>) {
    val colors = LiquidTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.textTertiary.copy(alpha = 0.08f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        details.forEach { detail ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = detail.label.uppercase(),
                    fontFamily = VkSansText,
                    color = colors.sectionLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp,
                )
                Text(
                    text = detail.value,
                    fontFamily = VkSansText,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun UserProfileSectionTitle(text: String) {
    Text(
        text = text,
        fontFamily = VkSansText,
        color = LiquidTheme.colors.sectionLabel,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 8.dp),
    )
}

@Composable
private fun UserProfileLinkRow(title: String, value: String, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(onClick = onClick)
            .padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(LmgGlyphs.LinkOutline28, null, tint = colors.iconMuted, modifier = Modifier.size(21.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = VkSansText, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(value, fontFamily = VkSansText, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(LmgGlyphs.ChevronRightOutline24, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MusicPreviewRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(onClick = onClick)
            .padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.textTertiary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(LmgGlyphs.MusicNote24, null, tint = colors.iconMuted, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = VkSansText, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontFamily = VkSansText, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(icon, null, tint = colors.iconMuted, modifier = Modifier.size(19.dp))
    }
}

private fun playlistPreviewUrl(playlist: AudioPlaylist): String? =
    playlist.photo?.bestUrl
        ?: playlist.thumbs?.maxByOrNull { it.width * it.height }?.bestUrl

@Composable
private fun UserProfileNotice(text: String) {
    val colors = LiquidTheme.colors
    Text(
        text = text,
        fontFamily = VkSansText,
        color = colors.textSecondary,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
    )
}

@Composable
private fun UserProfileLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LiquidTheme.colors.iconMuted)
    }
}

@Composable
private fun UserProfileMessage(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = LiquidTheme.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(LmgGlyphs.UserOutline28, null, tint = colors.iconMuted, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, fontFamily = VkSansText, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, fontFamily = VkSansText, color = colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = actionLabel,
                fontFamily = VkSansText,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.textTertiary.copy(alpha = 0.14f))
                    .liquidClickable(onClick = onAction)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

private fun profilePresence(context: Context, profile: VkAccountProfile): String? {
    if (profile.onlineInfo?.visible == false) return null
    if (profile.isOnline) return context.getString(R.string.presence_online)
    val seconds = profile.onlineInfo?.lastSeen ?: profile.lastSeen?.time ?: return null
    if (seconds <= 0L) return null
    val elapsed = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(seconds)
    return when {
        elapsed < TimeUnit.MINUTES.toMillis(2) -> context.getString(R.string.presence_recently)
        elapsed < TimeUnit.HOURS.toMillis(1) -> context.getString(R.string.presence_minutes_ago, elapsed / TimeUnit.MINUTES.toMillis(1))
        elapsed < TimeUnit.DAYS.toMillis(1) -> context.getString(R.string.presence_hours_ago, elapsed / TimeUnit.HOURS.toMillis(1))
        else -> context.getString(R.string.presence_last_seen, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(TimeUnit.SECONDS.toMillis(seconds))))
    }
}

private fun relationLabel(context: Context, value: Int): String = when (value) {
    1 -> context.getString(R.string.relation_single)
    2 -> context.getString(R.string.relation_relationship)
    3 -> context.getString(R.string.relation_engaged)
    4 -> context.getString(R.string.relation_married)
    5 -> context.getString(R.string.relation_complicated)
    6 -> context.getString(R.string.relation_searching)
    7 -> context.getString(R.string.relation_in_love)
    8 -> context.getString(R.string.relation_civil_union)
    else -> ""
}

private fun lifePriorityLabel(context: Context, value: Int): String = when (value) {
    1 -> context.getString(R.string.life_family)
    2 -> context.getString(R.string.life_career)
    3 -> context.getString(R.string.life_leisure)
    4 -> context.getString(R.string.life_science)
    5 -> context.getString(R.string.life_world)
    6 -> context.getString(R.string.life_development)
    7 -> context.getString(R.string.life_art)
    8 -> context.getString(R.string.life_fame)
    else -> ""
}

private fun peoplePriorityLabel(context: Context, value: Int): String = when (value) {
    1 -> context.getString(R.string.people_intellect)
    2 -> context.getString(R.string.people_kindness)
    3 -> context.getString(R.string.people_health)
    4 -> context.getString(R.string.people_wealth)
    5 -> context.getString(R.string.people_courage)
    6 -> context.getString(R.string.people_humor)
    else -> ""
}

private fun habitLabel(context: Context, value: Int): String = when (value) {
    1 -> context.getString(R.string.habit_very_negative)
    2 -> context.getString(R.string.habit_negative)
    3 -> context.getString(R.string.habit_neutral)
    4 -> context.getString(R.string.habit_compromise)
    5 -> context.getString(R.string.habit_positive)
    else -> ""
}

private fun isSupportedProfileActionUrl(value: String?): Boolean {
    val scheme = value?.let(Uri::parse)?.scheme?.lowercase() ?: return false
    return scheme == "http" || scheme == "https" || scheme == "vk"
}

private fun formatProfileCount(value: Int): String = when {
    value >= 1_000_000 -> "%.1f млн".format(value / 1_000_000.0).replace(".0 млн", " млн")
    value >= 1_000 -> "%.1f тыс.".format(value / 1_000.0).replace(".0 тыс.", " тыс.")
    else -> value.toString()
}
