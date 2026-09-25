package com.lmg.vk.ui

import com.lmg.vk.ui.navigation.OverlayRouteReturn
import com.lmg.vk.ui.navigation.WindowCloseSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.lmg.vk.R
import com.lmg.vk.engine.AppSettings
import com.lmg.vk.engine.AppUpdater
import com.lmg.vk.engine.NotificationRouter
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.ui.navigation.BottomBar
import com.lmg.vk.ui.navigation.LiquidNavHost
import com.lmg.vk.ui.navigation.NavRoutes
import com.lmg.vk.ui.player.FullPlayer
import com.lmg.vk.ui.screens.SettingsScreen
import com.lmg.vk.ui.screens.AuthScreen
import com.lmg.vk.ui.screens.ProfileScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lmg.vk.ui.components.VkAccountsDialog
import com.lmg.vk.ui.glass.GlassDialog
import com.lmg.vk.ui.glass.GlassDialogButton
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.ForceDarkContent
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.launch

private enum class RootOverlay { SEARCH, SETTINGS, PROFILE, AUTH }

@Composable
private fun WindowCloseLayer(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (requestBack: () -> Unit) -> Unit,
) {
    WindowCloseSurface(enabled = enabled, onBack = onBack, modifier = modifier) { requestBack ->
        content(requestBack)
    }
}

@Composable
fun AppRoot() {
    val track by PlayerController.currentTrack.collectAsState()
    com.lmg.vk.ui.glass.ProvidePlayingArtwork(track) {
        com.lmg.vk.ui.player.ProvideMotionArtwork(track) {
            AppRootContent()
        }
    }
}

