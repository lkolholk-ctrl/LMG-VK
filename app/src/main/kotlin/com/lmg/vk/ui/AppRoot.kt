package com.lmg.vk.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke
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

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Навигация (батч 15): единый NavHost с пер-таб бэкстеком. Индекс вкладки
    // выводим из текущего графа — весь низлежащий код (бар, мини-плеер, дым
    // Волны) продолжает работать по selectedIndex как раньше.
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentGraph = NavRoutes.graphOf(currentRoute)
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
        val (graph, home) = when (index) {
            2 -> NavRoutes.GRAPH_LIBRARY to NavRoutes.LIBRARY_HOME
            3 -> NavRoutes.GRAPH_SETTINGS to NavRoutes.SETTINGS_HOME
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

    // Оверлеи — живут НАД NavHost.
    var settingsOpen by remember { mutableStateOf(false) }
    var lrcPublishTrack by remember { mutableStateOf<com.lmg.vk.engine.Track?>(null) }
    var tagEditTrack by remember { mutableStateOf<com.lmg.vk.engine.Track?>(null) }
    var authOpen by remember { mutableStateOf(false) }
    var authAddingAccount by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var accountsDialogOpen by remember { mutableStateOf(false) }
    var accountActionError by remember { mutableStateOf<String?>(null) }
    var accountPendingRemoval by remember { mutableStateOf<com.lmg.vk.engine.backend.VkAccountSummary?>(null) }
    val accounts by com.lmg.vk.engine.backend.MusicAuth.accounts.collectAsState()
    val activeCaptchaPrompt by com.lmg.vk.network.GlobalCaptchaManager.activePrompt.collectAsState()
    val activeValidationPrompt by com.lmg.vk.network.GlobalCaptchaManager.activeValidation.collectAsState()
    // Поиск — полноэкранный ОВЕРЛЕЙ (как настройки/профиль), а НЕ пункт нав-графа.
    // Иначе экран поиска попадал в пер-таб бэкстек Волны и через saveState/
    // restoreState «прилипал» к вкладке — при возврате на таб вместо его старта
    // показывался поиск (полевой фидбек: «поиск повесился на вкладку»).
    var searchOpen by remember { mutableStateOf(false) }

    // Бар/сайдбар видны на вкладках и деталях; прячем под полными оверлеями.
    val barsVisible = !settingsOpen && !authOpen && !profileOpen && !searchOpen

    val currentTrack by PlayerController.currentTrack.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val currentPositionMs by PlayerController.currentPositionMs.collectAsState()
    val durationMs by PlayerController.durationMs.collectAsState()
    val volume by PlayerController.volume.collectAsState()

    val trackTitle = currentTrack?.title ?: "No track"
    val artistName = currentTrack?.artist ?: "—"

    val expandProgress = remember { Animatable(0f) }
    var screenHeightPx by remember { mutableStateOf(1f) }

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
            searchOpen = false
            settingsOpen = false
            profileOpen = false
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
            AppUpdater.checkForUpdate(versionCode)
        } catch (_: Exception) {}
    }

    val miniAlpha = (1f - expandProgress.value * 3f).coerceIn(0f, 1f)

    // ── Apple-style parallax: background scales down when player opens ──
    // Морф (масштаб/скругление/альфа фона) синхронно по прогрессу, а само
    // скругление — по апловской кривой (мягче «оседает»), как у их морфа обложки.
    val morphE = com.lmg.vk.ui.theme.AppleEasings.Standard.transform(
        expandProgress.value.coerceIn(0f, 1f)
    )
    val bgScale = (1f - expandProgress.value * 0.08f).coerceIn(0.9f, 1f)
    val bgCorner = (morphE * 24f).coerceAtLeast(0f)
    val bgAlpha = (1f - expandProgress.value * 0.15f).coerceIn(0.8f, 1f)

    val rootBackdrop: LayerBackdrop = rememberLayerBackdrop()

    // Back: сперва закрываем оверлеи (плеер/профиль/авторизация/эквалайзер/
    // редакторы), потом — если ничего не открыто — back уходит в NavHost,
    // который сам попает деталь → старт вкладки → предыдущая вкладка → выход.
    BackHandler(
        enabled = tagEditTrack != null || lrcPublishTrack != null || settingsOpen ||
            authOpen || profileOpen || searchOpen || expandProgress.value > 0.5f
    ) {
        when {
            tagEditTrack != null -> tagEditTrack = null
            lrcPublishTrack != null -> lrcPublishTrack = null
            settingsOpen -> settingsOpen = false
            authOpen -> {
                authOpen = false
                authAddingAccount = false
            }
            profileOpen -> profileOpen = false
            searchOpen -> searchOpen = false
            expandProgress.value > 0.5f -> animateCollapse()
        }
    }

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
                    expandProgress.value < 0.05f &&
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
                                searchOpen = true
                            } else {
                                switchTab(index, resetOnReselect = true)
                            }
                        },
                        onOpenProfile = { profileOpen = true },
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
                        onOpenAuth = { authAddingAccount = false; authOpen = true },
                        onOpenProfile = { profileOpen = true },
                        onOpenAccounts = {
                            accountActionError = null
                            accountsDialogOpen = true
                        },
                        onOpenSearch = { searchOpen = true }
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
            val lsBottomAlpha = if (expandProgress.value >= 0.99f) 0f else 1f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = expandProgress.value * 160.dp.toPx()
                        alpha = lsBottomAlpha
                    }
            ) {
                com.lmg.vk.ui.player.LandscapeBottomBar(
                    onExpand = { animateExpand() },
                    onQueueClick = { animateExpand() }
                )
            }
    } else if (barsVisible) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val bottomBarTranslateY = expandProgress.value * density.run { 160.dp.toPx() }
            val bottomBarAlpha = if (expandProgress.value >= 0.99f) 0f else 1f

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
                currentTrack?.displayArtUri, currentTrack?.coverUrl,
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
                        translationY = bottomBarTranslateY +
                            waveHideFrac * 160.dp.toPx()   // автоскрытие на Wave
                        alpha = bottomBarAlpha
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
                    val miniLiked by miniLibraryRepo.isFavoriteFlow(miniTrack.id)
                        .collectAsState(initial = false)
                    Spacer(Modifier.height(6.dp))
                    com.lmg.vk.ui.player.MiniPlayer(
                        trackTitle = trackTitle,
                        artistName = artistName,
                        isPlaying = isPlaying,
                        albumArtUri = miniTrack.displayArtUri,
                        coverUrl = miniTrack.coverUrl,
                        tint = albumColorsForBar.darkMuted,
                        isLiked = miniLiked,
                        onToggleLike = { scope.launch { miniLibraryRepo.toggleFavorite(miniTrack) } },
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
            if (onWaveTab && !waveBarShown && expandProgress.value < 0.1f) {
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
        ForceDarkContent {
        FullPlayer(
            expandProgress = expandProgress.value,
            trackTitle = trackTitle,
            artistName = artistName,
            artists = currentTrack?.artists ?: emptyList(),
            isPlaying = isPlaying,
            albumArtUri = currentTrack?.displayArtUri,
            coverUrl = currentTrack?.coverUrl,
            audioFileUri = currentTrack?.uri,
            albumId = currentTrack?.albumId ?: -1L,
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
            onOpenSettings = { settingsOpen = true },
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

        // ── Публикация текста в LRCLIB (открывается из меню трека в плеере) ──
        AnimatedVisibility(
            visible = lrcPublishTrack != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 300f)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 400f)
            ) + fadeOut(tween(150))
        ) {
            lrcPublishTrack?.let { track ->
                com.lmg.vk.ui.screens.LrcPublishScreen(
                    track = track,
                    onBack = { lrcPublishTrack = null }
                )
            }
        }

        // ── Редактирование тегов (открывается из меню трека в плеере) ──
        AnimatedVisibility(
            visible = tagEditTrack != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 300f)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 400f)
            ) + fadeOut(tween(150))
        ) {
            tagEditTrack?.let { track ->
                com.lmg.vk.ui.screens.TagEditScreen(
                    track = track,
                    onBack = { tagEditTrack = null }
                )
            }
        }

        // ── Поиск (оверлей поверх всего, из сайдбара или кнопки на главной) ──
        AnimatedVisibility(
            visible = searchOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 300f)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 400f)
            ) + fadeOut(tween(150))
        ) {
            com.lmg.vk.ui.screens.SearchScreen(
                onNavigateToAlbum = { id ->
                    searchOpen = false
                    navController.navigate(NavRoutes.album(NavRoutes.TAB_WAVE, id))
                },
                onNavigateToArtist = { id ->
                    searchOpen = false
                    navController.navigate(NavRoutes.artist(NavRoutes.TAB_WAVE, id))
                },
                onOpenPlayer = { searchOpen = false; animateExpand() },
                onBack = { searchOpen = false }
            )
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(340, easing = com.lmg.vk.ui.theme.AppleEasings.Standard)
            ) + fadeIn(animationSpec = tween(250)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300, easing = com.lmg.vk.ui.theme.AppleEasings.Standard)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            SettingsScreen(
                onBack = { settingsOpen = false },
                // Экран аудиоэффектов удалён — колбэк пустой (см. выше).
                onOpenEqualizer = {},
                onOpenProfile = { profileOpen = true; settingsOpen = false },
                onOpenAccounts = {
                    accountActionError = null
                    accountsDialogOpen = true
                },
                // Оверлей закрываем перед переходом: экран лога живёт в NavHost,
                // то есть ПОД оверлеем — иначе переход просто не было бы видно.
                onOpenDebugLog = { settingsOpen = false; navController.navigate(NavRoutes.DEBUG_LOG) },
                backdrop = rootBackdrop
            )
        }

        // ── Profile Screen ──
        AnimatedVisibility(
            visible = profileOpen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f)
            ) + fadeIn(tween(200)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 350f)
            ) + fadeOut(tween(150))
        ) {
            ProfileScreen(
                onOpenSettings = { profileOpen = false; settingsOpen = true },
                onLogout = { profileOpen = false },
                onOpenAuth = { authAddingAccount = false; authOpen = true },
                onOpenLibrary = { profileOpen = false; switchTab(2) },
                onOpenPlaylist = { playlistId ->
                    profileOpen = false
                    navController.navigate(
                        NavRoutes.playlist(NavRoutes.TAB_LIBRARY, playlistId),
                    )
                },
                onOpenUserProfile = { userId ->
                    profileOpen = false
                    navController.navigate(NavRoutes.userProfile(userId))
                },
                onOpenGroup = { ownerId ->
                    profileOpen = false
                    navController.navigate(NavRoutes.group(ownerId))
                },
                onAddAccount = {
                    authAddingAccount = true
                    authOpen = true
                },
            )
        }


        // ── Auth Screen ──
        // ДОЛЖЕН идти ПОСЛЕ ProfileScreen: экраны — соседние AnimatedVisibility
        // в одном Box, порядок в коде = z-порядок. Раньше Auth рисовался ДО
        // профиля и при «Sign In» из профиля выезжал ПОД ним — казалось, что
        // кнопка мертва. Теперь Auth — верхний слой, открывается поверх всего.
        AnimatedVisibility(
            visible = authOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 300f)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 400f)
            ) + fadeOut(tween(150))
        ) {
            AuthScreen(
                onAuthSuccess = {
                    authOpen = false
                    if (authAddingAccount) {
                        profileOpen = true
                    } else {
                        profileOpen = false
                        // На Wave (профиль теперь иконка слева вверху, не таб).
                        switchTab(0)
                    }
                    authAddingAccount = false
                },
                onBack = {
                    authOpen = false
                    authAddingAccount = false
                },
                isAddingAccount = authAddingAccount,
            )
        }

        if (accountsDialogOpen) {
            VkAccountsDialog(
                visible = accountsDialogOpen,
                accounts = accounts,
                errorMessage = accountActionError,
                onSelectAccount = { account ->
                    if (account.isExpired) {
                        accountsDialogOpen = false
                        authAddingAccount = true
                        authOpen = true
                    } else if (!account.isActive && com.lmg.vk.engine.backend.MusicAuth.switchAccount(account.userId)) {
                        accountsDialogOpen = false
                    } else if (!account.isActive) {
                        accountActionError = "Wait for library synchronization to finish"
                    }
                },
                onRemoveAccount = { account ->
                    accountActionError = null
                    accountsDialogOpen = false
                    accountPendingRemoval = account
                },
                onAddAccount = {
                    accountsDialogOpen = false
                    authAddingAccount = true
                    authOpen = true
                },
                onDismiss = { accountsDialogOpen = false },
            )
        }

        accountPendingRemoval?.let { account ->
            val removeMessage = buildString {
                append("Only this encrypted session will be removed from the device.")
                if (!accountActionError.isNullOrBlank()) {
                    append("\n\n")
                    append(accountActionError)
                }
            }
            GlassDialog(
                visible = true,
                onDismiss = { accountPendingRemoval = null },
                icon = lmgVector(LmgDrawables.DeleteOutline28),
                iconTint = Color(0xFFFC3C44),
                title = "Remove ${account.displayName}?",
                message = removeMessage,
                primaryButton = GlassDialogButton(
                    text = "Remove",
                    backgroundColor = Color(0xFFFC3C44),
                    onClick = {
                        if (com.lmg.vk.engine.backend.MusicAuth.removeAccount(account.userId)) {
                            accountPendingRemoval = null
                            if (!com.lmg.vk.engine.backend.MusicAuth.isLoggedIn.value) {
                                profileOpen = false
                            }
                        } else {
                            accountActionError = "Wait for library synchronization to finish"
                        }
                    },
                ),
                secondaryButton = GlassDialogButton(
                    text = "Cancel",
                    onClick = { accountPendingRemoval = null },
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
}
