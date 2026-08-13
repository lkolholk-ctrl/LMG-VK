package com.lmg.vk.ui.screens

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.VkProfileRepository
import com.lmg.vk.network.dto.VkFriend
import com.lmg.vk.network.dto.VkGroup
import com.lmg.vk.network.dto.music.AudioPlaylist
import com.lmg.vk.ui.components.DetailTopBar
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.LiquidMetrics
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

private val DestructiveRed = Color(0xFFFC3C44)
private val OnlineGreen = Color(0xFF34C759)

/**
 * VK account screen. Everything on it comes from the VK API — profile fields
 * from `users.get`, friends from `friends.get`, communities from `groups.get`,
 * counts from `audio.get`/`audio.getPlaylists`. No local metrics are shown here.
 */
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenAuth: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenPlaylist: (String) -> Unit = {},
    onOpenUserProfile: (Long) -> Unit = {},
    onOpenGroup: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LiquidTheme.colors
    val isLoggedIn by MusicAuth.isLoggedIn.collectAsState()
    val profileId by MusicAuth.profileId.collectAsState()
    val fallbackName by MusicAuth.profileName.collectAsState()
    val fallbackAvatar by MusicAuth.avatarUrl.collectAsState()
    val fallbackDomain by MusicAuth.profileDomain.collectAsState()
    val vk by VkProfileRepository.state.collectAsState()
    val ownerAudio by VkProfileRepository.ownerAudio.collectAsState()

    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var friendsExpanded by remember { mutableStateOf(false) }
    var groupsExpanded by remember { mutableStateOf(false) }
    var playlistsExpanded by remember { mutableStateOf(false) }
    var onlineFriendsOnly by remember { mutableStateOf(false) }
    var friendsQuery by remember { mutableStateOf("") }
    var groupsQuery by remember { mutableStateOf("") }
    var playlistsQuery by remember { mutableStateOf("") }

    // Пока VK ID неизвестен, тянуть friends/groups/audio нечем: сначала
    // users.get заполнит id, обновление flow перезапустит эффект.
    LaunchedEffect(isLoggedIn, profileId) {
        if (!isLoggedIn) return@LaunchedEffect
        val id = profileId
        if (id == null || id == 0L) {
            MusicAuth.fetchUserData()
            return@LaunchedEffect
        }
        VkProfileRepository.refresh(id)
    }

    val profile = vk.profile
    val displayName = profile?.displayName?.takeIf(String::isNotBlank)
        ?: fallbackName?.takeIf(String::isNotBlank)
        ?: if (isLoggedIn) "VK account" else "Guest"
    val slug = profile?.addressSlug?.takeIf(String::isNotBlank)
        ?: fallbackDomain?.takeIf(String::isNotBlank)
    // Для шапки во всю ширину нужен ОРИГИНАЛ (crop_photo), а не превью:
    // photo_max_orig это 400px, и растянутый на ширину экрана он даёт мыло.
    // fallbackAvatar — превью из сессии, лучше мыло, чем пустая шапка.
    val avatarUrl = profile?.largePhotoUrl?.takeIf(String::isNotBlank) ?: fallbackAvatar
    val accountSubtitle = when {
        !slug.isNullOrBlank() -> "vk.com/$slug"
        profileId != null -> "VK ID $profileId"
        isLoggedIn -> "VK account"
        else -> "Sign in to restore your VK library"
    }
    val playlistPreview = remember(vk.playlists) { vk.playlists.take(12) }
    val onlineFriends = remember(vk.friends) {
        vk.friends.filter { it.isActive && it.isOnline }
    }
    val friendsForOverlay = remember(vk.friends, onlineFriendsOnly, friendsQuery) {
        vk.friends
            .asSequence()
            .filter { !onlineFriendsOnly || (it.isActive && it.isOnline) }
            .filter { matchesFriendQuery(it, friendsQuery) }
            .toList()
    }
    val groupsForOverlay = remember(vk.groups, groupsQuery) {
        vk.groups.filter { matchesGroupQuery(it, groupsQuery) }
    }
    val playlistsForOverlay = remember(vk.playlists, playlistsQuery) {
        vk.playlists.filter { matchesPlaylistQuery(it, playlistsQuery) }
    }
    val lastSeenLabel = remember(profile?.isOnline, profile?.onlineInfo, profile?.lastSeen) {
        formatPresenceLabel(
            isOnline = profile?.isOnline == true,
            onlineLastSeen = profile?.onlineInfo?.takeIf { it.visible }?.lastSeen,
            lastSeenTime = profile?.lastSeen?.time?.takeIf {
                profile.onlineInfo?.visible != false
            },
        )
    }
    val profileLink = remember(slug, profileId) {
        when {
            !slug.isNullOrBlank() -> "https://vk.com/$slug"
            profileId != null -> "https://vk.com/id$profileId"
            else -> null
        }
    }

    val window = com.lmg.vk.ui.rememberWindowInfo()
    val compact = window.useSideBySide

    // Подэкран чужих аудио перехватывает "назад" раньше, чем оверлей профиля.
    // «Назад» разбирает оверлеи по одному, сверху вниз: аудио владельца лежит
    // над списком, список — над профилем. Один общий обработчик с приоритетами,
    // а не три независимых: иначе они спорят за одно и то же нажатие.
    BackHandler(
        enabled = ownerAudio != null || friendsExpanded || groupsExpanded || playlistsExpanded,
    ) {
        when {
            ownerAudio != null -> VkProfileRepository.closeOwnerAudio()
            playlistsExpanded -> {
                playlistsExpanded = false
                playlistsQuery = ""
            }
            friendsExpanded -> {
                friendsExpanded = false
                onlineFriendsOnly = false
                friendsQuery = ""
            }
            groupsExpanded -> {
                groupsExpanded = false
                groupsQuery = ""
            }
        }
    }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("Sign out of VK?", fontFamily = VkSansDisplay, fontWeight = FontWeight.SemiBold) },
            text = { Text("Your encrypted local VK session will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirmation = false
                        MusicAuth.logout()
                        onLogout()
                    },
                ) { Text("Sign out", color = DestructiveRed) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    Box(
        // Фон — лист из общего словаря, как на экранах артиста и альбома.
        // Раньше здесь стоял settingsBackground: профиль выглядел разделом
        // настроек, а не таким же экраном, как остальные.
        modifier = Modifier.fillMaxSize().background(LiquidSurfaces.sheet(colors.isDark)),
    ) {
        LazyColumn(
            modifier = if (window.useSideBySide) {
                Modifier.fillMaxHeight().widthIn(max = 640.dp).align(Alignment.TopCenter)
            } else {
                Modifier.fillMaxSize()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Шапка идёт ПОД статус-бар, как у артиста: фотография должна доходить
            // до верхнего края экрана, иначе наезжающий лист теряет смысл.
            item {
                ProfileHeaderWithSheet(
                    avatarUrl = avatarUrl,
                    displayName = displayName,
                    subtitle = accountSubtitle,
                    status = profile?.status?.takeIf(String::isNotBlank),
                    presence = lastSeenLabel,
                    isVerified = profile?.isVerified == true,
                    isOnline = profile?.isOnline == true,
                    isDark = colors.isDark,
                    compact = compact,
                    onOpenLibrary = onOpenLibrary,
                    onOpenSettings = onOpenSettings,
                )
            }

            if (isLoggedIn) {
                vk.error?.let { message ->
                    item {
                        ProfileCard {
                            ProfileNoticeRow(message)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                // Факты аккаунта плитками 2-в-ряд: строками они занимали пять
                // экранных полос ради нескольких слов на каждой.
                //
                // «VK session» убрана совсем: срок жизни токена — наша
                // внутренняя механика, пользователю знать про рефреш незачем.
                item {
                    val facts = buildList {
                        add(com.lmg.vk.ui.icons.LmgGlyphs.UserOutline28 to ("VK ID" to
                            ((profile?.id ?: profileId)?.toString() ?: "…")))
                        if (!slug.isNullOrBlank()) {
                            add(com.lmg.vk.ui.icons.LmgGlyphs.LinkOutline28 to ("Address" to "vk.com/$slug"))
                        }
                        profile?.locationLabel?.takeIf(String::isNotBlank)?.let {
                            add(lmgVector(LmgDrawables.PlaceOutline28) to ("Location" to it))
                        }
                        profile?.bdate?.takeIf(String::isNotBlank)?.let {
                            add(com.lmg.vk.ui.icons.LmgGlyphs.CakeOutline28 to ("Birthday" to formatBirthday(it)))
                        }
                    }
                    ProfileFactGrid(facts = facts, compact = compact)
                }
                item { Spacer(Modifier.height(16.dp)) }

                item {
                    ProfileCard {
                        ProfileSectionLabel("MY MUSIC")
                        ProfileMetricsRow(
                            firstValue = vk.audioTotal.orDash(),
                            firstLabel = "Tracks in VK",
                            secondValue = (vk.playlistsTotal ?: vk.playlists.size.takeIf { it > 0 }).orDash(),
                            secondLabel = "Playlists",
                            compact = compact,
                            onFirstClick = {
                                val p = profile
                                if (p != null && p.id != 0L) {
                                    scope.launch { VkProfileRepository.openMyAudio(p) }
                                } else {
                                    onOpenLibrary()
                                }
                            },
                            onSecondClick = {
                                if (vk.playlists.isNotEmpty()) {
                                    playlistsExpanded = true
                                } else {
                                    onOpenLibrary()
                                }
                            },
                        )
                        vk.musicError?.let {
                            ProfileDivider()
                            ProfileNoticeRow(it)
                        }
                        if (playlistPreview.isNotEmpty()) {
                            ProfileDivider()
                            ProfilePlaylistPreviewRow(
                                playlists = playlistPreview,
                                compact = compact,
                                onOpenAll = { playlistsExpanded = true },
                                onPlaylistClick = { playlist ->
                                    onOpenPlaylist(playlist.fullId)
                                },
                            )
                        }
                        ProfileDivider()
                        ProfileNavigationRow(
                            icon = com.lmg.vk.ui.icons.LmgGlyphs.ListPlayOutline28,
                            label = "My tracks on VK",
                            value = when {
                                vk.audioTotal != null -> "${formatCount(vk.audioTotal!!)} tracks · open list"
                                else -> "Open your VK audio"
                            },
                            compact = compact,
                            onClick = {
                                val p = profile
                                if (p != null && p.id != 0L) {
                                    scope.launch { VkProfileRepository.openMyAudio(p) }
                                } else {
                                    onOpenLibrary()
                                }
                            },
                        )
                        ProfileDivider()
                        ProfileNavigationRow(
                            icon = com.lmg.vk.ui.icons.LmgGlyphs.ListOutline28,
                            label = "My Library",
                            value = "Favorites, playlists & downloads",
                            compact = compact,
                            onClick = onOpenLibrary,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }

                // SOCIAL: плитки кликабельные — тап открывает полный список.
                // Online-strip сверху — быстрый вход в профили друзей, которые
                // сейчас в сети (из уже загруженной первой страницы friends.get).
                item {
                    ProfileCard {
                        ProfileSectionLabel("SOCIAL")
                        if (onlineFriends.isNotEmpty()) {
                            ProfileOnlineFriendsStrip(
                                friends = onlineFriends,
                                compact = compact,
                                onOpenAll = {
                                    onlineFriendsOnly = true
                                    friendsExpanded = true
                                },
                                onFriendClick = { friend ->
                                    onOpenUserProfile(friend.id)
                                },
                            )
                            ProfileDivider()
                        }
                        ProfileMetricsRow(
                            firstValue = (vk.friendsTotal ?: vk.profile?.counters?.friends).orDash(),
                            firstLabel = "Friends",
                            secondValue = (vk.groupsTotal ?: vk.profile?.counters?.groups).orDash(),
                            secondLabel = "Communities",
                            compact = compact,
                            onFirstClick = {
                                onlineFriendsOnly = false
                                friendsExpanded = true
                            },
                            onSecondClick = { groupsExpanded = true },
                        )
                        ProfileDivider()
                        // Подписчики и подписки — счётчики без своего экрана:
                        // VK не даёт метода для их списков этому токену, и
                        // делать плитку кликабельной «в никуда» нельзя.
                        ProfileMetricsRow(
                            firstValue = (profile?.followersCount ?: profile?.counters?.followers).orDash(),
                            firstLabel = "Followers",
                            secondValue = (profile?.counters?.subscriptions).orDash(),
                            secondLabel = "Subscriptions",
                            compact = compact,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            item {
                ProfileCard {
                    if (isLoggedIn) {
                        ProfileNavigationRow(
                            icon = com.lmg.vk.ui.icons.LmgGlyphs.RefreshOutline28,
                            label = "Refresh profile",
                            value = if (vk.isRefreshing || vk.isLoading) {
                                "Updating…"
                            } else {
                                "Reload profile, friends & music"
                            },
                            compact = compact,
                            enabled = !vk.isRefreshing && !vk.isLoading,
                            loading = vk.isRefreshing || (vk.isLoading && profile != null),
                            onClick = {
                                val id = profile?.id ?: profileId
                                if (id != null && id != 0L) {
                                    scope.launch { VkProfileRepository.refresh(id) }
                                }
                            },
                        )
                        ProfileDivider()
                        val editableProfileId = profile?.id ?: profileId
                        if (editableProfileId != null && editableProfileId != 0L) {
                            ProfileNavigationRow(
                                icon = lmgVector(LmgDrawables.UserPenOutline28),
                                label = "Edit VK profile",
                                value = "Status, about and full profile",
                                compact = compact,
                                onClick = { onOpenUserProfile(editableProfileId) },
                            )
                            ProfileDivider()
                        }
                        if (profileLink != null) {
                            ProfileNavigationRow(
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.CopyOutline28,
                                label = "Copy VK profile link",
                                value = profileLink.removePrefix("https://"),
                                compact = compact,
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "vk_profile_link",
                                            profileLink,
                                        ),
                                    )
                                    android.widget.Toast.makeText(
                                        context,
                                        "VK profile link copied",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                            ProfileDivider()
                            ProfileNavigationRow(
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.ShareOutline28,
                                label = "Share profile",
                                value = "Send link via apps",
                                compact = compact,
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, profileLink)
                                    }
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(send, "Share VK profile"),
                                        )
                                    }
                                },
                            )
                            ProfileDivider()
                            ProfileNavigationRow(
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.ExternalLinkOutline24,
                                label = "Open in VK",
                                value = "Browser profile page",
                                compact = compact,
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(profileLink)),
                                        )
                                    }
                                },
                            )
                            ProfileDivider()
                        }
                        ProfileNavigationRow(
                            icon = com.lmg.vk.ui.icons.LmgGlyphs.GearOutline24,
                            label = "Settings",
                            value = "Playback, appearance & data",
                            compact = compact,
                            onClick = onOpenSettings,
                        )
                        ProfileDivider()
                        ProfileActionRow(
                            icon = lmgVector(LmgDrawables.DoorArrowLeftOutline28),
                            label = "Sign Out",
                            compact = compact,
                            onClick = { showSignOutConfirmation = true },
                        )
                    } else {
                        ProfileNavigationRow(
                            icon = com.lmg.vk.ui.icons.LmgGlyphs.UserOutline28,
                            label = "Sign In",
                            value = "Connect your VK account",
                            compact = compact,
                            onClick = onOpenAuth,
                        )
                        ProfileDivider()
                        ProfileNavigationRow(
                            icon = com.lmg.vk.ui.icons.LmgGlyphs.GearOutline24,
                            label = "Settings",
                            value = "Playback, appearance & data",
                            compact = compact,
                            onClick = onOpenSettings,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(if (compact) 20.dp else 32.dp)) }
            item {
                Text(
                    text = "LMG VK • ${com.lmg.vk.BuildConfig.VERSION_NAME}",
                    fontFamily = VkSansText,
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        // Списки друзей и сообществ — оверлеи по тапу на плитку SOCIAL.
        // Порядок важен: аудио владельца рисуется ПОСЛЕ них, потому что
        // открывается изнутри списка и должно лежать выше.
        if (friendsExpanded) {
            ProfileOwnerListOverlay(
                title = if (onlineFriendsOnly) "Online friends" else "Friends",
                total = when {
                    friendsQuery.isNotBlank() || onlineFriendsOnly -> friendsForOverlay.size
                    else -> vk.friendsTotal
                },
                isLoading = vk.isLoading && vk.friends.isEmpty(),
                error = vk.friendsError?.takeIf {
                    vk.friends.isEmpty() || friendsForOverlay.isEmpty()
                },
                emptyText = when {
                    friendsQuery.isNotBlank() -> "No friends match “$friendsQuery”"
                    onlineFriendsOnly -> "No online friends"
                    else -> "No friends returned by VK"
                },
                itemCount = friendsForOverlay.size,
                loadedCount = vk.friends.size,
                // Фильтр и поиск применяются к уже полученным элементам, а
                // оверлей продолжает брать страницы, пока не найдёт совпадения
                // или VK не сообщит конец списка.
                hasMore = vk.friendsError == null && vk.hasMoreFriends,
                onLoadMore = {
                    scope.launch { VkProfileRepository.loadMoreFriends() }
                },
                onBack = {
                    friendsExpanded = false
                    onlineFriendsOnly = false
                    friendsQuery = ""
                },
                compact = compact,
                searchQuery = friendsQuery,
                searchHint = "Search friends",
                onSearchQueryChange = { friendsQuery = it },
                headerTrailing = {
                    if (onlineFriends.isNotEmpty() || onlineFriendsOnly) {
                        ProfileFilterChip(
                            label = if (onlineFriendsOnly) {
                                "All friends"
                            } else {
                                "Online · ${onlineFriends.size}"
                            },
                            selected = onlineFriendsOnly,
                            onClick = { onlineFriendsOnly = !onlineFriendsOnly },
                        )
                    }
                },
            ) { index ->
                val friend = friendsForOverlay[index]
                FriendRow(
                    friend = friend,
                    compact = compact,
                    onClick = {
                        onOpenUserProfile(friend.id)
                    },
                )
            }
        }

        if (groupsExpanded) {
            ProfileOwnerListOverlay(
                title = "Communities",
                total = if (groupsQuery.isNotBlank()) groupsForOverlay.size else vk.groupsTotal,
                isLoading = vk.isLoading && vk.groups.isEmpty(),
                error = vk.groupsError?.takeIf {
                    vk.groups.isEmpty() || groupsForOverlay.isEmpty()
                },
                emptyText = if (groupsQuery.isNotBlank()) {
                    "No communities match “$groupsQuery”"
                } else {
                    "No communities returned by VK"
                },
                itemCount = groupsForOverlay.size,
                loadedCount = vk.groups.size,
                hasMore = vk.groupsError == null && vk.hasMoreGroups,
                onLoadMore = {
                    scope.launch { VkProfileRepository.loadMoreGroups() }
                },
                onBack = {
                    groupsExpanded = false
                    groupsQuery = ""
                },
                compact = compact,
                searchQuery = groupsQuery,
                searchHint = "Search communities",
                onSearchQueryChange = { groupsQuery = it },
            ) { index ->
                val group = groupsForOverlay[index]
                GroupRow(
                    group = group,
                    compact = compact,
                    onClick = {
                        onOpenGroup(group.audioOwnerId)
                    },
                )
            }
        }

        if (playlistsExpanded) {
            ProfileOwnerListOverlay(
                title = "Playlists",
                total = if (playlistsQuery.isNotBlank()) {
                    playlistsForOverlay.size
                } else {
                    vk.playlistsTotal ?: vk.playlists.size
                },
                isLoading = vk.isLoading && vk.playlists.isEmpty(),
                error = vk.playlistsError?.takeIf {
                    vk.playlists.isEmpty() || playlistsForOverlay.isEmpty()
                },
                emptyText = if (playlistsQuery.isNotBlank()) {
                    "No playlists match “$playlistsQuery”"
                } else {
                    "No playlists returned by VK"
                },
                itemCount = playlistsForOverlay.size,
                loadedCount = vk.playlists.size,
                hasMore = vk.playlistsError == null && vk.hasMorePlaylists,
                onLoadMore = {
                    scope.launch { VkProfileRepository.loadMorePlaylists() }
                },
                onBack = {
                    playlistsExpanded = false
                    playlistsQuery = ""
                },
                compact = compact,
                searchQuery = playlistsQuery,
                searchHint = "Search playlists",
                onSearchQueryChange = { playlistsQuery = it },
            ) { index ->
                val playlist = playlistsForOverlay[index]
                PlaylistRow(
                    playlist = playlist,
                    compact = compact,
                    onClick = {
                        playlistsExpanded = false
                        playlistsQuery = ""
                        onOpenPlaylist(playlist.fullId)
                    },
                )
            }
        }

        ownerAudio?.let { audioState ->
            OwnerAudioScreen(
                state = audioState,
                onBack = { VkProfileRepository.closeOwnerAudio() },
            )
        }
    }
}

// ------------------------------- строки списков -------------------------------

/**
 * Полный список друзей, сообществ или плейлистов — оверлеем поверх профиля.
 *
 * Раньше эти списки жили карточками НА профиле, показывая по 5 строк с кнопками
 * «Show all» / «Load more»: два экрана прокрутки, дублирующие числа с плиток
 * SOCIAL. Теперь профиль короткий, а список открывается по тапу на плитку.
 *
 * Шапка — общий [DetailTopBar], а не своя: он уже умеет статус-бар, кнопку
 * назад и подложку при прокрутке.
 *
 * [row] отдаёт строку по индексу, а не готовый список: у друзей и сообществ
 * разные DTO, и обобщать их одним типом пришлось бы дженериком ради двух
 * вызовов.
 */
@Composable
private fun ProfileOwnerListOverlay(
    title: String,
    total: Int?,
    isLoading: Boolean,
    error: String?,
    emptyText: String,
    itemCount: Int,
    loadedCount: Int = itemCount,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    compact: Boolean,
    searchQuery: String = "",
    searchHint: String = "Search",
    onSearchQueryChange: ((String) -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    row: @Composable (Int) -> Unit,
) {
    val colors = LiquidTheme.colors
    val isDark = colors.isDark
    val listState = rememberLazyListState()

    // Догрузка, когда до конца осталось меньше экрана — как в OwnerAudioScreen.
    LaunchedEffect(itemCount, loadedCount, hasMore) {
        if (itemCount == 0 && hasMore) {
            onLoadMore()
        }
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, count) ->
            if (hasMore && count > 0 && last >= count - 5) onLoadMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidSurfaces.sheet(isDark))
            // Свой обработчик касания: без него нажатия проходили бы сквозь
            // оверлей в список профиля под ним.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailTopBar(
                title = if (total != null) "$title • $total" else title,
                showTitle = true,
                isDark = isDark,
                onBack = onBack,
            )
            if (onSearchQueryChange != null || headerTrailing != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LiquidMetrics.ScreenPadding)
                        .padding(top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onSearchQueryChange != null) {
                        ProfileSearchField(
                            query = searchQuery,
                            hint = searchHint,
                            onQueryChange = onSearchQueryChange,
                        )
                    }
                    if (headerTrailing != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            headerTrailing()
                        }
                    }
                }
            }
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.iconMuted)
                }
                error != null -> ProfileOverlayMessage(error)
                itemCount == 0 && hasMore -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.iconMuted)
                }
                itemCount == 0 -> ProfileOverlayMessage(emptyText)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp),
                    contentPadding = PaddingValues(bottom = 140.dp),
                ) {
                    items(itemCount) { index ->
                        if (index > 0) ProfileDivider()
                        row(index)
                    }
                    if (hasMore) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.iconMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileOverlayMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = VkSansText,
            color = LiquidSurfaces.textSecondary(LiquidTheme.colors.isDark),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FriendRow(friend: VkFriend, compact: Boolean, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    val presence = formatPresenceLabel(
        isOnline = friend.isOnline,
        onlineLastSeen = friend.onlineInfo?.takeIf { it.visible }?.lastSeen,
        lastSeenTime = friend.lastSeen?.time?.takeIf {
            friend.onlineInfo?.visible != false
        },
    )
    val subtitle = when {
        !friend.isActive -> if (friend.deactivated == "banned") "Banned" else "Deleted"
        !friend.audioProbablyVisible -> "Music is closed"
        friend.isOnline -> "Online"
        !presence.isNullOrBlank() -> presence
        friend.screenName.isNotBlank() -> "vk.com/${friend.screenName}"
        friend.domain.isNotBlank() -> "vk.com/${friend.domain}"
        else -> "Open VK profile"
    }
    OwnerRow(
        avatarUrl = friend.avatarUrl,
        title = friend.displayName,
        subtitle = subtitle,
        subtitleTint = if (friend.isOnline && friend.isActive) OnlineGreen else colors.textSecondary,
        showOnlineDot = friend.isOnline && friend.isActive,
        enabled = friend.isActive,
        compact = compact,
        onClick = onClick,
    )
}

@Composable
private fun GroupRow(group: VkGroup, compact: Boolean, onClick: () -> Unit) {
    val subtitle = when {
        group.membersCount != null -> "${formatCount(group.membersCount!!)} members"
        group.screenName.isNotBlank() -> "vk.com/${group.screenName}"
        else -> "Open community"
    }
    OwnerRow(
        avatarUrl = group.avatarUrl,
        title = group.name,
        subtitle = subtitle,
        subtitleTint = LiquidTheme.colors.textSecondary,
        showOnlineDot = false,
        enabled = true,
        compact = compact,
        circleAvatar = false,
        onClick = onClick,
    )
}

@Composable
private fun PlaylistRow(playlist: AudioPlaylist, compact: Boolean, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    val coverTargetPx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val coverUrl = remember(playlist, coverTargetPx) {
        playlist.profileCoverUrl(coverTargetPx)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(LiquidMetrics.CoverShape)
                .background(colors.textTertiary.copy(alpha = 0.16f)),
        ) {
            AlbumArtImage(
                uri = null,
                coverUrl = coverUrl,
                contentDescription = playlist.title,
                modifier = Modifier.fillMaxSize(),
                placeholderIconSize = 22.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title.ifBlank { "Playlist" },
                fontFamily = VkSansText,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (playlist.count > 0) {
                    "${formatCount(playlist.count)} tracks"
                } else {
                    "Open in library"
                },
                fontFamily = VkSansText,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            com.lmg.vk.ui.icons.LmgGlyphs.ChevronRightOutline24,
            null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun OwnerRow(
    avatarUrl: String,
    title: String,
    subtitle: String,
    subtitleTint: Color,
    showOnlineDot: Boolean,
    enabled: Boolean,
    compact: Boolean,
    circleAvatar: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val shape = if (circleAvatar) CircleShape else RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(shape)
                    .background(colors.textTertiary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                // Загрузка может провалиться уже после того, как ссылка пришла
                // (блокировка домена, TLS, 403 от CDN). Тогда вместо пустого
                // круга показываем честную заглушку, а причину пишем в лог —
                // по нему видно, ссылки не дошли или картинка не скачалась.
                var loadFailed by remember(avatarUrl) { mutableStateOf(false) }
                if (avatarUrl.isNotBlank() && !loadFailed) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { state ->
                            loadFailed = true
                            DebugLog.add(
                                "AVATAR fail \"$title\" $avatarUrl -> " +
                                    (state.result.throwable.message ?: state.result.throwable.toString()),
                            )
                        },
                    )
                } else {
                    val initials = remember(title) { initialsOf(title) }
                    if (initials.isNotEmpty()) {
                        Text(
                            text = initials,
                            fontFamily = VkSansText,
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        Icon(
                            imageVector = if (circleAvatar) com.lmg.vk.ui.icons.LmgGlyphs.UserOutline28 else com.lmg.vk.ui.icons.LmgGlyphs.Users3Outline28,
                            contentDescription = null,
                            tint = colors.iconMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (showOnlineDot) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(OnlineGreen),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = VkSansText,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontFamily = VkSansText,
                color = subtitleTint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            com.lmg.vk.ui.icons.LmgGlyphs.ChevronRightOutline24,
            null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// --------------------------------- примитивы ---------------------------------

/**
 * Шапка профиля + наезжающий лист — тот же приём, что на экранах артиста и
 * альбома, чтобы профиль перестал выглядеть разделом настроек.
 *
 * Собрано из общего словаря [LiquidMetrics]/[LiquidSurfaces], а не своими
 * числами: смена ритма приложения должна оставаться правкой одного файла.
 */
@Composable
private fun ProfileHeaderWithSheet(
    avatarUrl: String?,
    displayName: String,
    subtitle: String,
    status: String?,
    presence: String?,
    isVerified: Boolean,
    isOnline: Boolean,
    isDark: Boolean,
    compact: Boolean,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        ProfileHeader(
            avatarUrl = avatarUrl,
            displayName = displayName,
            subtitle = subtitle,
            status = status,
            presence = presence,
            isVerified = isVerified,
            isOnline = isOnline,
            compact = compact,
            onOpenLibrary = onOpenLibrary,
            onOpenSettings = onOpenSettings,
        )
        ProfileSheetTop(
            isDark = isDark,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Фотография профиля на всю ширину, поверх неё — имя, адрес и действия.
 *
 * ПОЧЕМУ ФОТО НА ВСЮ ШИРИНУ, А НЕ КРУЖОК. У артиста и альбома шапка именно
 * такая, и профиль был единственным экраном с центрированным кружком: разный
 * приём для одной и той же роли («кто это») читается как два приложения.
 *
 * Аватар — квадратная картинка, растянутая по ширине с обрезкой по центру
 * (ContentScale.Crop). VK отдаёт для photo_max квадрат, поэтому кадрирование
 * попадает по лицу; на нестандартном фото обрежется по краям, а не исказится.
 *
 * Текст поверх фото всегда светлый (LiquidSurfaces.onHeader*), как у артиста:
 * под ним затемняющий градиент, и в обеих темах шапка остаётся тёмной.
 */
@Composable
private fun ProfileHeader(
    avatarUrl: String?,
    displayName: String,
    subtitle: String,
    status: String?,
    presence: String?,
    isVerified: Boolean,
    isOnline: Boolean,
    compact: Boolean,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Шапка КВАДРАТНАЯ, как на экране друга: аватар у VK квадратный, и при
    // фиксированной высоте 300dp на ~410dp ширины ContentScale.Crop срезал ему
    // верх и низ. aspectRatio(1f) считает высоту от ФАКТИЧЕСКОЙ ширины
    // контейнера, поэтому на широком экране (список ограничен 640dp) квадрат
    // остаётся квадратом, а не тянется во всю ширину устройства.
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    // ImageRequest, а не просто model: нужен crossfade, иначе
                    // фото «вщёлкивается» поверх тёмной плашки.
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    // Фото растягивается под ширину экрана, и дефолтная Low
                    // даёт на апскейле заметную ступеньку по краям.
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Фото нет — ровная тёмная плашка со значком. Светлую здесь
                // ставить нельзя: белый текст поверх неё исчезнет.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2A2A2E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = com.lmg.vk.ui.icons.LmgGlyphs.UserOutline28,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.30f),
                        modifier = Modifier.size(96.dp),
                    )
                }
            }

            // Затемнение снизу: имя поверх светлого кадра иначе не читается.
            // Сверху тоже немного — под статус-баром иначе теряются часы.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.30f to Color.Transparent,
                            0.60f to Color.Black.copy(alpha = 0.25f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = LiquidMetrics.ScreenPadding,
                    end = LiquidMetrics.ScreenPadding,
                    // Ровно столько, чтобы кнопки не ушли под кромку листа.
                    bottom = LiquidMetrics.SheetOverlap + 8.dp,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    color = LiquidSurfaces.onHeaderPrimary,
                    fontSize = if (compact) 32.sp else LiquidMetrics.TitleHuge,
                    fontWeight = LiquidMetrics.TitleHugeWeight,
                    fontFamily = VkSansDisplay,
                    letterSpacing = LiquidMetrics.TitleHugeSpacing,
                    lineHeight = if (compact) 36.sp else 44.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isVerified) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = lmgVector(LmgDrawables.CheckShieldOutline28),
                        contentDescription = "Verified",
                        tint = Color(0xFF0077FF),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOnline) {
                    // Онлайн — точкой перед адресом. Строки «Presence» в списке
                    // ниже больше нет: одно и то же дважды не сообщаем.
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen),
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = listOfNotNull(
                        subtitle.takeIf(String::isNotBlank),
                        presence?.takeIf(String::isNotBlank),
                    ).joinToString(" · "),
                    color = LiquidSurfaces.onHeaderSecondary,
                    fontFamily = VkSansText,
                    fontSize = LiquidMetrics.HeaderCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            status?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    color = LiquidSurfaces.onHeaderSecondary,
                    fontFamily = VkSansText,
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileHeaderButton(
                    label = "My Music",
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.ListPlayOutline28,
                    filled = true,
                    onClick = onOpenLibrary,
                )
                ProfileHeaderButton(
                    label = "Settings",
                    icon = com.lmg.vk.ui.icons.LmgGlyphs.GearOutline24,
                    filled = false,
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

/**
 * Кнопка действия в шапке — тот же контракт, что у артиста: главная сплошная
 * белая (под ней фотография, только плотная заливка гарантирует читаемость),
 * вторая стеклянная, чтобы не спорить за внимание.
 */
@Composable
private fun RowScope.ProfileHeaderButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (filled) Color.Black else Color.White
    Row(
        modifier = Modifier
            .weight(1f)
            .height(LiquidMetrics.ActionButtonHeight)
            .shadow(
                elevation = if (filled) LiquidMetrics.ButtonElevation else 2.dp,
                shape = CircleShape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(CircleShape)
            .background(if (filled) Color.White else LiquidSurfaces.glassAction)
            .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = contentColor,
            fontFamily = VkSansText,
            fontSize = LiquidMetrics.ActionLabel,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Верхушка листа: наезжает на шапку, скруглена сверху, с полоской-ручкой. */
@Composable
private fun ProfileSheetTop(isDark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
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

@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    val isDark = LiquidTheme.colors.isDark
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LiquidMetrics.ScreenPadding)
            // Карточки — из общего словаря: радиус CardRadius, заливка
            // LiquidSurfaces.card, тень CardElevation с подсветкой (на тёмном
            // фоне чёрная тень не видна, и карточка выглядит плоской).
            // Свой dimensionalSurface здесь убран: он был вторым визуальным
            // языком рядом с тем, по которому сделаны артист и альбом.
            .shadow(
                elevation = LiquidMetrics.CardElevation,
                shape = LiquidMetrics.CardShape,
                ambientColor = LiquidSurfaces.shadowTint(isDark),
                spotColor = LiquidSurfaces.shadowTint(isDark),
            )
            .clip(LiquidMetrics.CardShape)
            .background(LiquidSurfaces.card(isDark)),
        content = content,
    )
}
@Composable
private fun ProfileSectionLabel(text: String) {
    // Заголовок раздела — как на артисте: крупный полужирный с плотным
    // трекингом. Прежний вариант (11sp капсом с разрядкой 1.4) — язык
    // системных настроек, из которого экран и вытаскиваем.
    Text(
        text = text,
        fontFamily = VkSansDisplay,
        color = LiquidSurfaces.textPrimary(LiquidTheme.colors.isDark),
        fontSize = LiquidMetrics.SectionTitle,
        fontWeight = LiquidMetrics.SectionTitleWeight,
        letterSpacing = LiquidMetrics.SectionTitleSpacing,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** Однострочное сообщение VK (ошибка секции, закрытая приватность, пустой список). */
@Composable
private fun ProfileNoticeRow(text: String) {
    Text(
        text = text,
        fontFamily = VkSansText,
        color = LiquidTheme.colors.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
    )
}
@Composable
private fun ProfileMetricsRow(
    firstValue: String,
    firstLabel: String,
    secondValue: String,
    secondLabel: String,
    compact: Boolean,
    onFirstClick: (() -> Unit)? = null,
    onSecondClick: (() -> Unit)? = null,
) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileMetric(firstValue, firstLabel, Modifier.weight(1f), colors, compact, onFirstClick)
        ProfileMetric(secondValue, secondLabel, Modifier.weight(1f), colors, compact, onSecondClick)
    }
}

/**
 * Плитка метрики. Раньше — просто два текста друг под другом; цифра тонула в
 * общем потоке строк. Теперь вложенная плитка со своей поверхностью: число
 * читается как значение, а не как ещё одна подпись.
 *
 * Поверхность чуть светлее/темнее карточки-родителя, а не тот же цвет — иначе
 * плитка на ней не видна вовсе.
 */
@Composable
private fun ProfileMetric(
    value: String,
    label: String,
    modifier: Modifier,
    colors: com.lmg.vk.ui.theme.LiquidColors,
    compact: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(LiquidMetrics.CoverShape)
            // cardPressed как «на тон отличную» поверхность: card совпал бы с
            // карточкой-родителем, и плитка не читалась бы вовсе.
            .background(LiquidSurfaces.cardPressed(colors.isDark))
            // Кликабельна ТОЛЬКО когда есть куда вести: у «Подписчиков» и
            // «Подписок» своего экрана нет (VK не даёт списков этому токену), и
            // отклик на нажатие обещал бы переход, которого не будет.
            .then(
                if (onClick != null) {
                    Modifier.liquidClickable(pressedScale = LiquidMotion.PressCard, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                fontFamily = VkSansText,
                color = LiquidSurfaces.textPrimary(colors.isDark),
                fontSize = if (compact) 20.sp else 23.sp,
                fontWeight = FontWeight.Bold,
            )
            // Шеврон — единственный признак, по которому видно, что плитка
            // открывается. Без него кликабельная и мёртвая выглядят одинаково.
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    com.lmg.vk.ui.icons.LmgGlyphs.ChevronRightOutline24,
                    contentDescription = null,
                    tint = LiquidSurfaces.textTertiary(colors.isDark),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontFamily = VkSansText,
            color = LiquidSurfaces.textSecondary(colors.isDark),
            fontSize = LiquidMetrics.Caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Факты аккаунта плитками по две в ряд.
 *
 * Строками (`ProfileInfoRow`) они занимали пять экранных полос ради нескольких
 * слов в каждой — VK ID, адрес, город, дата рождения. Здесь то же самое
 * умещается в две-три полосы.
 *
 * Нечётное число фактов даёт последнюю плитку на полную ширину: пустая ячейка
 * рядом выглядела бы как потерянные данные.
 */
@Composable
private fun ProfileFactGrid(
    facts: List<Pair<ImageVector, Pair<String, String>>>,
    compact: Boolean,
) {
    if (facts.isEmpty()) return
    val colors = LiquidTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = LiquidMetrics.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        facts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (icon, pair) ->
                    ProfileFactTile(
                        icon = icon,
                        label = pair.first,
                        value = pair.second,
                        compact = compact,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileFactTile(
    icon: ImageVector,
    label: String,
    value: String,
    compact: Boolean,
    colors: com.lmg.vk.ui.theme.LiquidColors,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(LiquidMetrics.CardShape)
            .background(LiquidSurfaces.card(colors.isDark))
            .padding(horizontal = 14.dp, vertical = if (compact) 12.dp else 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = LiquidSurfaces.textSecondary(colors.isDark),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontFamily = VkSansText,
                color = LiquidSurfaces.textSecondary(colors.isDark),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontFamily = VkSansText,
            color = LiquidSurfaces.textPrimary(colors.isDark),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileNavigationRow(
    icon: ImageVector,
    label: String,
    value: String,
    compact: Boolean,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontFamily = VkSansText, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(value, fontFamily = VkSansText, color = colors.textSecondary, fontSize = 12.sp)
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.iconMuted)
        } else {
            Icon(com.lmg.vk.ui.icons.LmgGlyphs.ChevronRightOutline24, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProfileActionRow(icon: ImageVector, label: String, compact: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DestructiveRed, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontFamily = VkSansText,
            color = DestructiveRed,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ProfileDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = 52.dp)
            .background(LiquidSurfaces.divider(LiquidTheme.colors.isDark)),
    )
}

/**
 * Горизонтальные обложки плейлистов из уже загруженного `audio.getPlaylists`.
 * Тап открывает выбранный серверный плейлист через существующий playlist route.
 */
@Composable
private fun ProfilePlaylistPreviewRow(
    playlists: List<AudioPlaylist>,
    compact: Boolean,
    onOpenAll: () -> Unit,
    onPlaylistClick: (AudioPlaylist) -> Unit,
) {
    val colors = LiquidTheme.colors
    val cover = if (compact) 72.dp else 84.dp
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Playlists",
                fontFamily = VkSansText,
                color = LiquidSurfaces.textSecondary(colors.isDark),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "See all",
                fontFamily = VkSansText,
                color = LiquidSurfaces.textTertiary(colors.isDark),
                fontSize = 12.sp,
                modifier = Modifier.liquidClickable(onClick = onOpenAll),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(playlists, key = { it.fullId }) { playlist ->
                ProfilePlaylistCover(
                    playlist = playlist,
                    size = cover,
                    onClick = { onPlaylistClick(playlist) },
                )
            }
        }
    }
}

@Composable
private fun ProfilePlaylistCover(
    playlist: AudioPlaylist,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val coverTargetPx = with(LocalDensity.current) { size.roundToPx() }
    val coverUrl = remember(playlist, coverTargetPx) {
        playlist.profileCoverUrl(coverTargetPx)
    }
    Column(
        modifier = Modifier
            .width(size)
            .liquidClickable(pressedScale = LiquidMotion.PressCard, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(LiquidMetrics.CoverShape)
                .background(LiquidSurfaces.cardPressed(colors.isDark)),
        ) {
            AlbumArtImage(
                uri = null,
                coverUrl = coverUrl,
                contentDescription = playlist.title,
                modifier = Modifier.fillMaxSize(),
                placeholderIconSize = 28.dp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = playlist.title.ifBlank { "Playlist" },
            fontFamily = VkSansText,
            color = LiquidSurfaces.textPrimary(colors.isDark),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (playlist.count > 0) {
            Text(
                text = "${formatCount(playlist.count)} tracks",
                fontFamily = VkSansText,
                color = LiquidSurfaces.textTertiary(colors.isDark),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Аватары друзей online из первой страницы `friends.get`.
 * Не отдельный API: фильтр уже загруженного списка.
 */
@Composable
private fun ProfileOnlineFriendsStrip(
    friends: List<VkFriend>,
    compact: Boolean,
    onOpenAll: () -> Unit,
    onFriendClick: (VkFriend) -> Unit,
) {
    val colors = LiquidTheme.colors
    val avatar = if (compact) 44.dp else 48.dp
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(OnlineGreen),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Online · ${friends.size}",
                fontFamily = VkSansText,
                color = LiquidSurfaces.textSecondary(colors.isDark),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "See all",
                fontFamily = VkSansText,
                color = LiquidSurfaces.textTertiary(colors.isDark),
                fontSize = 12.sp,
                modifier = Modifier.liquidClickable(onClick = onOpenAll),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(friends.take(16), key = { it.id }) { friend ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(avatar + 8.dp)
                        .liquidClickable(
                            pressedScale = LiquidMotion.PressCard,
                            onClick = { onFriendClick(friend) },
                        ),
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(avatar)
                                .clip(CircleShape)
                                .background(colors.textTertiary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val url = friend.avatarUrl
                            if (url.isNotBlank()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = friend.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text = initialsOf(friend.displayName),
                                    fontFamily = VkSansText,
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(LiquidSurfaces.card(colors.isDark))
                                .padding(2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(OnlineGreen),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = friend.firstName.ifBlank { friend.displayName },
                        fontFamily = VkSansText,
                        color = LiquidSurfaces.textPrimary(colors.isDark),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val bg = if (selected) {
        LiquidSurfaces.textPrimary(colors.isDark).copy(alpha = 0.12f)
    } else {
        LiquidSurfaces.cardPressed(colors.isDark)
    }
    val fg = if (selected) {
        LiquidSurfaces.textPrimary(colors.isDark)
    } else {
        LiquidSurfaces.textSecondary(colors.isDark)
    }
    Text(
        text = label,
        fontFamily = VkSansText,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun ProfileSearchField(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(LiquidSurfaces.cardPressed(colors.isDark))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.SearchOutline28,
            contentDescription = null,
            tint = LiquidSurfaces.textTertiary(colors.isDark),
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = hint,
                    fontFamily = VkSansText,
                    color = LiquidSurfaces.textTertiary(colors.isDark),
                    fontSize = 14.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = VkSansText,
                    color = LiquidSurfaces.textPrimary(colors.isDark),
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(LiquidSurfaces.textPrimary(colors.isDark)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                contentDescription = "Clear",
                tint = LiquidSurfaces.textTertiary(colors.isDark),
                modifier = Modifier
                    .size(18.dp)
                    .liquidClickable(onClick = { onQueryChange("") }),
            )
        }
    }
}

// ---------------------------------- форматы ----------------------------------

/**
 * Инициалы для заглушки аватара: «Иван Петров» → «ИП», «Public page» → «P».
 * Берём буквы/цифры первых двух слов — у сообществ в названии часто попадаются
 * эмодзи и знаки, из которых инициал получился бы бессмысленным.
 */
private fun initialsOf(title: String): String = title
    .split(*WORD_SEPARATORS)
    .asSequence()
    .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
    .take(2)
    .joinToString("")
    .uppercase()

/** Обычный пробел и неразрывный U+00A0 — записан escape-ом намеренно. */
private val WORD_SEPARATORS = charArrayOf(' ', '\u00A0')

private fun Int?.orDash(): String = this?.let(::formatCount) ?: "—"

private fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> "${value / 1_000_000},${(value % 1_000_000) / 100_000}M"
    value >= 10_000 -> "${value / 1_000}K"
    else -> value.toString()
}

/** VK отдаёт `D.M.YYYY` либо `D.M`, если год скрыт настройками приватности. */
private fun formatBirthday(bdate: String): String {
    val parts = bdate.split('.').mapNotNull { it.trim().toIntOrNull() }
    if (parts.size < 2) return bdate
    val month = MONTHS.getOrNull(parts[1] - 1) ?: return bdate
    val day = parts[0]
    return if (parts.size >= 3) "$month $day, ${parts[2]}" else "$month $day"
}

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * Подпись присутствия для шапки.
 * Online — коротко; offline — relative last_seen из `online_info` или `last_seen`.
 * Пустая строка, если данных нет (не рисуем «Unknown»).
 */
private fun formatPresenceLabel(
    isOnline: Boolean,
    onlineLastSeen: Long?,
    lastSeenTime: Long?,
): String? {
    if (isOnline) return "Online"
    val epochSec = when {
        onlineLastSeen != null && onlineLastSeen > 0L -> onlineLastSeen
        lastSeenTime != null && lastSeenTime > 0L -> lastSeenTime
        else -> return null
    }
    val nowSec = System.currentTimeMillis() / 1000L
    val delta = (nowSec - epochSec).coerceAtLeast(0L)
    val text = when {
        delta < 60L -> "just now"
        delta < 3600L -> {
            val m = TimeUnit.SECONDS.toMinutes(delta)
            if (m == 1L) "1 min ago" else "$m min ago"
        }
        delta < 86_400L -> {
            val h = TimeUnit.SECONDS.toHours(delta)
            if (h == 1L) "1 hour ago" else "$h hours ago"
        }
        delta < 86_400L * 7L -> {
            val d = TimeUnit.SECONDS.toDays(delta)
            if (d == 1L) "yesterday" else "$d days ago"
        }
        else -> return null
    }
    return "Last seen $text"
}

private fun matchesFriendQuery(friend: VkFriend, raw: String): Boolean {
    val q = raw.trim()
    if (q.isEmpty()) return true
    val hay = buildString {
        append(friend.displayName); append(' ')
        append(friend.firstName); append(' ')
        append(friend.lastName); append(' ')
        append(friend.domain); append(' ')
        append(friend.screenName); append(' ')
        append(friend.id)
    }
    return hay.contains(q, ignoreCase = true)
}

private fun matchesGroupQuery(group: VkGroup, raw: String): Boolean {
    val q = raw.trim()
    if (q.isEmpty()) return true
    val hay = buildString {
        append(group.name); append(' ')
        append(group.screenName); append(' ')
        append(group.id)
    }
    return hay.contains(q, ignoreCase = true)
}

private fun matchesPlaylistQuery(playlist: AudioPlaylist, raw: String): Boolean {
    val q = raw.trim()
    if (q.isEmpty()) return true
    val hay = buildString {
        append(playlist.title); append(' ')
        append(playlist.subtitle.orEmpty()); append(' ')
        append(playlist.fullId)
    }
    return hay.contains(q, ignoreCase = true)
}

/** Выбирает достаточную, а не максимальную (до 2560 px), обложку для строки. */
private fun AudioPlaylist.profileCoverUrl(minSidePx: Int): String? = sequence {
    photo?.let { yield(it) }
    thumbs.orEmpty().forEach { yield(it) }
}.map { it.thumbUrlFor(minSidePx) }
    .firstOrNull(String::isNotBlank)
