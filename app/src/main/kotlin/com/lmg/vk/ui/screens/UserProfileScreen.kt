package com.lmg.vk.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    viewModel: UserProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val state by viewModel.state.collectAsState()
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

    LaunchedEffect(userId) { viewModel.load(userId) }

    val showTopTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(LiquidSurfaces.sheet(colors.isDark))) {
        when {
            state.isLoading && state.profile == null -> UserProfileLoading()
            state.notFound -> UserProfileMessage(
                title = "Profile not found",
                message = "VK did not return a user with id $userId.",
            )
            state.error != null && state.profile == null -> UserProfileMessage(
                title = "Couldn't open profile",
                message = state.error!!,
                actionLabel = "Retry",
                onAction = { viewModel.load(userId, force = true) },
            )
            state.profile != null -> {
                val profile = state.profile!!
                val profileUrl = "https://vk.com/${profile.addressSlug.ifBlank { "id$userId" }}"
                val facts = remember(profile) { profileFacts(profile) }
                val details = remember(profile) { profileDetails(profile) }
                val friendLabel = when {
                    state.isOwnProfile && state.isSavingProfile -> "Saving..."
                    state.isOwnProfile -> "Edit profile"
                    state.isFriendActionLoading -> "Working..."
                    profile.friendStatus == 1 -> "Request sent"
                    profile.friendStatus == 2 -> "Accept request"
                    profile.friendStatus == 3 || profile.isFriend == 1 -> "Friends"
                    else -> "Add friend"
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
                                    context.startActivity(Intent.createChooser(intent, "Share VK profile"))
                                }
                            },
                        )
                    }

                    profile.deactivated?.takeIf(String::isNotBlank)?.let { reason ->
                        item {
                            UserProfileNotice(
                                if (reason == "banned") "This VK profile is blocked." else "This VK profile was deleted.",
                            )
                        }
                    }
                    if (!profile.isAccessible && profile.deactivated.isNullOrBlank()) {
                        item { UserProfileNotice("This profile is private. Only public fields are available.") }
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

                    if (facts.isNotEmpty()) {
                        item { UserProfileSectionTitle("PROFILE") }
                        item { UserProfileFacts(facts) }
                    }

                    item { UserProfileSectionTitle("SOCIAL") }
                    profile.commonCount?.takeIf { it > 0 }?.let { count ->
                        item {
                            UserProfileLinkRow(
                                title = "Mutual friends",
                                value = formatProfileCount(count),
                                onClick = { onOpenConnections("mutual") },
                            )
                        }
                    }
                    item {
                        UserProfileLinkRow(
                            title = "Followers",
                            value = profile.followersCount?.let(::formatProfileCount) ?: "View list",
                            onClick = { onOpenConnections("followers") },
                        )
                    }
                    item {
                        UserProfileLinkRow(
                            title = "Subscriptions",
                            value = "People and communities",
                            onClick = { onOpenConnections("subscriptions") },
                        )
                    }

                    profile.actualStatusAudio?.let { statusAudio ->
                        item { UserProfileSectionTitle("STATUS TRACK") }
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
                        item { UserProfileSectionTitle("MUSIC") }
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
                                        title = playlist.title.ifBlank { "Playlist" },
                                        subtitle = "${formatProfileCount(playlist.count)} tracks",
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
                                        title = "All music",
                                        value = "${formatProfileCount(state.musicTotal)} tracks · " +
                                            "${formatProfileCount(state.playlistTotal)} playlists",
                                        onClick = { onOpenMusic(profile.id) },
                                    )
                                }
                            }
                        }
                    }
                    state.musicPreviewError?.let { error ->
                        item { UserProfileNotice(error) }
                    }
                    if (details.isNotEmpty()) {
                        item { UserProfileSectionTitle("DETAILS") }
                        item { UserProfileDetails(details) }
                    }

                    val serverActions = profile.profileButtons.flatten().filter { button ->
                        button.text.isNotBlank() && isSupportedProfileActionUrl(button.action.url)
                    }
                    if (serverActions.isNotEmpty()) {
                        item { UserProfileSectionTitle("ACTIONS") }
                        serverActions.forEach { button ->
                            item(key = "profile-action:${button.uid}:${button.text}") {
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

                    item { UserProfileSectionTitle("LINKS") }
                    item {
                        UserProfileLinkRow(
                            title = "Share profile",
                            value = profileUrl.removePrefix("https://"),
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, profileUrl)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, "Share VK profile"))
                                }
                            },
                        )
                    }
                    item {
                        UserProfileLinkRow(
                            title = "Open in VK",
                            value = profileUrl.removePrefix("https://"),
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
                                }
                            },
                        )
                    }
                    profile.site?.takeIf(String::isNotBlank)?.let { site ->
                        item {
                            UserProfileLinkRow(
                                title = "Website",
                                value = site,
                                onClick = {
                                    val normalized = if (site.startsWith("http://") || site.startsWith("https://")) {
                                        site
                                    } else {
                                        "https://$site"
                                    }
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
            title = state.profile?.displayName.orEmpty(),
            showTitle = showTopTitle,
            isDark = colors.isDark,
            onBack = onBack,
        )
    }

    if (showRemoveFriendConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveFriendConfirm = false },
            title = { Text("Remove friend?") },
            text = { Text("The user will remain available in followers if VK keeps the subscription.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveFriendConfirm = false
                        viewModel.changeFriendship()
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveFriendConfirm = false }) { Text("Cancel") }
            },
        )
    }
    if (showEditProfile) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isSavingProfile && !state.isUploadingImage) showEditProfile = false
            },
            title = { Text("Edit VK profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editStatus,
                        onValueChange = { editStatus = it },
                        label = { Text("Status") },
                        maxLines = 3,
                    )
                    OutlinedTextField(
                        value = editAbout,
                        onValueChange = { editAbout = it },
                        label = { Text("About") },
                        minLines = 3,
                        maxLines = 7,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !state.isUploadingImage,
                            onClick = { avatarPicker.launch("image/*") },
                        ) { Text("Change photo") }
                        TextButton(
                            enabled = !state.isUploadingImage,
                            onClick = { coverPicker.launch("image/*") },
                        ) { Text("Change cover") }
                    }
                    if (state.isUploadingImage) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Uploading image...", fontSize = 12.sp)
                        }
                    }
                    Text(
                        "Cover images must already be prepared close to VK's 2.5:1 format.",
                        color = LiquidTheme.colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isSavingProfile && !state.isUploadingImage,
                    onClick = {
                        viewModel.saveOwnProfile(editStatus.trim(), editAbout.trim())
                        showEditProfile = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isSavingProfile && !state.isUploadingImage,
                    onClick = { showEditProfile = false },
                ) { Text("Cancel") }
            },
        )
    }
}

