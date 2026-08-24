package com.lmg.vk.ui.screens

import android.content.Context
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.lmg.vk.R
import com.lmg.vk.engine.AppSettings
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.PlayerSettings
import com.lmg.vk.data.local.db.LibraryDuplicateGroup
import com.lmg.vk.data.local.db.LibraryDuplicateScan
import com.lmg.vk.data.local.db.LibraryRepository
import com.lmg.vk.ui.LauncherIconManager
import com.lmg.vk.ui.components.SectionTopBar
import com.lmg.vk.ui.glass.GlassDialog
import com.lmg.vk.ui.glass.GlassDialogButton
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.rememberWindowInfo
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.launch

private enum class SettingsPage(
    val titleRes: Int,
    val subtitleRes: Int,
) {
    ROOT(R.string.settings_page_root, R.string.settings_page_root_subtitle),
    VK(R.string.settings_page_vk, R.string.settings_page_vk_subtitle),
    PLAYBACK(R.string.settings_page_playback, R.string.settings_page_playback_subtitle),
    NETWORK(R.string.settings_page_network, R.string.settings_page_network_subtitle),
    APPEARANCE(R.string.settings_page_appearance, R.string.settings_page_appearance_subtitle),
    DIAGNOSTICS(R.string.settings_page_diagnostics, R.string.settings_page_diagnostics_subtitle),
    DUPLICATES(R.string.settings_page_duplicates, R.string.settings_page_duplicates_subtitle),
}

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
    backHandlingEnabled: Boolean = true,
    onRootBackStateChanged: (Boolean) -> Unit = {},
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
    var duplicateScan by remember { mutableStateOf<LibraryDuplicateScan?>(null) }
    var duplicateError by remember { mutableStateOf<String?>(null) }
    var duplicateStatus by remember { mutableStateOf<String?>(null) }
    var duplicateLoading by remember { mutableStateOf(false) }
    var showDuplicateRemovalDialog by remember { mutableStateOf(false) }

    val sleepTimerMinutes by AppSettings.sleepTimerMinutes.collectAsState()
    val crossfadeMs by PlayerSettings.crossfadeMs.collectAsState()
    val themeMode by PlayerController.themeMode.collectAsState()
    val increaseContrast by PlayerSettings.increaseContrast.collectAsState()
    val broadcastToStatus by AppSettings.broadcastToStatus.collectAsState()
    val vkLoggedIn by com.lmg.vk.engine.backend.MusicAuth.isLoggedIn.collectAsState()
    val activeAccountId by com.lmg.vk.engine.backend.MusicAuth.profileId.collectAsState()
    val accounts by com.lmg.vk.engine.backend.MusicAuth.accounts.collectAsState()
    val proxyEnabled by com.lmg.vk.network.proxy.VkProxyRepository.enabled.collectAsState()
    val vpnBypassEnabled by AppSettings.vpnBypassEnabled.collectAsState()
    val isVpnActive by com.lmg.vk.network.VpnBypassManager.isVpnActive.collectAsState()
    val isVpnBypassApplied by com.lmg.vk.network.VpnBypassManager.isBypassApplied.collectAsState()

    LaunchedEffect(page) { scroll.scrollTo(0) }
    LaunchedEffect(activeAccountId) {
        duplicateScan = null
        duplicateError = null
        duplicateStatus = null
    }
    LaunchedEffect(Unit) { com.lmg.vk.network.VpnBypassManager.updateStateAndApply() }

    fun returnFromPage() {
        page = if (page == SettingsPage.DUPLICATES) SettingsPage.VK else SettingsPage.ROOT
    }

    SideEffect {
        onRootBackStateChanged(
            page == SettingsPage.ROOT && showBack && !showDuplicateRemovalDialog
        )
    }

    BackHandler(enabled = backHandlingEnabled && page != SettingsPage.ROOT) {
        returnFromPage()
    }

    val headerBack: (() -> Unit)? = when {
        page != SettingsPage.ROOT -> ::returnFromPage
        showBack -> onBack
        else -> null
    }

    fun scanDuplicates() {
        if (duplicateLoading) return
        duplicateLoading = true
        duplicateError = null
        duplicateStatus = null
        proxyScope.launch {
            LibraryRepository.getInstance(context).scanCloudDuplicates()
                .onSuccess { duplicateScan = it }
                .onFailure { duplicateError = it.message ?: context.getString(R.string.duplicates_scan_failed) }
            duplicateLoading = false
        }
    }

    fun removeDuplicates() {
        if (duplicateLoading) return
        duplicateLoading = true
        duplicateError = null
        duplicateStatus = null
        proxyScope.launch {
            LibraryRepository.getInstance(context).removeScannedCloudDuplicates()
                .onSuccess { result ->
                    duplicateScan = result.scan
                    duplicateStatus = when {
                        result.removed == 0 && result.failed > 0 -> context.getString(R.string.duplicates_delete_forbidden)
                        result.failed > 0 -> context.getString(R.string.duplicates_partial_result, result.removed, result.failed)
                        else -> context.getString(R.string.duplicates_removed_count, result.removed)
                    }
                }
                .onFailure { duplicateError = it.message ?: context.getString(R.string.duplicates_remove_failed) }
            duplicateLoading = false
        }
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
                title = stringResource(page.titleRes),
                subtitle = stringResource(page.subtitleRes),
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
                                if (enabled) context.getString(R.string.debug_tools_on) else context.getString(R.string.debug_tools_off),
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
                                title = stringResource(R.string.settings_page_vk),
                                subtitle = if (vkLoggedIn) {
                                    if (accounts.size > 1) stringResource(R.string.saved_accounts_status, accounts.size)
                                    else stringResource(R.string.settings_vk_subtitle_alt)
                                } else {
                                    stringResource(R.string.sign_in_configure_vk_music)
                                },
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.UserCircleOutline28,
                                onClick = { page = SettingsPage.VK },
                            )
                            SettingsCategoryDivider()
                            SettingsCategoryItem(
                                title = stringResource(R.string.settings_page_playback),
                                subtitle = playbackSummary(context, crossfadeMs, sleepTimerMinutes),
                                icon = lmgVector(LmgDrawables.SoundWaveOutline28),
                                onClick = { page = SettingsPage.PLAYBACK },
                            )
                            SettingsCategoryDivider()
                            SettingsCategoryItem(
                                title = stringResource(R.string.settings_page_network),
                                subtitle = networkSummary(
                                    context,
                                    vpnBypassEnabled,
                                    isVpnActive,
                                    isVpnBypassApplied,
                                    proxyEnabled,
                                ),
                                icon = lmgVector(LmgDrawables.GlobeOutline28),
                                onClick = { page = SettingsPage.NETWORK },
                            )
                            SettingsCategoryDivider()
                            SettingsCategoryItem(
                                title = stringResource(R.string.settings_page_appearance),
                                subtitle = stringResource(R.string.theme_icon_summary, themeModeSummary(context, themeMode), stringResource(launcherIcon.titleRes)),
                                icon = lmgVector(LmgDrawables.PaletteOutline28),
                                onClick = { page = SettingsPage.APPEARANCE },
                            )
                            SettingsCategoryDivider()
                            SettingsCategoryItem(
                                title = stringResource(R.string.settings_page_diagnostics),
                                subtitle = stringResource(R.string.playback_log_troubleshooting),
                                icon = lmgVector(LmgDrawables.BugOutline28),
                                onClick = { page = SettingsPage.DIAGNOSTICS },
                            )
                        }
                    }

                    SettingsPage.VK -> {
                        SectionLabel(stringResource(R.string.section_account))
                        PlainCard {
                            SettingsActionItem(
                                title = stringResource(R.string.vk_profile_title),
                                subtitle = if (vkLoggedIn) stringResource(R.string.profile_and_account_data) else stringResource(R.string.sign_in_to_vk),
                                icon = lmgVector(LmgDrawables.UserOutline28),
                                onClick = onOpenProfile,
                            )
                            if (vkLoggedIn || accounts.isNotEmpty()) {
                                PlainDivider()
                                SettingsActionItem(
                                    title = stringResource(R.string.vk_accounts_title),
                                    subtitle = when (accounts.size) {
                                        0 -> stringResource(R.string.vk_accounts_add)
                                        1 -> stringResource(R.string.saved_account_switch, 1)
                                        else -> stringResource(R.string.saved_accounts_switch, accounts.size)
                                    },
                                    icon = lmgVector(LmgDrawables.Users3Outline28),
                                    onClick = onOpenAccounts,
                                )
                            }
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel(stringResource(R.string.section_music_profile))
                        PlainCard {
                            SettingsToggleItem(
                                title = stringResource(R.string.broadcast_status),
                                subtitle = if (!vkLoggedIn) {
                                    stringResource(R.string.broadcast_sign_in_hint)
                                } else {
                                    stringResource(R.string.broadcast_visible_hint)
                                },
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.MusicNoteWaveOutline28,
                                selected = broadcastToStatus,
                                onSelect = { AppSettings.setBroadcastToStatus(it) },
                            )
                            PlainDivider()
                            SettingsActionItem(
                                title = stringResource(R.string.tune_recommendations),
                                subtitle = if (!vkLoggedIn) {
                                    stringResource(R.string.sign_in_first_hint)
                                } else {
                                    stringResource(R.string.choose_artists_hint)
                                },
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.SlidersOutline28,
                                onClick = {
                                    if (vkLoggedIn) onOpenRecommendationsOnboarding()
                                    else onOpenProfile()
                                },
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel(stringResource(R.string.section_vk_library))
                        PlainCard {
                            SettingsActionItem(
                                title = stringResource(R.string.duplicates_scan_title),
                                subtitle = if (vkLoggedIn) {
                                    stringResource(R.string.duplicates_scan_subtitle)
                                } else {
                                    stringResource(R.string.sign_in_vk_first_hint)
                                },
                                icon = lmgVector(LmgDrawables.ScanViewfinderOutline28),
                                onClick = {
                                    if (vkLoggedIn) page = SettingsPage.DUPLICATES
                                    else onOpenProfile()
                                },
                            )
                        }
                    }

                    SettingsPage.PLAYBACK -> {
                        SectionLabel(stringResource(R.string.section_background_playback))
                        PlainCard {
                            SettingsActionItem(
                                title = stringResource(R.string.ignore_battery_optimization),
                                subtitle = stringResource(R.string.battery_optimization_subtitle),
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.WarningTriangleOutline28,
                                onClick = { requestIgnoreBatteryOptimizations(context) },
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel(stringResource(R.string.sleep_timer_section))
                        PlainCard {
                            SleepTimerSelector(
                                options = listOf(0, 15, 30, 45, 60, 90),
                                selectedMinutes = sleepTimerMinutes,
                                onSelect = { AppSettings.setSleepTimer(it) },
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel(stringResource(R.string.crossfade_section))
                        PlainCard {
                            CrossfadeSelector(
                                options = listOf(0, 4, 9, 12, 15, 18),
                                selectedMs = crossfadeMs,
                                onSelect = { PlayerSettings.setCrossfadeMs(it * 1000) },
                            )
                            Text(
                                text = stringResource(R.string.crossfade_description),
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }

                    SettingsPage.NETWORK -> {
                        SectionLabel(stringResource(R.string.section_vpn_connection))
                        PlainCard {
                            SettingsToggleItem(
                                title = stringResource(R.string.vpn_bypass_title),
                                subtitle = if (vpnBypassEnabled) {
                                    when {
                                        isVpnBypassApplied -> stringResource(R.string.vpn_active_vk_ok)
                                        isVpnActive -> stringResource(R.string.vpn_active_bypass_pending)
                                        else -> stringResource(R.string.vpn_enabled_standby)
                                    }
                                } else {
                                    stringResource(R.string.vpn_disabled_all_traffic)
                                },
                                icon = lmgVector(LmgDrawables.CheckShieldOutline28),
                                selected = vpnBypassEnabled,
                                onSelect = AppSettings::setVpnBypassEnabled,
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel(stringResource(R.string.section_vk_connection))
                        PlainCard {
                            SettingsToggleItem(
                                title = stringResource(R.string.proxied_connection_title),
                                subtitle = stringResource(R.string.proxied_connection_subtitle),
                                icon = lmgVector(LmgDrawables.LockOutline28),
                                selected = proxyEnabled,
                                onSelect = { com.lmg.vk.network.proxy.VkProxyRepository.setEnabled(it) },
                            )
                            PlainDivider()
                            SettingsActionItem(
                                title = stringResource(R.string.update_proxied_connection),
                                subtitle = stringResource(R.string.load_actual_settings),
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
                        SectionLabel(stringResource(R.string.section_theme))
                        PlainCard {
                            ThemeModeSelector(
                                selected = themeMode,
                                onSelect = PlayerController::setThemeMode,
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel(stringResource(R.string.section_accessibility))
                        PlainCard {
                            SettingsToggleItem(
                                title = stringResource(R.string.increase_contrast),
                                subtitle = stringResource(R.string.increase_contrast_subtitle),
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.SlidersOutline28,
                                selected = increaseContrast,
                                onSelect = PlayerSettings::setIncreaseContrast,
                            )
                        }

                        Spacer(Modifier.height(sectionGap))
                        SectionLabel(stringResource(R.string.section_app_icon))
                        PlainCard {
                            LauncherIconSelector(
                                selected = launcherIcon,
                                onSelect = { icon ->
                                    if (LauncherIconManager.select(context, icon)) {
                                        launcherIcon = icon
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.icon_selected_toast, context.getString(icon.titleRes)),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                            )
                        }
                    }

                    SettingsPage.DIAGNOSTICS -> {
                        SectionLabel(stringResource(R.string.settings_page_playback))
                        PlainCard {
                            SettingsActionItem(
                                title = stringResource(R.string.debug_log_title),
                                subtitle = stringResource(R.string.debug_log_subtitle),
                                icon = com.lmg.vk.ui.icons.LmgGlyphs.DocumentTextOutline28,
                                onClick = onOpenDebugLog,
                            )
                        }
                    }

                    SettingsPage.DUPLICATES -> {
                        SectionLabel(stringResource(R.string.section_cloud_library))
                        PlainCard {
                            SettingsActionItem(
                                title = if (duplicateLoading) stringResource(R.string.scanning_ellipsis) else stringResource(R.string.scan_library),
                                subtitle = stringResource(R.string.readonly_scan_hint),
                                icon = lmgVector(LmgDrawables.ScanViewfinderOutline28),
                                onClick = {
                                    if (!duplicateLoading) scanDuplicates()
                                },
                            )
                        }

                        if (duplicateLoading) {
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = colors.accent,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.wait_library_checking),
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        duplicateError?.let { message ->
                            Spacer(Modifier.height(16.dp))
                            DuplicateScannerMessage(message, Color(0xFFFC3C44))
                        }

                        duplicateStatus?.let { message ->
                            Spacer(Modifier.height(16.dp))
                            DuplicateScannerMessage(message, colors.accent)
                        }

                        duplicateScan?.let { scan ->
                            Spacer(Modifier.height(sectionGap))
                            SectionLabel(stringResource(R.string.section_result))
                            PlainCard {
                                DuplicateScanSummary(scan)
                                if (scan.groups.isNotEmpty()) {
                                    scan.groups.forEach { group ->
                                        PlainDivider()
                                        DuplicateGroupItem(group)
                                    }
                                }
                            }

                            if (scan.duplicateCount > 0) {
                                Spacer(Modifier.height(sectionGap))
                                SectionLabel(stringResource(R.string.section_cleanup))
                                PlainCard {
                                    SettingsActionItem(
                                        title = stringResource(R.string.delete_exact_duplicates),
                                        subtitle = stringResource(R.string.delete_duplicates_subtitle),
                                        icon = lmgVector(LmgDrawables.DeleteOutline28),
                                        onClick = {
                                            if (!duplicateLoading) showDuplicateRemovalDialog = true
                                        },
                                    )
                                }
                            }
                        }

                        GlassDialog(
                            visible = showDuplicateRemovalDialog,
                            onDismiss = { showDuplicateRemovalDialog = false },
                            icon = lmgVector(LmgDrawables.DeleteOutline28),
                            iconTint = Color(0xFFFC3C44),
                            title = stringResource(R.string.delete_exact_duplicates_question),
                            message = stringResource(R.string.delete_duplicates_message),
                            primaryButton = GlassDialogButton(
                                text = stringResource(R.string.action_delete),
                                onClick = {
                                    showDuplicateRemovalDialog = false
                                    removeDuplicates()
                                },
                            ),
                            secondaryButton = GlassDialogButton(
                                text = stringResource(R.string.action_cancel),
                                onClick = { showDuplicateRemovalDialog = false },
                                backgroundColor = colors.glassTint,
                                textColor = colors.textPrimary,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(110.dp))
            }
        }
    }
}

@Composable
private fun DuplicateScanSummary(scan: LibraryDuplicateScan) {
    val colors = LiquidTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = lmgVector(LmgDrawables.CopyOutline28),
            contentDescription = null,
            tint = if (scan.duplicateCount == 0) colors.accent else colors.iconDefault,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (scan.duplicateCount == 0) stringResource(R.string.no_duplicates_found) else stringResource(R.string.duplicates_found_count, scan.duplicateCount),
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.checked_tracks_groups, scan.totalTracks, scan.groups.size),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DuplicateGroupItem(group: LibraryDuplicateGroup) {
    val colors = LiquidTheme.colors
    val track = group.keeper
    val durationSeconds = (track.durationMs / 1_000L).coerceAtLeast(0L)
    val duration = "%d:%02d".format(durationSeconds / 60L, durationSeconds % 60L)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (group.duplicates.size + 1).toString(),
                color = colors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist.orEmpty()} · $duration · ${group.duplicates.size} лишних",
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DuplicateScannerMessage(message: String, tint: Color) {
    Text(
        text = message,
        color = tint,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
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
        else -> stringResource(R.string.guest)
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
                accounts.size > 1 -> stringResource(R.string.saved_accounts_longtap, accounts.size)
                profileName != null -> stringResource(R.string.profile_and_vk_account)
                else -> stringResource(R.string.sign_in_vk_music)
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
                    contentDescription = stringResource(R.string.switch_account),
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
    val lightLabel = stringResource(R.string.light_theme_short)
                val darkLabel = stringResource(R.string.dark_theme_short)
                val autoLabel = stringResource(R.string.auto_theme_short)
                val options = listOf(2 to lightLabel, 1 to darkLabel, 0 to autoLabel)

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
                text = stringResource(R.string.color_scheme),
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
                    stringResource(R.string.dynamic_color_hint)
                } else {
                    stringResource(R.string.follows_system_hint)
                },
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

private fun themeModeSummary(context: Context, mode: Int): String = when (mode) {
    1 -> context.getString(R.string.dark_theme)
    2 -> context.getString(R.string.light_theme)
    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getString(R.string.auto_dynamic_color)
    } else {
        context.getString(R.string.auto_theme)
    }
}

private fun playbackSummary(context: Context, crossfadeMs: Int, sleepTimerMinutes: Int): String {
    val crossfade = if (crossfadeMs <= 0) context.getString(R.string.crossfade_off)
    else context.getString(R.string.crossfade_seconds, crossfadeMs / 1000)
    val timer = if (sleepTimerMinutes <= 0) context.getString(R.string.timer_off)
    else context.getString(R.string.timer_minutes, sleepTimerMinutes)
    return "$crossfade · $timer"
}

private fun networkSummary(
    context: Context,
    vpnBypass: Boolean,
    isVpnActive: Boolean,
    isVpnBypassApplied: Boolean,
    proxyEnabled: Boolean,
): String = buildString {
    if (vpnBypass) {
        append(
            when {
                isVpnBypassApplied -> context.getString(R.string.vpn_outside_active)
                isVpnActive -> context.getString(R.string.vpn_bypass_pending)
                else -> context.getString(R.string.vpn_bypass_enabled)
            },
        )
    } else {
        append(if (isVpnActive) context.getString(R.string.through_system_vpn) else context.getString(R.string.direct_connection))
    }
    if (proxyEnabled) {
        append(" · ${context.getString(R.string.proxied_connection)}")
    }
}
