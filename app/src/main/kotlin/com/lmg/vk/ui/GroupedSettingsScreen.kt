package com.lmg.vk.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.lmg.vk.engine.AppSettings
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlayerSettings
import com.lmg.vk.ui.LauncherIconManager
import com.lmg.vk.ui.components.SectionTopBar
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.rememberWindowInfo
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.launch

private enum class SettingsPage(
    val title: String,
    val subtitle: String,
) {
    ROOT("Settings", "Account, playback and application"),
    VK("VK and profile", "Account, status and recommendations"),
    PLAYBACK("Playback", "Background work, timer and transitions"),
    NETWORK("Network", "Connection and proxy settings"),
    APPEARANCE("Themes and interface", "Theme, contrast and application icon"),
    DIAGNOSTICS("Diagnostics", "Playback log and troubleshooting"),
}

/**
 * Настройки сгруппированы по модели VK X: корневой экран показывает только
 * смысловые разделы, а конкретные параметры живут на отдельных страницах.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onOpenRecommendationsOnboarding: () -> Unit = {},
    onOpenDebugLog: () -> Unit = {},
    showBack: Boolean = true,
    backdrop: LayerBackdrop,
) {
    val context = LocalContext.current
    val colors = LiquidTheme.colors
    val window = rememberWindowInfo()
    val sectionGap = if (window.useSideBySide) 20.dp else 28.dp
    val scroll = rememberScrollState()
    val proxyScope = rememberCoroutineScope()

    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    var debugTaps by remember { mutableStateOf(0) }
    var debugLastTapAt by remember { mutableStateOf(0L) }
    var launcherIcon by remember { mutableStateOf(LauncherIconManager.current(context)) }

    val sleepTimerMinutes by AppSettings.sleepTimerMinutes.collectAsState()
    val crossfadeMs by PlayerSettings.crossfadeMs.collectAsState()
    val themeMode by PlayerController.themeMode.collectAsState()
    val increaseContrast by PlayerSettings.increaseContrast.collectAsState()
    val broadcastToStatus by AppSettings.broadcastToStatus.collectAsState()
    val vkLoggedIn by com.lmg.vk.engine.backend.MusicAuth.isLoggedIn.collectAsState()
    val accounts by com.lmg.vk.engine.backend.MusicAuth.accounts.collectAsState()
    val proxyEnabled by com.lmg.vk.network.proxy.VkProxyRepository.enabled.collectAsState()
    val proxyState by com.lmg.vk.network.proxy.VkProxyRepository.state.collectAsState()
    val vpnBypassEnabled by AppSettings.vpnBypassEnabled.collectAsState()
    val isVpnActive by com.lmg.vk.network.VpnBypassManager.isVpnActive.collectAsState()

    LaunchedEffect(page) { scroll.scrollTo(0) }
    BackHandler(enabled = page != SettingsPage.ROOT) { page = SettingsPage.ROOT }

    val headerBack: (() -> Unit)? = when {
        page != SettingsPage.ROOT -> ({ page = SettingsPage.ROOT })
        showBack -> onBack
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.settingsBackground)) {
        Column(
            modifier = Modifier
                .then(
                    if (window.useSideBySide) {
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxHeight()
                            .widthIn(max = 640.dp)
                    } else {
                        Modifier.fillMaxSize()
                    },
                )
                .verticalScroll(scroll),
        ) {
            SectionTopBar(
                title = page.title,
                subtitle = page.subtitle,
                isDark = colors.isDark,
                onBack = headerBack,
                onTitleClick = if (page == SettingsPage.ROOT) {
                    {
                        val now = System.currentTimeMillis()
                        debugTaps = if (now - debugLastTapAt < 1200L) debugTaps + 1 else 1
                        debugLastTapAt = now
                        if (debugTaps >= 5) {
                            debugTaps = 0
                            val enabled = !AppSettings.debugUiEnabled.value
                            AppSettings.setDebugUiEnabled(enabled)
                            android.widget.Toast.makeText(
                                context,
                                if (enabled) "Debug tools: ON" else "Debug tools: OFF",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                } else {
                    null
                },
            )

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                when (page) {
                    SettingsPage.ROOT -> {
                        SettingsProfileCard(
                            onClick = onOpenProfile,
                            onOpenAccounts = onOpenAccounts,
                        )
                        Spacer(Modifier.height(sectionGap))
                        PlainCard {
                            SettingsCategoryItem(
                                title = "VK and profile",
                                subtitle = if (vkLoggedIn) {
                                    if (accounts.size > 1) "${accounts.size} saved accounts · Status & settings"
                                    else "Status, recommendations and account"
                                } else {
                                    "Sign in and configure VK Music"
                                },
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.UserCircleOutline28,
                                onClick = { page = SettingsPage.VK },
                            )
                            SettingsCategoryDivider()
                            SettingsCategoryItem(
                                title = "Playback",
                                subtitle = playbackSummary(crossfadeMs, sleepTimerMinutes),
                                icon = lmgVector(LmgDrawables.SoundWaveOutline28),
                                onClick = { page = SettingsPage.PLAYBACK },
                            )
                            SettingsCategoryDivider()
                            SettingsCategoryItem(
                                title = "Network",
                                subtitle = networkSummary(vpnBypassEnabled, isVpnActive, proxyEnabled, proxyState),
                                icon = lmgVector(LmgDrawables.GlobeOutline28),
                                onClick = { page = SettingsPage.NETWORK },
                            )
                            SettingsCategoryDivider()
                            SettingsCategoryItem(
                                title = "Themes and interface",
                                subtitle = "${themeModeSummary(themeMode)} · ${launcherIcon.title} icon",
                                icon = lmgVector(LmgDrawables.PaletteOutline28),
                                onClick = { page = SettingsPage.APPEARANCE },
                            )
                            SettingsCategoryDivider()
                            SettingsCategoryItem(
                                title = "Diagnostics",
                                subtitle = "Playback log and troubleshooting",
                                icon = lmgVector(LmgDrawables.BugOutline28),
                                onClick = { page = SettingsPage.DIAGNOSTICS },
                            )
                        }
                    }

                    SettingsPage.VK -> {
                        SectionLabel("Account")
                        PlainCard {
                            SettingsActionItem(
                                title = "VK profile",
                                subtitle = if (vkLoggedIn) "Profile and account data" else "Sign in to VK",
                                icon = lmgVector(LmgDrawables.UserOutline28),
                                onClick = onOpenProfile,
                            )
                            if (vkLoggedIn || accounts.isNotEmpty()) {
                                PlainDivider()
                                SettingsActionItem(
                                    title = "VK accounts",
                                    subtitle = when (accounts.size) {
                                        0 -> "Add VK account"
                                        1 -> "1 saved account · Switch profile"
                                        else -> "${accounts.size} saved accounts · Switch profile"
                                    },
                                    icon = lmgVector(LmgDrawables.Users3Outline28),
                                    onClick = onOpenAccounts,
                                )
                            }
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel("Music profile")
                        PlainCard {
                            SettingsToggleItem(
                                title = "Транслировать в статус",
                                subtitle = if (!vkLoggedIn) {
                                    "Войдите в аккаунт ВКонтакте, чтобы включить трансляцию"
                                } else {
                                    "Играющий трек будет виден друзьям в вашем профиле"
                                },
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.MusicNoteWaveOutline28,
                                selected = broadcastToStatus,
                                onSelect = { AppSettings.setBroadcastToStatus(it) },
                            )
                            PlainDivider()
                            SettingsActionItem(
                                title = "Настроить рекомендации",
                                subtitle = if (!vkLoggedIn) {
                                    "Сначала войдите в аккаунт ВКонтакте"
                                } else {
                                    "Выберите исполнителей для музыкальной выдачи"
                                },
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.SlidersOutline28,
                                onClick = {
                                    if (vkLoggedIn) onOpenRecommendationsOnboarding()
                                    else onOpenProfile()
                                },
                            )
                        }
                    }

                    SettingsPage.PLAYBACK -> {
                        SectionLabel("Background playback")
                        PlainCard {
                            SettingsActionItem(
                                title = "Ignore Battery Optimization",
                                subtitle = "Prevents background stutter when Android enters Doze",
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.WarningTriangleOutline28,
                                onClick = { requestIgnoreBatteryOptimizations(context) },
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel("Sleep timer")
                        PlainCard {
                            SleepTimerSelector(
                                options = listOf(0, 15, 30, 45, 60, 90),
                                selectedMinutes = sleepTimerMinutes,
                                onSelect = { AppSettings.setSleepTimer(it) },
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel("Crossfade")
                        PlainCard {
                            CrossfadeSelector(
                                options = listOf(0, 4, 9, 12, 15, 18),
                                selectedMs = crossfadeMs,
                                onSelect = { PlayerSettings.setCrossfadeMs(it * 1000) },
                            )
                            Text(
                                text = "Fade between tracks. Off plays them back to back.",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }

                    SettingsPage.NETWORK -> {
                        SectionLabel("VPN & Connection")
                        PlainCard {
                            SettingsToggleItem(
                                title = "Обход системного VPN",
                                subtitle = if (vpnBypassEnabled) {
                                    if (isVpnActive) "VPN активен · музыка идёт напрямую через физ. сеть"
                                    else "Включен · направляет трафик напрямую через Wi-Fi / SIM"
                                } else {
                                    "Выключен · весь трафик идёт через системный VPN"
                                },
                                icon = lmgVector(LmgDrawables.CheckShieldOutline28),
                                selected = vpnBypassEnabled,
                                onSelect = AppSettings::setVpnBypassEnabled,
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel("Зеркала и прокси VK")
                        PlainCard {
                            SettingsToggleItem(
                                title = "Обход блокировок VK",
                                subtitle = networkDetail(proxyState),
                                icon = lmgVector(LmgDrawables.LockOutline28),
                                selected = proxyEnabled,
                                onSelect = { com.lmg.vk.network.proxy.VkProxyRepository.setEnabled(it) },
                            )
                            PlainDivider()
                            SettingsActionItem(
                                title = "Обновить адреса",
                                subtitle = "Перечитать список серверов и сертификатов",
                                icon = lmgVector(LmgDrawables.RefreshOutline28),
                                onClick = {
                                    proxyScope.launch {
                                        com.lmg.vk.network.proxy.VkProxyRepository.refresh()
                                    }
                                },
                            )
                        }
                    }

                    SettingsPage.APPEARANCE -> {
                        SectionLabel("Theme")
                        PlainCard {
                            ThemeModeSelector(
                                selected = themeMode,
                                onSelect = PlayerController::setThemeMode,
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel("Accessibility")
                        PlainCard {
                            SettingsToggleItem(
                                title = "Increase Contrast",
                                subtitle = "Stronger text and less glass transparency",
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.SlidersOutline28,
                                selected = increaseContrast,
                                onSelect = PlayerSettings::setIncreaseContrast,
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel("App icon")
                        PlainCard {
                            LauncherIconSelector(
                                selected = launcherIcon,
                                onSelect = { icon ->
                                    if (LauncherIconManager.select(context, icon)) {
                                        launcherIcon = icon
                                        android.widget.Toast.makeText(
                                            context,
                                            "Иконка «${icon.title}» выбрана",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                            )
                        }
                    }

                    SettingsPage.DIAGNOSTICS -> {
                        SectionLabel("Playback")
                        PlainCard {
                            SettingsActionItem(
                                title = "Отладочный лог",
                                subtitle = "Очистите лог, воспроизведите трек и отправьте результат",
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.DocumentTextOutline28,
                                onClick = onOpenDebugLog,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(110.dp))
            }
        }
    }
}

@Composable
private fun SettingsProfileCard(
    onClick: () -> Unit,
    onOpenAccounts: (() -> Unit)? = null,
) {
    val colors = LiquidTheme.colors
    val compact = rememberWindowInfo().useSideBySide
    val avatarUrl by com.lmg.vk.engine.backend.MusicAuth.avatarUrl.collectAsState()
    val profileName by com.lmg.vk.engine.backend.MusicAuth.profileName.collectAsState()
    val userEmail by com.lmg.vk.engine.backend.MusicAuth.userEmail.collectAsState()
    val accounts by com.lmg.vk.engine.backend.MusicAuth.accounts.collectAsState()
    val displayName = when {
        !profileName.isNullOrBlank() -> profileName.orEmpty()
        !userEmail.isNullOrBlank() -> userEmail.orEmpty().substringBefore("@")
            .replaceFirstChar { it.uppercase() }
        else -> "Guest"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LiquidSurfaces.card(colors.isDark))
            .liquidClickable(
                onLongClick = onOpenAccounts,
                onClick = onClick,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.glassTint),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = lmgVector(LmgDrawables.UserOutline28),
                    contentDescription = null,
                    tint = colors.iconMuted,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                color = colors.textPrimary,
                fontSize = if (compact) 15.sp else 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subText = when {
                accounts.size > 1 -> "${accounts.size} saved accounts · Long tap to switch"
                profileName != null -> "Profile and VK account"
                else -> "Sign in to VK Music"
            }
            Text(
                text = subText,
                color = colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (accounts.size > 1 && onOpenAccounts != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.12f))
                    .liquidClickable(
                        pressedScale = LiquidMotion.PressButton,
                        onClick = onOpenAccounts,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = lmgVector(LmgDrawables.Users3Outline28),
                    contentDescription = "Switch account",
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            imageVector = lmgVector(LmgDrawables.ChevronRightOutline28),
            contentDescription = null,
            tint = colors.iconMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingsCategoryItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = LiquidTheme.colors
    val compact = rememberWindowInfo().useSideBySide
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = if (colors.isDark) 0.18f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ChevronRightOutline24,
            contentDescription = null,
            tint = colors.iconMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingsCategoryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 66.dp)
            .height(1.dp)
            .background(LiquidSurfaces.divider(LiquidTheme.colors.isDark)),
    )
}

@Composable
private fun ThemeModeSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = LiquidTheme.colors
    val options = listOf(2 to "Light", 1 to "Dark", 0 to "Auto")

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.GearOutline24,
                contentDescription = null,
                tint = colors.iconDefault,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Color scheme",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (mode, label) ->
                val active = selected == mode
                val background = if (active) colors.accent else colors.glassTint
                val targetText = if (active) Color.White else colors.textSecondary
                val textColor by animateColorAsState(
                    targetValue = targetText,
                    animationSpec = tween(200),
                    label = "themeModeText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(background)
                        .liquidClickable(
                            pressedScale = LiquidMotion.PressButton,
                            onClick = { onSelect(mode) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        if (selected == 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Dynamic Color · adapts to wallpaper and system theme"
                } else {
                    "Follows system theme · Dynamic Color requires Android 12+"
                },
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

private fun themeModeSummary(mode: Int): String = when (mode) {
    1 -> "Dark theme"
    2 -> "Light theme"
    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        "Auto · Dynamic Color"
    } else {
        "Auto theme"
    }
}

private fun playbackSummary(crossfadeMs: Int, sleepTimerMinutes: Int): String {
    val crossfade = if (crossfadeMs <= 0) "Crossfade off" else "${crossfadeMs / 1000}s crossfade"
    val timer = if (sleepTimerMinutes <= 0) "timer off" else "${sleepTimerMinutes}m timer"
    return "$crossfade · $timer"
}

private fun networkSummary(
    vpnBypass: Boolean,
    isVpnActive: Boolean,
    proxyEnabled: Boolean,
    state: com.lmg.vk.network.proxy.VkProxyState,
): String = buildString {
    if (vpnBypass) {
        append(if (isVpnActive) "VPN bypass active" else "VPN bypass on")
    } else {
        append("Direct connection")
    }
    if (proxyEnabled) {
        append(" · VK proxy")
    }
}

private fun networkDetail(state: com.lmg.vk.network.proxy.VkProxyState): String = when (state) {
    is com.lmg.vk.network.proxy.VkProxyState.Available ->
        "Готово: ${state.ips.size} адр., ${state.allowedDomains.size} доменов" +
            if (state.certificates.isEmpty()) "" else ", пиннинг"
    is com.lmg.vk.network.proxy.VkProxyState.Loading -> "Загружаю настройки…"
    is com.lmg.vk.network.proxy.VkProxyState.FailedToLoad ->
        "Настройки не загрузились — нажмите «Обновить»"
}