private data class ProfileFact(val label: String, val value: String)
private data class ProfileDetail(val label: String, val value: String)

private fun profileFacts(profile: VkAccountProfile): List<ProfileFact> = buildList {
    profile.locationLabel.takeIf(String::isNotBlank)?.let { add(ProfileFact("Location", it)) }
    profile.homeTown?.takeIf(String::isNotBlank)?.let { add(ProfileFact("Hometown", it)) }
    profile.bdate.takeIf(String::isNotBlank)?.let { add(ProfileFact("Birthday", it)) }
    profile.occupation?.name?.takeIf(String::isNotBlank)?.let { add(ProfileFact("Occupation", it)) }
    profile.imageStatus?.name?.takeIf(String::isNotBlank)?.let { add(ProfileFact("Image status", it)) }
    profile.followersCount?.let { add(ProfileFact("Followers", formatProfileCount(it))) }
    profile.commonCount?.takeIf { it > 0 }?.let { add(ProfileFact("Mutual friends", formatProfileCount(it))) }
    val connection = when (profile.friendStatus) {
        1 -> "Request sent"
        2 -> "Request received"
        3 -> "VK friend"
        else -> if (profile.isFriend == 1) "VK friend" else null
    }
    connection?.let { add(ProfileFact("Connection", it)) }
}

