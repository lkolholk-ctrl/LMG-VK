package com.lmg.vk.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.VkProfileRepository
import com.lmg.vk.network.dto.VkFriend
import com.lmg.vk.network.dto.VkGroup
import com.lmg.vk.ui.glass.dimensionalSurface
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.launch

private val DestructiveRed = Color(0xFFFC3C44)
private val OnlineGreen = Color(0xFF34C759)
private val ProfileSurfaceDark = Color(0xFF1C1C1E)
private val ProfileSurfaceLight = Color(0xFFF2F2F7)

/** Сколько друзей/сообществ показываем в свёрнутой карточке. */
private const val PREVIEW_ROWS = 5

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
    onOpenStats: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LiquidTheme.colors
    val isLoggedIn by MusicAuth.isLoggedIn.collectAsState()
    val profileId by MusicAuth.profileId.collectAsState()
    val fallbackName by MusicAuth.profileName.collectAsState()
    val fallbackAvatar by MusicAuth.avatarUrl.collectAsState()
    val fallbackDomain by MusicAuth.profileDomain.collectAsState()
    val sessionExpiresAt by MusicAuth.profileSessionExpiresAt.collectAsState()
    val vk by VkProfileRepository.state.collectAsState()
    val ownerAudio by VkProfileRepository.ownerAudio.collectAsState()

    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var friendsExpanded by remember { mutableStateOf(false) }
    var groupsExpanded by remember { mutableStateOf(false) }

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
    val avatarUrl = profile?.bestPhotoUrl?.takeIf(String::isNotBlank) ?: fallbackAvatar
    val accountSubtitle = when {
        !slug.isNullOrBlank() -> "vk.com/$slug"
        profileId != null -> "VK ID $profileId"
        isLoggedIn -> "VK account"
        else -> "Sign in to restore your VK library"
    }

    val window = com.lmg.vk.ui.rememberWindowInfo()
    val compact = window.useSideBySide
    val surface = if (colors.isDark) ProfileSurfaceDark else ProfileSurfaceLight

    // Подэкран чужих аудио перехватывает "назад" раньше, чем оверлей профиля.
    BackHandler(enabled = ownerAudio != null) { VkProfileRepository.closeOwnerAudio() }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("Sign out of VK?") },
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
        modifier = Modifier.fillMaxSize().background(colors.settingsBackground),
    ) {
        LazyColumn(
            modifier = if (window.useSideBySide) {
                Modifier.fillMaxHeight().widthIn(max = 640.dp).align(Alignment.TopCenter)
            } else {
                Modifier.fillMaxSize()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
            item { Spacer(Modifier.height(if (compact) 12.dp else 24.dp)) }

            item {
                ProfileHero(
                    avatarUrl = avatarUrl,
                    displayName = displayName,
                    subtitle = accountSubtitle,
                    status = profile?.status?.takeIf(String::isNotBlank),
                    isVerified = profile?.isVerified == true,
                    isOnline = profile?.isOnline == true,
                    surface = surface,
                    compact = compact,
                )
            }


            item { Spacer(Modifier.height(if (compact) 16.dp else 24.dp)) }

            if (isLoggedIn) {
                vk.error?.let { message ->
                    item {
                        ProfileCard(surface = surface) {
                            ProfileNoticeRow(message)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                item {
                    ProfileCard(surface = surface) {
                        ProfileInfoRow(
                            icon = Icons.Rounded.Person,
                            label = "VK ID",
                            value = (profile?.id ?: profileId)?.toString() ?: "Loading account…",
                            compact = compact,
                        )
                        if (!slug.isNullOrBlank()) {
                            ProfileDivider()
                            ProfileInfoRow(
                                icon = Icons.Rounded.Person,
                                label = "Profile address",
                                value = "vk.com/$slug",
                                compact = compact,
                            )
                        }
                        profile?.locationLabel?.takeIf(String::isNotBlank)?.let { location ->
                            ProfileDivider()
                            ProfileInfoRow(
                                icon = Icons.Rounded.Place,
                                label = "Location",
                                value = location,
                                compact = compact,
                            )
                        }
                        profile?.bdate?.takeIf(String::isNotBlank)?.let { bdate ->
                            ProfileDivider()
                            ProfileInfoRow(
                                icon = Icons.Rounded.Cake,
                                label = "Birthday",
                                value = formatBirthday(bdate),
                                compact = compact,
                            )
                        }
                        // Строки «Presence» здесь больше нет: онлайн-статус
                        // показывает точка на аватаре в шапке, и дублировать его
                        // текстом означало бы сообщать одно и то же дважды.
                        ProfileDivider()
                        ProfileInfoRow(
                            icon = Icons.Rounded.Refresh,
                            label = "VK session",
                            value = formatSessionStatus(sessionExpiresAt),
                            compact = compact,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }

                item {
                    ProfileCard(surface = surface) {
                        ProfileSectionLabel("MY MUSIC")
                        ProfileMetricsRow(
                            firstValue = vk.audioTotal.orDash(),
                            firstLabel = "Tracks in VK",
                            secondValue = (vk.playlistsTotal ?: vk.playlists.size.takeIf { it > 0 }).orDash(),
                            secondLabel = "Playlists",
                            compact = compact,
                        )
                        vk.musicError?.let {
                            ProfileDivider()
                            ProfileNoticeRow(it)
                        }
                        ProfileDivider()
                        ProfileNavigationRow(
                            icon = Icons.Rounded.QueueMusic,
                            label = "My Library",
                            value = "Favorites, playlists & downloads",
                            compact = compact,
                            onClick = onOpenLibrary,
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }

                item {
                    ProfileCard(surface = surface) {
                        ProfileSectionLabel("SOCIAL")
                        ProfileMetricsRow(
                            firstValue = (vk.friendsTotal ?: vk.profile?.counters?.friends).orDash(),
                            firstLabel = "Friends",
                            secondValue = (vk.groupsTotal ?: vk.profile?.counters?.groups).orDash(),
                            secondLabel = "Communities",
                            compact = compact,
                        )
                        ProfileDivider()
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

                // ---------------------------- Друзья ----------------------------
                item {
                    ProfileCard(surface = surface) {
                        ProfileSectionLabel(
                            if (vk.friendsTotal != null) "FRIENDS • ${vk.friendsTotal}" else "FRIENDS",
                        )
                        when {
                            vk.isLoading && vk.friends.isEmpty() -> ProfileLoadingRow()
                            vk.friendsError != null && vk.friends.isEmpty() ->
                                ProfileNoticeRow(vk.friendsError!!)
                            vk.friends.isEmpty() -> ProfileNoticeRow("No friends returned by VK")
                            else -> {
                                val shown = if (friendsExpanded) vk.friends else vk.friends.take(PREVIEW_ROWS)
                                shown.forEachIndexed { index, friend ->
                                    if (index > 0) ProfileDivider()
                                    FriendRow(
                                        friend = friend,
                                        compact = compact,
                                        onClick = {
                                            scope.launch { VkProfileRepository.openFriendAudio(friend) }
                                        },
                                    )
                                }
                                if (!friendsExpanded && vk.friends.size > PREVIEW_ROWS) {
                                    ProfileDivider()
                                    ProfileNavigationRow(
                                        icon = Icons.Rounded.Person,
                                        label = "Show all friends",
                                        value = "${vk.friends.size} loaded",
                                        compact = compact,
                                        onClick = { friendsExpanded = true },
                                    )
                                } else if (friendsExpanded && vk.hasMoreFriends) {
                                    ProfileDivider()
                                    ProfileNavigationRow(
                                        icon = Icons.Rounded.Person,
                                        label = "Load more friends",
                                        value = "${vk.friends.size} of ${vk.friendsTotal}",
                                        compact = compact,
                                        onClick = { scope.launch { VkProfileRepository.loadMoreFriends() } },
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }

                // -------------------------- Сообщества --------------------------
                item {
                    ProfileCard(surface = surface) {
                        ProfileSectionLabel(
                            if (vk.groupsTotal != null) "COMMUNITIES • ${vk.groupsTotal}" else "COMMUNITIES",
                        )
                        when {
                            vk.isLoading && vk.groups.isEmpty() -> ProfileLoadingRow()
                            vk.groupsError != null && vk.groups.isEmpty() ->
                                ProfileNoticeRow(vk.groupsError!!)
                            vk.groups.isEmpty() -> ProfileNoticeRow("No communities returned by VK")
                            else -> {
                                val shown = if (groupsExpanded) vk.groups else vk.groups.take(PREVIEW_ROWS)
                                shown.forEachIndexed { index, group ->
                                    if (index > 0) ProfileDivider()
                                    GroupRow(
                                        group = group,
                                        compact = compact,
                                        onClick = {
                                            scope.launch { VkProfileRepository.openGroupAudio(group) }
                                        },
                                    )
                                }
                                if (!groupsExpanded && vk.groups.size > PREVIEW_ROWS) {
                                    ProfileDivider()
                                    ProfileNavigationRow(
                                        icon = Icons.Rounded.Groups,
                                        label = "Show all communities",
                                        value = "${vk.groups.size} loaded",
                                        compact = compact,
                                        onClick = { groupsExpanded = true },
                                    )
                                } else if (groupsExpanded && vk.hasMoreGroups) {
                                    ProfileDivider()
                                    ProfileNavigationRow(
                                        icon = Icons.Rounded.Groups,
                                        label = "Load more communities",
                                        value = "${vk.groups.size} of ${vk.groupsTotal}",
                                        compact = compact,
                                        onClick = { scope.launch { VkProfileRepository.loadMoreGroups() } },
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            item {
                ProfileCard(surface = surface) {
                    if (isLoggedIn) {
                        ProfileNavigationRow(
                            icon = Icons.Rounded.Refresh,
                            label = if (vk.isRefreshing) "Refreshing profile" else "Refresh profile",
                            value = "Fetch current details from VK",
                            compact = compact,
                            enabled = !vk.isRefreshing && !vk.isLoading,
                            loading = vk.isRefreshing || vk.isLoading,
                            onClick = {
                                scope.launch {
                                    val refreshed = MusicAuth.fetchUserData()
                                    VkProfileRepository.refresh(MusicAuth.profileId.value ?: 0L)
                                    if (!refreshed) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Couldn't refresh VK profile",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                        )
                        ProfileDivider()
                        if (!slug.isNullOrBlank()) {
                            ProfileNavigationRow(
                                icon = Icons.Rounded.Person,
                                label = "Copy VK profile link",
                                value = "vk.com/$slug",
                                compact = compact,
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "vk_profile_link",
                                            "https://vk.com/$slug",
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
                        }
                        ProfileNavigationRow(
                            icon = Icons.Rounded.BarChart,
                            label = "Listening Stats",
                            value = "Your top songs & artists",
                            compact = compact,
                            onClick = onOpenStats,
                        )
                        ProfileDivider()
                        ProfileNavigationRow(
                            icon = Icons.Rounded.Settings,
                            label = "Settings",
                            value = "Playback, appearance & data",
                            compact = compact,
                            onClick = onOpenSettings,
                        )
                        ProfileDivider()
                        ProfileActionRow(
                            icon = Icons.AutoMirrored.Rounded.ExitToApp,
                            label = "Sign Out",
                            compact = compact,
                            onClick = { showSignOutConfirmation = true },
                        )
                    } else {
                        ProfileNavigationRow(
                            icon = Icons.Rounded.Person,
                            label = "Sign In",
                            value = "Connect your VK account",
                            compact = compact,
                            onClick = onOpenAuth,
                        )
                        ProfileDivider()
                        ProfileNavigationRow(
                            icon = Icons.Rounded.Settings,
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
                    fontFamily = AppFontFamily,
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
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

@Composable
private fun FriendRow(friend: VkFriend, compact: Boolean, onClick: () -> Unit) {
    val colors = LiquidTheme.colors
    val subtitle = when {
        !friend.isActive -> if (friend.deactivated == "banned") "Banned" else "Deleted"
        !friend.audioProbablyVisible -> "Music is closed"
        friend.isOnline -> "Online"
        friend.screenName.isNotBlank() -> "vk.com/${friend.screenName}"
        friend.domain.isNotBlank() -> "vk.com/${friend.domain}"
        else -> "Tap to open audio"
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
        else -> "Tap to open audio"
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
                            fontFamily = AppFontFamily,
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        Icon(
                            imageVector = if (circleAvatar) Icons.Rounded.Person else Icons.Rounded.Groups,
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
                fontFamily = AppFontFamily,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontFamily = AppFontFamily,
                color = subtitleTint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// --------------------------------- примитивы ---------------------------------

/**
 * Шапка профиля.
 *
 * Раньше это был плоский круг и три строки текста по центру — как в системных
 * настройках. Теперь карточка с глубиной, и глубина здесь СТРУКТУРНАЯ, а не
 * из-за цвета:
 *
 *  1. Подложка — аватар пользователя, растянутый и размытый (24dp). Это «свет
 *     из-за объекта»: карточка перестаёт быть вырезанной из бумаги, потому что
 *     позади неё есть пространство своего цвета. Blur только на подложке, не на
 *     содержимом — размывать текст незачем, а Modifier.blur на большой площади
 *     недёшев.
 *  2. Аватар приподнят над карточкой: ободок цвета поверхности вокруг фото
 *     читается как «лежит выше», плюс своя тень.
 *  3. Онлайн-точка на аватаре, а не строкой «Presence: Online now» в списке.
 *
 * Всё, что здесь показано, приходит из VK API — выдуманных значений нет.
 */
@Composable
private fun ProfileHero(
    avatarUrl: String?,
    displayName: String,
    subtitle: String,
    status: String?,
    isVerified: Boolean,
    isOnline: Boolean,
    surface: Color,
    compact: Boolean,
) {
    val colors = LiquidTheme.colors
    val avatarSize = if (compact) 88.dp else 116.dp
    val ringWidth = 4.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(32.dp))
            .dimensionalSurface(base = surface, isDark = colors.isDark, cornerRadius = 32.dp),
    ) {
        // Размытая подложка из аватара. Держим НАД заливкой, но под контентом:
        // alpha невысокая, чтобы текст остался читаемым на любом фото.
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (colors.isDark) 0.28f else 0.20f,
                modifier = Modifier
                    .matchParentSize()
                    .blur(24.dp),
            )
            // Вуаль: без неё яркое фото «съедает» подписи снизу.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                surface.copy(alpha = 0.15f),
                                surface.copy(alpha = 0.80f),
                            ),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (compact) 22.dp else 30.dp, bottom = if (compact) 20.dp else 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                // Ободок цветом поверхности — аватар «лежит выше» карточки.
                Box(
                    modifier = Modifier
                        .size(avatarSize + ringWidth * 2)
                        .clip(CircleShape)
                        .background(surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .dimensionalSurface(base = surface, isDark = colors.isDark, edge = false),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = colors.iconMuted,
                                modifier = Modifier.size(avatarSize / 2),
                            )
                        }
                    }
                }
                // Онлайн-точка вместо строки «Presence» в списке ниже.
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(OnlineGreen),
                        )
                    }
                }
            }

            Spacer(Modifier.height(if (compact) 14.dp else 18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    fontFamily = AppFontFamily,
                    color = colors.textPrimary,
                    fontSize = if (compact) 21.sp else 27.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isVerified) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF0077FF),
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Адрес — стеклянной пилюлей, а не простой строкой: он один
            // «кликабельного вида» элемент шапки и не должен читаться как
            // продолжение имени.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.textTertiary.copy(alpha = if (colors.isDark) 0.16f else 0.10f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = subtitle,
                    fontFamily = AppFontFamily,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            status?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    fontFamily = AppFontFamily,
                    color = colors.textTertiary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(surface: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(28.dp))
            // Объём вместо плоской заливки. dimensionalSurface идёт ПОСЛЕ clip:
            // кисть заливает весь слой, и форма должна быть обрезана раньше.
            .dimensionalSurface(
                base = surface,
                isDark = LiquidTheme.colors.isDark,
                cornerRadius = 28.dp,
            ),
        content = content,
    )
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    compact: Boolean,
) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontFamily = AppFontFamily, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(value, fontFamily = AppFontFamily, color = colors.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProfileSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        color = LiquidTheme.colors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
    )
}

/** Однострочное сообщение VK (ошибка секции, закрытая приватность, пустой список). */
@Composable
private fun ProfileNoticeRow(text: String) {
    Text(
        text = text,
        fontFamily = AppFontFamily,
        color = LiquidTheme.colors.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
    )
}

@Composable
private fun ProfileLoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = LiquidTheme.colors.iconMuted,
        )
        Text(
            text = "Loading from VK…",
            fontFamily = AppFontFamily,
            color = LiquidTheme.colors.textSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ProfileMetricsRow(
    firstValue: String,
    firstLabel: String,
    secondValue: String,
    secondLabel: String,
    compact: Boolean,
) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileMetric(firstValue, firstLabel, Modifier.weight(1f), colors, compact)
        ProfileMetric(secondValue, secondLabel, Modifier.weight(1f), colors, compact)
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
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.textTertiary.copy(alpha = if (colors.isDark) 0.10f else 0.06f))
            .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 13.dp),
    ) {
        Text(
            value,
            fontFamily = AppFontFamily,
            color = colors.textPrimary,
            fontSize = if (compact) 20.sp else 23.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontFamily = AppFontFamily,
            color = colors.textSecondary,
            fontSize = 11.sp,
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
            Text(label, fontFamily = AppFontFamily, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(value, fontFamily = AppFontFamily, color = colors.textSecondary, fontSize = 12.sp)
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.iconMuted)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
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
            fontFamily = AppFontFamily,
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
            .background(LiquidTheme.colors.textTertiary.copy(alpha = 0.12f)),
    )
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

private fun formatSessionStatus(expiresAtSeconds: Long?): String {
    if (expiresAtSeconds == null) return "Active • no fixed expiry"
    val secondsLeft = expiresAtSeconds - (System.currentTimeMillis() / 1_000L)
    return when {
        secondsLeft <= 0L -> "Expired • refresh on next VK request"
        secondsLeft < 3_600L -> "Active • expires in ${secondsLeft / 60}m"
        else -> "Active • expires in ${secondsLeft / 3_600}h"
    }
}