@Composable
private fun AppRootContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Навигация (батч 15): единый NavHost с пер-таб бэкстеком. Индекс вкладки
    // выводим из текущего графа — весь низлежащий код (бар, мини-плеер, дым
    // Волны) продолжает работать по selectedIndex как раньше.
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        com.lmg.vk.debug.AppStartupTrace.mark("route=$currentRoute")
    }
    val currentGraph = NavRoutes.graphOf(currentRoute)
    var overlayNavReturn by remember { mutableStateOf<OverlayRouteReturn<RootOverlay>?>(null) }
    var rootOverlayStack by remember { mutableStateOf<List<RootOverlay>>(emptyList()) }
    val selectedIndex = when (currentGraph) {
        NavRoutes.GRAPH_LIBRARY -> 2
        NavRoutes.GRAPH_SETTINGS -> 3
        NavRoutes.GRAPH_NEW -> 4
        else -> 0
    }
    // Адаптив: в широком окне (телефон-альбом / планшет) навигация уходит в
    // боковой SideBar слева, основной нижний бар прячется.
    val win = com.lmg.vk.ui.rememberWindowInfo()
    val sideProfileName by com.lmg.vk.engine.backend.MusicAuth.profileName.collectAsState()
    val sideAvatarUrl by com.lmg.vk.engine.backend.MusicAuth.avatarUrl.collectAsState()

    val onWaveHome = currentRoute == NavRoutes.WAVE_HOME

    fun switchTab(index: Int, resetOnReselect: Boolean = false) {
        overlayNavReturn?.let { pending ->
            rootOverlayStack = pending.visibleOverlays(rootOverlayStack)
        }
        overlayNavReturn = null
        if (index == 3) {
            rootOverlayStack = rootOverlayStack.filterNot {
                it == RootOverlay.SETTINGS
            } + RootOverlay.SETTINGS
            AppSettings.setLastScreen(index)
            return
        }
        rootOverlayStack = emptyList()
        val (graph, home) = when (index) {
            2 -> NavRoutes.GRAPH_LIBRARY to NavRoutes.LIBRARY_HOME
            4 -> NavRoutes.GRAPH_NEW to NavRoutes.NEW_HOME
            else -> NavRoutes.GRAPH_WAVE to NavRoutes.WAVE_HOME
        }
        if (resetOnReselect && graph == currentGraph) {
            // Re-selecting the active tab means "back to this tab's home".
            // Do this in two explicit operations. `navigate(home)` with
            // launchSingleTop can reuse the existing home entry when a screen
            // (Library/Settings) keeps its inner page in `remember`, so only
            // route-based New appeared to reset. Removing every destination
            // above the nested graph disposes that remembered state; the next
            // navigate creates a genuinely fresh home entry for every tab.
            navController.popBackStack(graph, inclusive = false)
            navController.navigate(home)
        } else if (resetOnReselect) {
            // Keep the independent back stack of the target tab.
            navController.navigate(graph) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            // A restored state must never leave another tab on top. If an old
            // saved stack is inconsistent (observed as Library showing New),
            // fall back to the concrete home represented by the clicked item.
            if (NavRoutes.graphOf(navController.currentDestination?.route) != graph) {
                navController.navigate(home) {
                    popUpTo(navController.graph.findStartDestination().id)
                    launchSingleTop = true
                }
            }
        } else {
            // Programmatic navigation keeps the existing per-tab back-stack
            // behavior. It is not a bottom-navigation reselect.
            navController.navigate(graph) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        AppSettings.setLastScreen(index)
    }

    var lrcPublishTrack by remember { mutableStateOf<com.lmg.vk.engine.Track?>(null) }
    var tagEditTrack by remember { mutableStateOf<com.lmg.vk.engine.Track?>(null) }
    var authAddingAccount by remember { mutableStateOf(false) }
    var accountsDialogOpen by remember { mutableStateOf(false) }
    var reopenAccountsAfterAuth by remember { mutableStateOf(false) }
    var reopenAccountsAfterRemoval by remember { mutableStateOf(false) }
    var settingsAtRoot by remember { mutableStateOf(true) }
    var profileAtRoot by remember { mutableStateOf(true) }
    var authAtRoot by remember { mutableStateOf(true) }
    var lrcPublishAtRoot by remember { mutableStateOf(true) }
    var accountActionError by remember { mutableStateOf<String?>(null) }
    var accountPendingRemoval by remember { mutableStateOf<com.lmg.vk.engine.backend.VkAccountSummary?>(null) }
    val accounts by com.lmg.vk.engine.backend.MusicAuth.accounts.collectAsState()
    LaunchedEffect(accountsDialogOpen) {
        if (accountsDialogOpen) com.lmg.vk.engine.backend.MusicAuth.refreshSavedSessions()
    }
    val activeCaptchaPrompt by com.lmg.vk.network.GlobalCaptchaManager.activePrompt.collectAsState()
    val activeValidationPrompt by com.lmg.vk.network.GlobalCaptchaManager.activeValidation.collectAsState()
    val visibleRootOverlays = overlayNavReturn?.visibleOverlays(rootOverlayStack) ?: rootOverlayStack
    val settingsRetained = RootOverlay.SETTINGS in rootOverlayStack
    val authRetained = RootOverlay.AUTH in rootOverlayStack
    val profileRetained = RootOverlay.PROFILE in rootOverlayStack
    val searchRetained = RootOverlay.SEARCH in rootOverlayStack
    val settingsOpen = RootOverlay.SETTINGS in visibleRootOverlays
    val authOpen = RootOverlay.AUTH in visibleRootOverlays
    val profileOpen = RootOverlay.PROFILE in visibleRootOverlays
    val searchOpen = RootOverlay.SEARCH in visibleRootOverlays
    val topRootOverlay = visibleRootOverlays.lastOrNull()

    fun openRootOverlay(overlay: RootOverlay) {
        rootOverlayStack = rootOverlayStack.filterNot { it == overlay } + overlay
    }

    fun closeRootOverlay(overlay: RootOverlay) {
        rootOverlayStack = rootOverlayStack.filterNot { it == overlay }
    }

    fun rootOverlayAtRoot(overlay: RootOverlay): Boolean = when (overlay) {
        RootOverlay.SETTINGS -> settingsAtRoot
        RootOverlay.PROFILE -> profileAtRoot
        RootOverlay.AUTH -> authAtRoot
        RootOverlay.SEARCH -> true
    }

    fun closeRootOverlayFromBack(overlay: RootOverlay) {
        closeRootOverlay(overlay)
        if (overlay == RootOverlay.AUTH) {
            authAddingAccount = false
            if (reopenAccountsAfterAuth) {
                accountsDialogOpen = true
                reopenAccountsAfterAuth = false
            }
        }
    }

    fun finishRootBack() {
        val overlay = topRootOverlay ?: return
        if (rootOverlayAtRoot(overlay)) closeRootOverlayFromBack(overlay)
    }

    fun clearRootOverlays() {
        rootOverlayStack = emptyList()
    }

    val overlayBackground = LiquidTheme.colors.settingsBackground

    @Composable
    fun overlayModifier(overlay: RootOverlay): Modifier {
        val hidden = overlay in rootOverlayStack && overlay !in visibleRootOverlays
        val position = rootOverlayStack.indexOf(overlay)
        val layer = remember(overlay) { androidx.compose.runtime.mutableFloatStateOf(100f) }
        val z = if (position >= 0) position.toFloat() + 100f else layer.floatValue
        androidx.compose.runtime.SideEffect { if (position >= 0) layer.floatValue = z }
        return Modifier
            .zIndex(if (hidden) -10f else z)
            .then(
                if (hidden) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                } else Modifier,
            )
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
            .background(overlayBackground)
    }

    fun navigateFromOverlay(overlay: RootOverlay, route: String) {
        if (overlay !in visibleRootOverlays) return
        val originEntryId = navController.currentBackStackEntry?.id ?: return
        navController.navigate(route)
        overlayNavReturn = OverlayRouteReturn(rootOverlayStack.toList(), originEntryId)
    }

    LaunchedEffect(navBackStackEntry?.id, overlayNavReturn) {
        val pending = overlayNavReturn ?: return@LaunchedEffect
        overlayNavReturn = pending.followEntry(navBackStackEntry?.id)
    }

    val fullScreenRoute = currentRoute == NavRoutes.RECOMMENDATIONS_ONBOARDING ||
        currentRoute == NavRoutes.DEBUG_LOG
    val barsVisible = !settingsOpen && !authOpen && !profileOpen && !searchOpen && !fullScreenRoute

    val currentTrack by PlayerController.currentTrack.collectAsState()
    val trackArtwork = com.lmg.vk.ui.glass.rememberTrackArtwork(currentTrack)
    val preferredTrackCover = trackArtwork.coverUrl
    val preferredArtUri = currentTrack?.displayArtUri?.takeIf { trackArtwork.isReady }
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val currentPositionMs by PlayerController.currentPositionMs.collectAsState()
    val durationMs by PlayerController.durationMs.collectAsState()
    val volume by PlayerController.volume.collectAsState()

    val trackTitle = currentTrack?.title ?: context.getString(R.string.no_track)
    val artistName = currentTrack?.artist ?: "—"

    val expandProgress = remember { Animatable(0f) }
    val playerHandlesBack by remember { derivedStateOf { expandProgress.value > 0.5f } }
    val playerLeavesWaveVisible by remember { derivedStateOf { expandProgress.value < 0.05f } }
    val playerCollapsed by remember { derivedStateOf { expandProgress.value < 0.1f } }
    var screenHeightPx by remember { mutableStateOf(1f) }
    val navBackEnabled = topRootOverlay == null &&
        lrcPublishTrack == null &&
        tagEditTrack == null &&
        !playerHandlesBack

    DisposableEffect(navController, navBackEnabled) {
        navController.enableOnBackPressed(navBackEnabled)
        onDispose { navController.enableOnBackPressed(true) }
    }

    fun animateExpand() {
        scope.launch {
            expandProgress.animateTo(
                1f,
                spring(dampingRatio = 0.82f, stiffness = 300f)
            )
        }
    }

    fun animateCollapse() {
        scope.launch {
            expandProgress.animateTo(
                0f,
                spring(dampingRatio = 0.88f, stiffness = 400f)
            )
        }
    }

    LaunchedEffect(Unit) {
        // Collect notification tap events and expand player
        NotificationRouter.openLargePlayer.collect {
            animateExpand()
        }
    }

    // ── Входящие ссылки ВКонтакте (VkLinkResolver → VkLinkRouter) ──
    // Резолвер живёт в MainActivity и знать про NavController не может, поэтому
    // навигацию по разобранной ссылке выполняем здесь. Открываем деталь в ТЕКУЩЕЙ
    // вкладке (у Настроек графа деталей нет — тогда уходим в Волну) и гасим
    // оверлеи: иначе экран выехал бы ПОД поиском/профилем и тап казался мёртвым.
    val pendingVkLink by com.lmg.vk.engine.VkLinkRouter.pending.collectAsState()
    LaunchedEffect(pendingVkLink) {
        val target = pendingVkLink ?: return@LaunchedEffect
        val tab = when (currentGraph) {
            NavRoutes.GRAPH_LIBRARY -> NavRoutes.TAB_LIBRARY
            NavRoutes.GRAPH_NEW -> NavRoutes.TAB_NEW
            else -> NavRoutes.TAB_WAVE
        }
        val route = when (target) {
            is com.lmg.vk.engine.VkLinkTarget.Album ->
                NavRoutes.album(tab, target.navId)
            is com.lmg.vk.engine.VkLinkTarget.Playlist ->
                NavRoutes.playlist(tab, target.navId)
            is com.lmg.vk.engine.VkLinkTarget.Artist ->
                NavRoutes.artist(tab, target.idOrDomain)
            // Аудио владельца зарегистрировано только в графе Библиотеки (экран
            // один, состояние в VkProfileRepository одно), поэтому вкладку здесь
            // НЕ подставляем — навигация сама переключит граф.
            is com.lmg.vk.engine.VkLinkTarget.OwnerAudio ->
                if (target.wantsProfile) {
                    if (target.isGroup) {
                        NavRoutes.group(target.ownerId)
                    } else {
                        NavRoutes.userProfile(target.ownerId)
                    }
                } else {
                    NavRoutes.ownerAudio(target.ownerId)
                }
            // Трек играется самим резолвером — сюда такие цели не доходят.
            else -> null
        }
        if (route != null) {
            clearRootOverlays()
            overlayNavReturn = null
            animateCollapse()
            navController.navigate(route)
        }
        // Обнуляем всегда: цель уже отработана, повторно вести по ней нельзя.
        com.lmg.vk.engine.VkLinkRouter.consume()
    }

    LaunchedEffect(Unit) {
        // Check for updates on launch
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            com.lmg.vk.debug.AppStartupTrace.elapsed("app_update_check") { AppUpdater.checkForUpdate(versionCode) }
        } catch (_: Exception) {}
    }

    val rootBackdrop: LayerBackdrop = rememberLayerBackdrop()

    BackHandler(
        enabled = playerHandlesBack && topRootOverlay == null && lrcPublishTrack == null && tagEditTrack == null,
        onBack = ::animateCollapse,
    )

    val lc = LiquidTheme.colors
    val rootBg = if (lc.isDark) Color.Black else Color(0xFFF5F5F7)

    Box(
            modifier = Modifier
                .fillMaxSize()
                .background(rootBg) // visible behind scaled content
                .onGloballyPositioned { screenHeightPx = it.size.height.toFloat() }
        ) {
        // ── Background content with parallax ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val e = expandProgress.value.coerceIn(0f, 1f)
                    val bgScale = (1f - e * 0.08f).coerceIn(0.9f, 1f)
                    val bgAlpha = (1f - e * 0.15f).coerceIn(0.8f, 1f)
                    val bgCorner = com.lmg.vk.ui.theme.AppleEasings.Standard.transform(e) * 24f
                    scaleX = bgScale
                    scaleY = bgScale
                    alpha = bgAlpha
                    clip = true
                    shape = RoundedCornerShape(bgCorner.dp)
                }
                .background(lc.settingsBackground)
                .layerBackdrop(rootBackdrop)
        ) {
            val waveAnimationsActive = onWaveHome &&
                    !settingsOpen && !authOpen && !profileOpen &&
                    playerLeavesWaveVisible &&
                    // При потере фокуса окна (пикер, «о приложении», шторка) замораживаем
                    // тяжёлый дым Волны, чтобы рендер не душил аудио-колбэк JUCE.
                    EffectsLifecycle.hasWindowFocus

            // Широкое окно: слева боковая навигация (SideBar), справа — контент
            // (NavHost + оверлей эквалайзера). Компакт (телефон-портрет): сайдбара
            // нет, всё как раньше (навигация нижним баром).
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                if (win.useSideBySide && barsVisible) {
                    com.lmg.vk.ui.navigation.SideBar(
                        selectedIndex = if (searchOpen) 1 else selectedIndex,
                        onItemSelected = { index ->
                            // Поиск — оверлей (не переключение вкладки), поэтому не
                            // трогает бэкстек и не прилипает к вкладкам.
                            if (index == 1) {
                                openRootOverlay(RootOverlay.SEARCH)
                            } else {
                                switchTab(index, resetOnReselect = true)
                            }
                        },
                        onOpenProfile = { openRootOverlay(RootOverlay.PROFILE) },
                        onOpenAccounts = {
                            accountActionError = null
                            accountsDialogOpen = true
                        },
                        profileName = sideProfileName,
                        avatarUrl = sideAvatarUrl
                     )
                 }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    // ── Единый NavHost: вкладки + их детали (пер-таб бэкстек) ──
                    LiquidNavHost(
                        navController = navController,
                        backdrop = rootBackdrop,
                        waveAnimationsActive = waveAnimationsActive,
                        onOpenPlayer = { animateExpand() },
                        onOpenAuth = {
                            authAddingAccount = false
                            openRootOverlay(RootOverlay.AUTH)
                        },
                        onOpenProfile = { openRootOverlay(RootOverlay.PROFILE) },
                        onOpenAccounts = {
                            accountActionError = null
                            accountsDialogOpen = true
                        },
                        onOpenSearch = { openRootOverlay(RootOverlay.SEARCH) }
                    )
                }
            }

        }

    // barsVisible объявлена выше (нужна и для SideBar, и для нижнего бара).
    // Нижний бар: в широком окне скрыт (навигация в SideBar), но мини-плеер
    // остаётся. В компакте — как раньше.
    if (barsVisible && win.useSideBySide) {
            // Альбом/планшет: снизу либо собственный бар раздела Яндекса (со
            // своими вкладками — это «приложение в приложении»), либо наш
            // полноширинный мини-плеер (раскладка по референсу друга, стиль наш).
            // Прячется под полным плеером. Основная навигация — в SideBar слева.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = expandProgress.value * 160.dp.toPx()
                        alpha = if (expandProgress.value >= 0.99f) 0f else 1f
                    }
            ) {
                com.lmg.vk.ui.player.LandscapeBottomBar(
                    onExpand = { animateExpand() },
                    onQueueClick = { animateExpand() }
                )
            }
    } else if (barsVisible) {

            // ── Автоскрытие бара на главной (Wave): 3с бездействия → бар плавно
            // уезжает вниз, фон обложки дотекает до края. Тап по нижней зоне —
            // бар возвращается (и таймер перезапускается). Без жестов. ──
            var waveBarShown by remember { mutableStateOf(true) }
            var waveBarPokes by remember { mutableStateOf(0) }
            LaunchedEffect(selectedIndex) { waveBarShown = true }  // смена вкладки — показать
            LaunchedEffect(selectedIndex, waveBarShown, waveBarPokes) {
                if (selectedIndex == 0 && waveBarShown) {
                    kotlinx.coroutines.delay(3000)
                    waveBarShown = false
                }
            }
            val waveHideFrac by animateFloatAsState(
                targetValue = if (selectedIndex == 0 && !waveBarShown) 1f else 0f,
                animationSpec = tween(350),
                label = "waveBarHide"
            )

            // На вкладке Wave бар подстраивается под дым: красится тёмной базой
            // палитры обложки (darkMuted — тот же цвет, к которому аура гасит дым
            // у нижней кромки), с плавным переливом при смене трека. На остальных
            // вкладках — обычный цвет темы.
            val onWaveTab = selectedIndex == 0
            val albumColorsForBar = com.lmg.vk.ui.glass.rememberAlbumColors(
                preferredArtUri, preferredTrackCover,
            )
            val waveBarColor by animateColorAsState(
                targetValue = lerp(albumColorsForBar.darkMuted, Color.Black, 0.35f),
                animationSpec = tween(600),
                label = "waveBarColor"
            )
            val barBackground =
                if (onWaveTab) waveBarColor
                else if (LiquidTheme.colors.isDark) Color(0xFF0D0D0F) else Color(0xFFF2F2F4)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = expandProgress.value * 160.dp.toPx() +
                            waveHideFrac * 160.dp.toPx()   // автоскрытие на Wave
                        alpha = if (expandProgress.value >= 0.99f) 0f else 1f
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Мини-плеер (стиль ЯМ, плоский — без стекла): только на вкладках,
                // где включают музыку. На Wave-главной не нужен — экран сам плеер.
                // Фон-подложку под ним НЕ рисуем — карточка «висит в воздухе» над
                // контентом (заливка barBackground — только у самого бара ниже).
                val miniTrack = currentTrack
                if (selectedIndex != 0 && miniTrack != null) {
                    val miniLibraryRepo = remember {
                        com.lmg.vk.data.local.db.LibraryRepository.getInstance(context)
                    }
                    val miniFavoriteFlow = remember(miniLibraryRepo, miniTrack.id) {
                        miniLibraryRepo.isFavoriteFlow(miniTrack.id)
                    }
                    val miniLiked by miniFavoriteFlow.collectAsState(initial = false)
                    Spacer(Modifier.height(6.dp))
                    com.lmg.vk.ui.player.MiniPlayer(
                        trackTitle = trackTitle,
                        artistName = artistName,
                        isPlaying = isPlaying,
                        albumArtUri = preferredArtUri,
                        coverUrl = preferredTrackCover,
                        tint = albumColorsForBar.darkMuted,
                        isLiked = miniLiked,
                        onToggleLike = {
                            scope.launch { miniLibraryRepo.toggleFavorite(miniTrack, "player") }
                        },
                        onExpand = { animateExpand() },
                        onPlayPause = { PlayerController.togglePlayPause(context) },
                        onSkipNext = { PlayerController.skipNext(context) },
                        onSkipPrevious = { PlayerController.skipPrevious(context) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                // Wave-контент всегда тёмный → на дымном фоне иконки бара тоже
                // должны быть «тёмной темы» (белые), даже если тема приложения светлая.
                val barContent: @Composable () -> Unit = {
                    when {
                        // Широкое окно: основной бар скрыт — навигация в SideBar
                        // слева (мини-плеер снизу остаётся).
                        win.useSideBySide -> Unit
                        else -> BottomBar(
                            selectedIndex = selectedIndex,
                            onItemSelected = { index ->
                                waveBarPokes++                 // взаимодействие — перезапуск таймера
                                switchTab(index, resetOnReselect = true)
                            }
                        )
                    }
                }
                // Заливку баром ограничиваем самим баром + navbar-инсетом, чтобы
                // серый прямоугольник не выступал из-под «висящего» мини-плеера.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barBackground),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (onWaveTab) ForceDarkContent { barContent() } else barContent()

                    Spacer(
                        modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
                    )
                }
            }

            // Невидимая тап-зона внизу: пока бар скрыт, тап возвращает его
            // (и НЕ проваливается в контент под ним). Только Wave + плеер свёрнут.
            if (onWaveTab && !waveBarShown && playerCollapsed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { waveBarShown = true }
                )
            }
        }

        // Фулл-плеер всегда тёмный — эффекты/палитра рассчитаны на тёмный фон.
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
        ForceDarkContent {
        FullPlayer(
            expandProgress = expandProgress.asState(),
            trackTitle = trackTitle,
            artistName = artistName,
            artists = currentTrack?.artists ?: emptyList(),
            isPlaying = isPlaying,
            albumArtUri = preferredArtUri,
            coverUrl = preferredTrackCover,
            audioFileUri = currentTrack?.uri?.takeIf { trackArtwork.isReady },
            albumId = if (trackArtwork.isReady) currentTrack?.albumId ?: -1L else -1L,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            volume = volume,
            onClose = { animateCollapse() },
            onDrag = { dragAmountPx ->
                if (screenHeightPx > 0f) {
                    val delta = dragAmountPx / screenHeightPx
                    scope.launch {
                        expandProgress.snapTo(
                            (expandProgress.value - delta).coerceIn(0f, 1f)
                        )
                    }
                }
            },
            onDragEnd = { flungDown ->
                // Как у Apple: закрываем при резком флике ВНИЗ (velocity) ИЛИ
                // если утащили ниже ~28% (порог мягче прежних 15%).
                if (flungDown || expandProgress.value < 0.72f) animateCollapse()
                else animateExpand()
            },
            onPlayPause = { PlayerController.togglePlayPause(context) },
            onSkipNext = { PlayerController.skipNext(context) },
            onSkipPrevious = { PlayerController.skipPrevious(context) },
            onSeek = { PlayerController.seekTo(it) },
            onVolumeChange = { PlayerController.setVolume(it) },
            onOpenSettings = { openRootOverlay(RootOverlay.SETTINGS) },
            onNavigateToArtist = { artistId ->
                // Открываем деталь артиста в текущей вкладке (у Настроек нет
                // графа деталей — тогда уходим в Волну).
                val tab = when (currentGraph) {
                    NavRoutes.GRAPH_LIBRARY -> NavRoutes.TAB_LIBRARY
                    NavRoutes.GRAPH_NEW -> NavRoutes.TAB_NEW
                    else -> NavRoutes.TAB_WAVE
                }
                navController.navigate(NavRoutes.artist(tab, artistId))
                animateCollapse()   // плеер рисуется ПОВЕРХ деталей — сворачиваем
            },
            onPublishLyrics = { track -> lrcPublishTrack = track },
            onEditTags = { track -> tagEditTrack = track }
        )
        }
        }

        // ── Публикация текста в LRCLIB (открывается из меню трека в плеере) ──
        AnimatedVisibility(
            visible = lrcPublishTrack != null,
            modifier = Modifier.zIndex(50f),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 300f)
            ) + fadeIn(tween(200)),
            exit = androidx.compose.animation.ExitTransition.None
        ) {
            lrcPublishTrack?.let { track ->
                WindowCloseLayer(
                    enabled = tagEditTrack == null && topRootOverlay == null && lrcPublishAtRoot,
                    onBack = { lrcPublishTrack = null },
                ) { requestBack ->
                    com.lmg.vk.ui.screens.LrcPublishScreen(
                        track = track,
                        onBack = requestBack,
                        onRootBackStateChanged = { lrcPublishAtRoot = it },
                    )
                }
            }
        }

        // ── Редактирование тегов (открывается из меню трека в плеере) ──
        AnimatedVisibility(
            visible = tagEditTrack != null,
            modifier = Modifier.zIndex(51f),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 300f)
            ) + fadeIn(tween(200)),
            exit = androidx.compose.animation.ExitTransition.None
        ) {
            tagEditTrack?.let { track ->
                WindowCloseLayer(
                    enabled = topRootOverlay == null,
                    onBack = { tagEditTrack = null },
                ) { requestBack ->
                    com.lmg.vk.ui.screens.TagEditScreen(
                        track = track,
                        onBack = requestBack,
                    )
                }
            }
        }

        // ── Поиск (оверлей поверх всего, из сайдбара или кнопки на главной) ──
        AnimatedVisibility(
            visible = searchRetained,
            modifier = overlayModifier(RootOverlay.SEARCH),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 300f)
            ) + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(160)) { it / 16 } + fadeOut(tween(160))
        ) {
            com.lmg.vk.ui.screens.SearchScreen(
                onNavigateToAlbum = { id ->
                    navigateFromOverlay(
                        RootOverlay.SEARCH,
                        NavRoutes.album(NavRoutes.TAB_WAVE, id),
                    )
                },
                onNavigateToArtist = { id ->
                    navigateFromOverlay(
                        RootOverlay.SEARCH,
                        NavRoutes.artist(NavRoutes.TAB_WAVE, id),
                    )
                },
                onOpenPlayer = {
                    closeRootOverlay(RootOverlay.SEARCH)
                    animateExpand()
                },
                onBack = { finishRootBack() },
            )
        }

        AnimatedVisibility(
            visible = settingsRetained,
            modifier = overlayModifier(RootOverlay.SETTINGS),
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(340, easing = com.lmg.vk.ui.theme.AppleEasings.Standard)
            ) + fadeIn(animationSpec = tween(250)),
            exit = slideOutHorizontally(tween(160)) { it / 16 } + fadeOut(tween(160))
        ) {
            SettingsScreen(
                onBack = { finishRootBack() },
                onOpenEqualizer = {},
                onOpenProfile = { openRootOverlay(RootOverlay.PROFILE) },
                onOpenAccounts = {
                    accountActionError = null
                    accountsDialogOpen = true
                },
                onOpenRecommendationsOnboarding = {
                    navigateFromOverlay(
                        RootOverlay.SETTINGS,
                        NavRoutes.RECOMMENDATIONS_ONBOARDING,
                    )
                },
                onOpenDebugLog = {
                    navigateFromOverlay(RootOverlay.SETTINGS, NavRoutes.DEBUG_LOG)
                },
                backHandlingEnabled = topRootOverlay == RootOverlay.SETTINGS,
                onRootBackStateChanged = { settingsAtRoot = it },
                backdrop = rootBackdrop,
            )
        }

        AnimatedVisibility(
            visible = profileRetained,
            modifier = overlayModifier(RootOverlay.PROFILE),
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f)
            ) + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(160)) { it / 16 } + fadeOut(tween(160))
        ) {
            ProfileScreen(
                onBack = { closeRootOverlayFromBack(RootOverlay.PROFILE) },
                onOpenHistory = { navigateFromOverlay(RootOverlay.PROFILE, NavRoutes.VK_HISTORY) },
                onSelectMainTab = { switchTab(it, resetOnReselect = true) },
                onOpenSettings = { openRootOverlay(RootOverlay.SETTINGS) },
                onLogout = { closeRootOverlay(RootOverlay.PROFILE) },
                onOpenAuth = {
                    authAddingAccount = false
                    openRootOverlay(RootOverlay.AUTH)
                },
                onOpenLibrary = { switchTab(2) },
                onOpenPlaylist = { playlistId ->
                    navigateFromOverlay(
                        RootOverlay.PROFILE,
                        NavRoutes.playlist(NavRoutes.TAB_LIBRARY, playlistId),
                    )
                },
                onOpenUserProfile = { userId ->
                    navigateFromOverlay(RootOverlay.PROFILE, NavRoutes.userProfile(userId))
                },
                onOpenGroup = { ownerId ->
                    navigateFromOverlay(RootOverlay.PROFILE, NavRoutes.group(ownerId))
                },
                onAddAccount = {
                    authAddingAccount = true
                    openRootOverlay(RootOverlay.AUTH)
                },
                onOpenAccounts = {
                    accountActionError = null
                    accountsDialogOpen = true
                },
                backHandlingEnabled = topRootOverlay == RootOverlay.PROFILE,
                onRootBackStateChanged = { profileAtRoot = it },
            )
        }
        AnimatedVisibility(
            visible = authRetained,
            modifier = overlayModifier(RootOverlay.AUTH),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 300f)
            ) + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(160)) { it / 16 } + fadeOut(tween(160))
        ) {
            AuthScreen(
                onAuthSuccess = {
                    closeRootOverlay(RootOverlay.AUTH)
                    reopenAccountsAfterAuth = false
                    if (!authAddingAccount) {
                        closeRootOverlay(RootOverlay.PROFILE)
                        switchTab(0)
                    }
                    authAddingAccount = false
                },
                onBack = { finishRootBack() },
                isAddingAccount = authAddingAccount,
                backHandlingEnabled = topRootOverlay == RootOverlay.AUTH,
                onRootBackStateChanged = { authAtRoot = it },
            )
        }

        if (accountsDialogOpen) {
            VkAccountsDialog(
                visible = accountsDialogOpen,
                accounts = accounts,
                errorMessage = accountActionError,
                onSelectAccount = { account ->
                    if (!account.isActive && com.lmg.vk.engine.backend.MusicAuth.switchAccount(account.userId)) {
                        accountsDialogOpen = false
                    } else if (!account.isActive) {
                        accountActionError = context.getString(R.string.wait_for_sync)
                    }
                },
                onRemoveAccount = { account ->
                    accountActionError = null
                    accountsDialogOpen = false
                    reopenAccountsAfterRemoval = true
                    accountPendingRemoval = account
                },
                onAddAccount = {
                    accountsDialogOpen = false
                    reopenAccountsAfterAuth = true
                    authAddingAccount = true
                    openRootOverlay(RootOverlay.AUTH)
                },
                onDismiss = { accountsDialogOpen = false },
            )
        }

        accountPendingRemoval?.let { account ->
            val removeMessage = buildString {
                append(context.getString(R.string.remove_session_body))
                if (!accountActionError.isNullOrBlank()) {
                    append("\n\n")
                    append(accountActionError)
                }
            }
            GlassDialog(
                visible = true,
                onDismiss = {
                    accountPendingRemoval = null
                    if (reopenAccountsAfterRemoval) accountsDialogOpen = true
                    reopenAccountsAfterRemoval = false
                },
                icon = lmgVector(LmgDrawables.DeleteOutline28),
                iconTint = Color(0xFFFC3C44),
                title = stringResource(R.string.remove_account_question, account.displayName),
                message = removeMessage,
                primaryButton = GlassDialogButton(
                    text = stringResource(R.string.action_remove),
                    backgroundColor = Color(0xFFFC3C44),
                    onClick = {
                        if (com.lmg.vk.engine.backend.MusicAuth.removeAccount(account.userId)) {
                            accountPendingRemoval = null
                            if (reopenAccountsAfterRemoval) accountsDialogOpen = true
                            reopenAccountsAfterRemoval = false
                            if (!com.lmg.vk.engine.backend.MusicAuth.isLoggedIn.value) {
                                closeRootOverlay(RootOverlay.PROFILE)
                            }
                        } else {
                            accountActionError = context.getString(R.string.wait_for_sync)
                        }
                    },
                ),
                secondaryButton = GlassDialogButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = {
                        accountPendingRemoval = null
                        if (reopenAccountsAfterRemoval) accountsDialogOpen = true
                        reopenAccountsAfterRemoval = false
                    },
                ),
            )
        }

        activeCaptchaPrompt?.let { prompt ->
            com.lmg.vk.ui.components.VkCaptchaDialog(
                prompt = prompt,
                onDismiss = com.lmg.vk.network.GlobalCaptchaManager::dismiss,
                onSubmit = com.lmg.vk.network.GlobalCaptchaManager::submit,
            )
        }

        activeValidationPrompt?.let { prompt ->
            com.lmg.vk.ui.components.VkWebValidationDialog(
                prompt = prompt,
                onDismiss = com.lmg.vk.network.GlobalCaptchaManager::dismissValidation,
                onComplete = com.lmg.vk.network.GlobalCaptchaManager::submitValidation,
            )
        }

    }

    BackHandler(
        enabled = topRootOverlay != null &&
            rootOverlayAtRoot(topRootOverlay) &&
            !accountsDialogOpen &&
            accountPendingRemoval == null &&
            activeCaptchaPrompt == null &&
            activeValidationPrompt == null,
        onBack = ::finishRootBack,
    )
}