private fun profileDetails(profile: VkAccountProfile): List<ProfileDetail> = buildList {
    profile.description?.takeIf(String::isNotBlank)?.let { add(ProfileDetail("Description", it)) }
    profile.descriptions.filter(String::isNotBlank).forEach { add(ProfileDetail("Profile", it)) }
    profile.about?.takeIf(String::isNotBlank)?.let { add(ProfileDetail("About", it)) }
    profile.activities?.takeIf(String::isNotBlank)?.let { add(ProfileDetail("Activities", it)) }
    profile.interests?.takeIf(String::isNotBlank)?.let { add(ProfileDetail("Interests", it)) }
    profile.music?.takeIf(String::isNotBlank)?.let { add(ProfileDetail("Favorite music", it)) }
    profile.career.forEach { career ->
        val work = listOfNotNull(
            career.position?.takeIf(String::isNotBlank),
            career.company?.takeIf(String::isNotBlank),
        ).joinToString(" at ")
        val extra = listOfNotNull(
            career.cityName?.takeIf(String::isNotBlank),
            career.from?.let { from -> career.until?.let { "$from-$it" } ?: "$from-present" },
        ).joinToString(" · ")
        listOf(work, extra).filter(String::isNotBlank).joinToString("\n")
            .takeIf(String::isNotBlank)?.let { add(ProfileDetail("Career", it)) }
    }
    profile.universities.forEach { university ->
        val value = listOfNotNull(
            university.name?.takeIf(String::isNotBlank),
            university.facultyName?.takeIf(String::isNotBlank),
            university.chairName?.takeIf(String::isNotBlank),
            university.graduation?.takeIf { it > 0 }?.toString(),
        ).joinToString(" · ")
        value.takeIf(String::isNotBlank)?.let { add(ProfileDetail("University", it)) }
    }
    profile.schools.forEach { school ->
        val value = listOfNotNull(
            school.name?.takeIf(String::isNotBlank),
            school.speciality?.takeIf(String::isNotBlank),
            school.yearGraduated?.takeIf { it > 0 }?.toString(),
        ).joinToString(" · ")
        value.takeIf(String::isNotBlank)?.let { add(ProfileDetail("School", it)) }
    }
    profile.relation?.takeIf { it in 1..8 }?.let { relation ->
        val label = relationLabel(relation)
        val partner = profile.relationPartner?.displayName?.takeIf(String::isNotBlank)
        add(ProfileDetail("Relationship", listOfNotNull(label, partner).joinToString(" · ")))
    }
    profile.relatives.forEach { relative ->
        relative.name?.takeIf(String::isNotBlank)?.let {
            add(ProfileDetail(relative.type.replaceFirstChar { char -> char.uppercase() }, it))
        }
    }
    profile.personal?.langs?.takeIf { it.isNotEmpty() }?.let {
        add(ProfileDetail("Languages", it.joinToString(", ")))
    }
    profile.personal?.religion?.takeIf(String::isNotBlank)?.let { add(ProfileDetail("Worldview", it)) }
    profile.mobilePhone?.takeIf(String::isNotBlank)?.let { add(ProfileDetail("Mobile phone", it)) }
    profile.homePhone?.takeIf(String::isNotBlank)?.let { add(ProfileDetail("Home phone", it)) }
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
    onFriendship: () -> Unit,
    onShare: () -> Unit,
) {
    val isDark = LiquidTheme.colors.isDark
    val coverPhoto = profile.coverUrl
    val avatarPhoto = profile.animatedAvatarUrl ?: profile.largePhotoUrl.takeIf(String::isNotBlank)
    val photo = coverPhoto ?: avatarPhoto
    val presence = profilePresence(profile)

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(if (compact) 320.dp else 400.dp)) {
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                if (photo != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(photo).crossfade(true).build(),
                        contentDescription = profile.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF29292D)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            LmgGlyphs.UserOutline28,
                            null,
                            tint = Color.White.copy(alpha = 0.30f),
                            modifier = Modifier.size(96.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.30f),
                            0.35f to Color.Transparent,
                            0.62f to Color.Black.copy(alpha = 0.30f),
                            1f to Color.Black.copy(alpha = 0.90f),
                        ),
                    ),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = LiquidMetrics.ScreenPadding)
                    .padding(bottom = LiquidMetrics.SheetOverlap + 8.dp),
            ) {
                if (coverPhoto != null && avatarPhoto != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(avatarPhoto).crossfade(true).build(),
                        contentDescription = profile.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(if (compact) 76.dp else 92.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF29292D)),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName.ifBlank { "id${profile.id}" },
                        color = LiquidSurfaces.onHeaderPrimary,
                        fontFamily = VkSansDisplay,
                        fontSize = if (compact) 30.sp else 38.sp,
                        fontWeight = LiquidMetrics.TitleHugeWeight,
                        letterSpacing = LiquidMetrics.TitleHugeSpacing,
                        lineHeight = if (compact) 34.sp else 42.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (profile.isVerified) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            lmgVector(LmgDrawables.CheckCircleOutline28),
                            contentDescription = "Verified",
                            tint = Color(0xFF2787F5),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                val subtitle = listOfNotNull(
                    profile.addressSlug.takeIf(String::isNotBlank)?.let { "vk.com/$it" },
                    presence,
                ).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        color = LiquidSurfaces.onHeaderSecondary,
                        fontFamily = VkSansText,
                        fontSize = LiquidMetrics.HeaderCaption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                profile.status.takeIf(String::isNotBlank)?.let { status ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = status,
                        color = LiquidSurfaces.onHeaderSecondary,
                        fontFamily = VkSansText,
                        fontSize = 12.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UserProfileActionButton(
                        label = if (profile.canSeeAudio == 0) "Music closed" else "Music",
                        icon = if (profile.canSeeAudio == 0) LmgGlyphs.LockOutline28 else LmgGlyphs.MusicNote24,
                        enabled = canOpenMusic,
                        filled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenMusic,
                    )
                    if (friendshipLabel != null) {
                        UserProfileActionButton(
                            label = friendshipLabel,
                            icon = friendshipIcon,
                            enabled = friendshipEnabled,
                            filled = false,
                            modifier = Modifier.weight(1f),
                            onClick = onFriendship,
                        )
                    } else {
                        UserProfileActionButton(
                            label = "Share",
                            icon = LmgGlyphs.ShareOutline28,
                            enabled = true,
                            filled = false,
                            modifier = Modifier.weight(1f),
                            onClick = onShare,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(LiquidMetrics.SheetShape)
                .background(LiquidSurfaces.sheet(isDark))
                .padding(top = 12.dp, bottom = 4.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(LiquidSurfaces.grabber(isDark)),
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
    val contentColor = when {
        !enabled -> Color.White.copy(alpha = 0.45f)
        filled -> Color.Black
        else -> Color.White
    }
    Row(
        modifier = modifier
            .height(LiquidMetrics.ActionButtonHeight)
            .shadow(
                elevation = if (filled) LiquidMetrics.ButtonElevation else 2.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.White.copy(alpha = 0.10f)
                    filled -> Color.White
                    else -> LiquidSurfaces.glassAction
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 12.dp),
    )
}

@Composable
private fun UserProfileLinkRow(title: String, value: String, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(onClick = onClick)
            .padding(horizontal = LiquidMetrics.ScreenPadding, vertical = 13.dp),
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

private fun profilePresence(profile: VkAccountProfile): String? {
    if (profile.onlineInfo?.visible == false) return null
    if (profile.isOnline) return "Online"
    val seconds = profile.onlineInfo?.lastSeen ?: profile.lastSeen?.time ?: return null
    if (seconds <= 0L) return null
    val elapsed = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(seconds)
    return when {
        elapsed < TimeUnit.MINUTES.toMillis(2) -> "Recently online"
        elapsed < TimeUnit.HOURS.toMillis(1) -> "Online ${elapsed / TimeUnit.MINUTES.toMillis(1)} min ago"
        elapsed < TimeUnit.DAYS.toMillis(1) -> "Online ${elapsed / TimeUnit.HOURS.toMillis(1)} h ago"
        else -> "Last seen ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(TimeUnit.SECONDS.toMillis(seconds)))}"
    }
}

private fun relationLabel(value: Int): String = when (value) {
    1 -> "Single"
    2 -> "In a relationship"
    3 -> "Engaged"
    4 -> "Married"
    5 -> "It's complicated"
    6 -> "Actively searching"
    7 -> "In love"
    8 -> "In a civil union"
    else -> ""
}

private fun isSupportedProfileActionUrl(value: String?): Boolean {
    val scheme = value?.let(Uri::parse)?.scheme?.lowercase() ?: return false
    return scheme == "http" || scheme == "https" || scheme == "vk"
}

private fun formatProfileCount(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0).replace(".0M", "M")
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0).replace(".0K", "K")
    else -> value.toString()
}
