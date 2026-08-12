package com.lmg.vk.ui.screens

import android.net.Uri
import android.widget.ImageView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieComposition
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.mix.VkMixLottieStore
import com.lmg.vk.engine.backend.Chart
import com.lmg.vk.engine.backend.HomeItem
import com.lmg.vk.engine.backend.toTrack
import com.lmg.vk.engine.AudioReactor
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.PlaybackBackend
import com.lmg.vk.engine.PlaybackContext
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.Track
import com.lmg.vk.engine.VkMixCategoryType
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.glass.rememberAlbumColors
import com.lmg.vk.ui.player.AuraBackground
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.viewmodel.HomeViewModel
import com.lmg.vk.ui.viewmodel.VkMixFeedbackState
import com.lmg.vk.ui.viewmodel.VkMixUiState
import java.util.Calendar
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "My Wave" — LMG's Aura presentation for the personal VK Mix.
 *
 * VK owns the playback source and continuation, while this screen remains the
 * existing Aura hero, vertically centered (idle: big title +
 * Play; playing: artist, cover, flat controls and a live wave progress line in
 * the title pill). Mood tiles and content sections (recently played, charts,
 * recommendations) live in the New tab.
 *
 * Glass is intentionally avoided (heavy blur lags on devices) — controls are flat
 * and the background is a single animated aura Canvas. Tapping the cover or the
 * title panel opens the full-screen player via [onOpenPlayer].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveHomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    onOpenAuth: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    animationsActive: Boolean = true,
) {
    val context = LocalContext.current
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.cancelHomeLoad()
            viewModel.cancelChartsLoad()
        }
    }

    val currentTrack by PlayerController.currentTrack.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val isBuffering by PlayerController.isBuffering.collectAsState()
    val favoriteIds by PlayerController.favoriteIds.collectAsState()
    val isBuildingWave by viewModel.isBuildingWave.collectAsState()
    val mixState by viewModel.vkMixState.collectAsState()
    val mixFeedback by viewModel.vkMixFeedback.collectAsState()
    val isLoggedIn by com.lmg.vk.engine.backend.MusicAuth.isLoggedIn.collectAsState()
    var showMixSettings by remember { mutableStateOf(false) }
    val mixSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun startAuraMix() {
        if (isLoggedIn) viewModel.startAuraMix(context) else onOpenAuth()
    }

    // Активная «именованная» волна (по муду/треку/артисту). У дефолтной «Моей волны»
    // имени нет → индикатор не показываем.
    val waveContext by PlayerController.waveRefillContext.collectAsState()
    val activeStationName = waveContext?.name?.takeIf { it.isNotBlank() }

    val albumColors = rememberAlbumColors(currentTrack?.displayArtUri, currentTrack?.coverUrl)

    val track = currentTrack
    val isFavorite = track?.id
        ?.let { com.lmg.vk.engine.VkAudioIdentity.stableFullId(it) }
        ?.let(favoriteIds::contains) == true

    val scope = rememberCoroutineScope()
    val backend by PlayerController.playbackBackend.collectAsState()
    val queueList by PlayerController.queueFlow.collectAsState()

    LaunchedEffect(track?.id) {
        viewModel.onAuraTrackChanged(track?.id)
    }

    // Сглаженный бас 0..1 — пульс обложки и кромок мудкарточек. Питается только
    // от стриминга (BassAudioProcessor в цепочке ExoPlayer); у локального JUCE
    // уровней нет — эффект просто нулевой, как и «вдох» ауры-дыма. Кадровый цикл
    // работает ТОЛЬКО пока играет музыка (на паузе плавно гаснет и остановлен);
    // в энергосбережении пульс выключен целиком.
    val bassLevel = rememberSmoothedBass(
        animate = animationsActive && !com.lmg.vk.ui.PowerSaveMonitor.active,
        playing = isPlaying
    )

    // Акцент экрана — vibrant играющей обложки (fallback — зелёный волны).
    val accent by animateColorAsState(
        targetValue = if (track != null) albumColors.vibrant else WaveAccent,
        animationSpec = tween(700),
        label = "waveAccent"
    )

    // «Дальше в волне»: следующие до трёх треков очереди; индекс — абсолютный
    // (для PlayerController.playTrack). Очередь и позиция общие для обоих
    // бэкендов (JUCE-локал и ExoPlayer-стриминг) — контроллер сам маршрутизирует.
    val upNext = remember(queueList, track?.id) {
        val idx = queueList.indexOfFirst { it.id == track?.id }
        if (idx < 0) emptyList()
        else (idx + 1 until minOf(idx + 4, queueList.size)).map { it to queueList[it] }
    }

    // Official dislike belongs only to an active VK Mix streaming source.
    val showWaveFeedback = track != null &&
        PlayerController.playbackContext is PlaybackContext.VkMix &&
        backend == PlaybackBackend.EXO_STREAMING

    val mixSession = when (val state = mixState) {
        is VkMixUiState.Ready -> state.session
        is VkMixUiState.Empty -> state.session
        is VkMixUiState.Error -> state.session
        else -> null
    }
    // With no retained session the settings action resolves the personal Mix
    // from CatalogKit. While a start/settings request is already running, do
    // not open a sheet that cannot launch a second request.
    val canOpenMixSettings = isLoggedIn &&
        mixState !is VkMixUiState.Loading &&
        (mixSession?.isTunable ?: true)

    // Широкое окно (телефон-альбом / планшет): вместо полноэкранной Волны с
    // дымом — медиатечный двухколоночный layout (референс). return ПОСЛЕ всех
    // hooks выше (число composable-хуков стабильно между ориентациями).
    val win = com.lmg.vk.ui.rememberWindowInfo()
    val landscapeProfileName by com.lmg.vk.engine.backend.MusicAuth.profileName.collectAsState()
    if (win.useSideBySide) {
        LandscapeHome(
            onOpenPlayer = onOpenPlayer,
            onNavigateToArtist = onNavigateToArtist,
            profileName = landscapeProfileName,
            onOpenSearch = onNavigateToSearch,
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Living aura background (own AGSL shader, reacts to the music) ──
        AuraBackground(
            albumColors = albumColors,
            modifier = Modifier.fillMaxSize(),
            animate = animationsActive,
            playing = isPlaying,
            smokeSaturation = 1.22f,
            smokeContrast = 1.16f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            WaveTopBar(
                onSearch = onNavigateToSearch,
                onOpenProfile = onOpenProfile,
                onTune = if (canOpenMixSettings) {
                    {
                        showMixSettings = true
                        viewModel.prepareVkMixSettings()
                    }
                } else {
                    null
                },
            )

            // ── Индикатор активной волны (по муду/треку/артисту) + сброс на «Мою волну» ──
            if (activeStationName != null) {
                WaveStationIndicator(
                    name = activeStationName,
                    onClear = ::startAuraMix,
                )
            }

            // ── Hero — по центру освободившегося экрана (мудкарточки уехали в New).
            // Box центрует, внутренний скролл — страховка для маленьких экранов. ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 72.dp),   // высота нижнего бара + зазор
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                if (track == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Приветствие по времени суток — экран «свой» ещё до музыки.
                        Text(
                            text = remember { greetingForNow() },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = AppFontFamily,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "My Wave",
                            color = Color.White,
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = AppFontFamily,
                            textAlign = TextAlign.Center
                        )
                        VkMixInlineStatus(
                            state = mixState,
                            onRetry = { viewModel.retryVkMix(context) },
                            onAuth = onOpenAuth,
                        )
                        Spacer(Modifier.height(40.dp))
                        BigPlayButton(
                            loading = isBuildingWave || mixState is VkMixUiState.Loading,
                            accent = accent,
                            onClick = ::startAuraMix,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(24.dp))
                        // Имя артиста — КРУПНО (главный герой экрана). lineHeight >
                        // fontSize — при переносах (несколько артистов) строки не
                        // налезают друг на друга; до 3 строк, дальше многоточие.
                        Text(
                            text = track.artist,
                            color = Color.White,
                            fontSize = 60.sp,
                            lineHeight = 66.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = AppFontFamily,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        // Текущая строка синхронного текста (пусто, если лирики нет).
                        CurrentLyricLine(track = track)
                        Spacer(Modifier.height(16.dp))

                        // Обложка: пульсирует от баса (только стриминг — у JUCE
                        // уровней нет) и свайпается влево/вправо для скипа. Скип —
                        // через PlayerController (асинхронно, оба бэкенда), main
                        // ничего не ждёт.
                        val density = LocalDensity.current
                        val dragThresholdPx = remember(density) { with(density) { 96.dp.toPx() } }
                        val maxDragPx = remember(density) { with(density) { 160.dp.toPx() } }
                        val coverOffset = remember { Animatable(0f) }

                        // Страховка от «застрявшей» наклонённой обложки: как только
                        // реально сменился трек — возвращаем обложку в центр, даже
                        // если возвратная анимация жеста была чем-то отменена.
                        LaunchedEffect(track.id) {
                            if (coverOffset.value != 0f) {
                                coverOffset.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 420f))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(248.dp)
                                .graphicsLayer {
                                    translationX = coverOffset.value
                                    rotationZ = coverOffset.value / 42f
                                    val pulse = 1f + bassLevel.value * 0.025f
                                    scaleX = pulse
                                    scaleY = pulse
                                    alpha = 1f -
                                        (abs(coverOffset.value) / (dragThresholdPx * 4f)).coerceAtMost(0.35f)
                                }
                                .clip(RoundedCornerShape(28.dp))
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        // Резинка: тянется с сопротивлением и не дальше maxDragPx.
                                        scope.launch {
                                            val next = (coverOffset.value + delta * 0.85f)
                                                .coerceIn(-maxDragPx, maxDragPx)
                                            coverOffset.snapTo(next)
                                        }
                                    },
                                    onDragStopped = { velocity ->
                                        val off = coverOffset.value
                                        when {
                                            off < -dragThresholdPx || velocity < -2800f ->
                                                PlayerController.skipNext(context)
                                            off > dragThresholdPx || velocity > 2800f ->
                                                PlayerController.skipPrevious(context)
                                        }
                                        // Возврат — в ТОМ ЖЕ scope, что и snapTo-дельты:
                                        // единая очередь main, отставший snapTo не отменит
                                        // возвратную анимацию (раньше обложка могла застрять
                                        // наклонённой на время буферизации).
                                        scope.launch {
                                            coverOffset.animateTo(
                                                0f,
                                                spring(dampingRatio = 0.78f, stiffness = 300f)
                                            )
                                        }
                                    }
                                )
                                .clickable { onOpenPlayer() },
                            contentAlignment = Alignment.Center
                        ) {
                            AlbumArtImage(
                                uri = track.displayArtUri,
                                coverUrl = track.coverUrl,
                                albumId = track.albumId,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Буферизация после скипа: затемнение + спиннер прямо на
                            // обложке — видно, что трек грузится, а не «всё зависло».
                            if (isBuffering) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f))
                                )
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FlatCircleButton(onClick = { PlayerController.togglePlayPause(context) }) {
                                Icon(
                                    imageVector = if (isPlaying) com.lmg.vk.ui.icons.LmgGlyphs.Pause28 else com.lmg.vk.ui.icons.LmgGlyphs.Play28,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            // Перемотка водой: горизонтальный свайп по пилюле —
                            // вода следует за пальцем, отпустил — seek. Тап без
                            // движения — открытие плеера, как раньше.
                            var scrubFrac by remember(track.id) {
                                androidx.compose.runtime.mutableStateOf<Float?>(null)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    // Подложка пилюли красится акцентом обложки —
                                    // экран целиком уходит в палитру трека.
                                    .background(accent.copy(alpha = 0.16f))
                                    .clickable { onOpenPlayer() }
                                    .pointerInput(track.id, track.durationMs) {
                                        if (track.durationMs <= 0L) return@pointerInput
                                        detectHorizontalDragGestures(
                                            onDragStart = { off ->
                                                scrubFrac = (off.x / size.width)
                                                    .coerceIn(0f, 1f)
                                            },
                                            onDragEnd = {
                                                scrubFrac?.let { f ->
                                                    PlayerController.seekTo(
                                                        (f * track.durationMs).toLong()
                                                    )
                                                }
                                                scrubFrac = null
                                            },
                                            onDragCancel = { scrubFrac = null },
                                            onHorizontalDrag = { change, _ ->
                                                scrubFrac = (change.position.x / size.width)
                                                    .coerceIn(0f, 1f)
                                                change.consume()
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Прогресс — «жидкость»: заливает пилюлю целиком
                                // слева направо, волнистый край дышит пока играет.
                                WaveProgressFill(
                                    durationMs = track.durationMs,
                                    accent = accent,
                                    playing = isPlaying,
                                    animate = animationsActive &&
                                        !com.lmg.vk.ui.PowerSaveMonitor.active,
                                    overrideProgress = scrubFrac,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = AppFontFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            FlatCircleButton(onClick = { PlayerController.toggleFavorite(track.id) }) {
                                Icon(
                                    imageVector = com.lmg.vk.ui.icons.LmgGlyphs.FavoriteOutline28,
                                    contentDescription = "Like",
                                    tint = if (isFavorite) Color(0xFFFF4D67) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Official VK negative feedback with a short undo window.
                        if (showWaveFeedback) {
                            Spacer(Modifier.height(14.dp))
                            val feedbackForTrack = mixFeedback.takeIf { state ->
                                when (state) {
                                    is VkMixFeedbackState.Submitting -> state.trackId == track.id
                                    is VkMixFeedbackState.UndoAvailable -> state.trackId == track.id
                                    is VkMixFeedbackState.Undoing -> state.trackId == track.id
                                    is VkMixFeedbackState.Error -> state.trackId == track.id
                                    VkMixFeedbackState.Idle -> true
                                }
                            } ?: VkMixFeedbackState.Idle
                            val undoAction = feedbackForTrack is VkMixFeedbackState.UndoAvailable ||
                                (feedbackForTrack is VkMixFeedbackState.Error && feedbackForTrack.retryUndo)
                            WaveFeedbackChip(
                                icon = if (undoAction) lmgVector(LmgDrawables.ArrowUturnLeftOutline28) else lmgVector(LmgDrawables.UnfavoriteOutline28),
                                label = when (feedbackForTrack) {
                                    is VkMixFeedbackState.Submitting -> "Sending…"
                                    is VkMixFeedbackState.UndoAvailable -> "Undo"
                                    is VkMixFeedbackState.Undoing -> "Undoing…"
                                    is VkMixFeedbackState.Error -> if (feedbackForTrack.retryUndo) {
                                        "Retry undo"
                                    } else {
                                        "Try again"
                                    }
                                    VkMixFeedbackState.Idle -> "Less"
                                },
                                tint = Color.White.copy(alpha = 0.78f),
                                enabled = feedbackForTrack !is VkMixFeedbackState.Submitting &&
                                    feedbackForTrack !is VkMixFeedbackState.Undoing,
                            ) {
                                when (feedbackForTrack) {
                                    is VkMixFeedbackState.UndoAvailable ->
                                        viewModel.undoAuraDislike(track.id)
                                    is VkMixFeedbackState.Error ->
                                        viewModel.retryVkMixFeedback(context)
                                    VkMixFeedbackState.Idle ->
                                        viewModel.dislikeAuraTrack(context, track.id)
                                    else -> Unit
                                }
                            }
                            (feedbackForTrack as? VkMixFeedbackState.Error)?.let { error ->
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    text = error.message,
                                    color = Color.White.copy(alpha = 0.76f),
                                    fontSize = 12.sp,
                                    fontFamily = AppFontFamily,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                            }
                        }

                        if (mixState is VkMixUiState.Empty || mixState is VkMixUiState.Error) {
                            VkMixInlineStatus(
                                state = mixState,
                                onRetry = { viewModel.retryVkMix(context) },
                                onAuth = onOpenAuth,
                            )
                        }

                        // ── «Дальше в волне»: следующие обложки, тап — перескок ──
                        if (upNext.isNotEmpty()) {
                            Spacer(Modifier.height(22.dp))
                            UpNextRow(
                                upNext = upNext,
                                onPlay = { index -> PlayerController.playTrack(context, index) }
                            )
                        }
                    }
                }
                }
            }
            // Мудкарточки и рекомендации живут в табе New — низ Wave свободен,
            // герой отцентрован по вертикали.
        }

    }

    if (showMixSettings) {
        ModalBottomSheet(
            onDismissRequest = { showMixSettings = false },
            sheetState = mixSettingsSheetState,
            containerColor = Color(0xFF151718),
            contentColor = Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f)),
                )
            },
        ) {
            VkMixSettingsSheet(
                state = mixState,
                accent = accent,
                onToggle = viewModel::toggleVkMixOption,
                onReset = viewModel::resetVkMixOptions,
                onApply = {
                    viewModel.applyVkMixSettings(context)
                    showMixSettings = false
                },
                onRetry = { viewModel.retryVkMix(context) },
                onAuth = {
                    showMixSettings = false
                    onOpenAuth()
                },
            )
        }
    }
}

internal fun HomeItem.toWaveTrack(): Track = Track(
    id = id,
    title = title,
    artist = displayArtist,
    albumName = album ?: "",
    uri = com.lmg.vk.engine.VkAudioIdentity.playbackUri(),
    durationMs = durationMs,
    albumId = collectionId?.hashCode()?.toLong() ?: -1L,
    coverUrl = cover,
    // artistId есть в модели — без него тап по артисту в плеере молчал
    // для треков, запущенных с главной/New.
    artists = artistId?.let {
        listOf(com.lmg.vk.engine.backend.MiniArtist(id = it, name = displayArtist))
    } ?: emptyList(),
    isExplicit = isExplicit,
    // без source резолвер стрима не знал, откуда тянуть (apple/vk) → трек не грузился
    source = source,
    genre = genre
)

@Composable
private fun VkMixInlineStatus(
    state: VkMixUiState,
    onRetry: () -> Unit,
    onAuth: () -> Unit,
) {
    val message = when (state) {
        is VkMixUiState.Empty -> "VK Mix не вернул треки"
        is VkMixUiState.Error -> state.message
        else -> null
    } ?: return

    Spacer(Modifier.height(16.dp))
    Text(
        text = message,
        color = Color.White.copy(alpha = 0.72f),
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontFamily = AppFontFamily,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 28.dp),
    )
    Spacer(Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.13f))
            .liquidClickable(pressedScale = LiquidMotion.PressButton) {
                if (state is VkMixUiState.Error && state.sessionExpired) onAuth() else onRetry()
            }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (state is VkMixUiState.Error && state.sessionExpired) "Войти" else "Повторить",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
        )
    }
}

@Composable
private fun VkMixSettingsSheet(
    state: VkMixUiState,
    accent: Color,
    onToggle: (String, String) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onAuth: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            VkMixUiState.Idle,
            VkMixUiState.Loading -> {
                CircularProgressIndicator(color = accent, strokeWidth = 3.dp)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Загружаем настройки VK Mix…",
                    color = Color.White.copy(alpha = 0.72f),
                    fontFamily = AppFontFamily,
                )
            }

            is VkMixUiState.Empty -> {
                VkMixSheetMessage(
                    title = "VK Mix пока пуст",
                    message = "VK не вернул треки с этими настройками.",
                    action = "Повторить",
                    onAction = onRetry,
                )
            }

            is VkMixUiState.Error -> {
                VkMixSheetMessage(
                    title = if (state.sessionExpired) "Сессия VK истекла" else "Не удалось загрузить VK Mix",
                    message = state.message,
                    action = if (state.sessionExpired) "Войти" else "Повторить",
                    onAction = if (state.sessionExpired) onAuth else onRetry,
                )
            }

            is VkMixUiState.Ready -> {
                val settings = state.draft
                Text(
                    text = settings?.title?.takeIf(String::isNotBlank) ?: state.session.title.ifBlank {
                        "Настроить VK Mix"
                    },
                    color = Color.White,
                    fontSize = 25.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = AppFontFamily,
                    textAlign = TextAlign.Center,
                )
                settings?.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontFamily = AppFontFamily,
                        textAlign = TextAlign.Center,
                    )
                }

                when {
                    !state.session.isTunable -> {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "Для этого микса VK не разрешил менять настройки.",
                            color = Color.White.copy(alpha = 0.68f),
                            fontFamily = AppFontFamily,
                            textAlign = TextAlign.Center,
                        )
                    }

                    settings == null -> {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "VK не вернул настройки этого микса.",
                            color = Color.White.copy(alpha = 0.68f),
                            fontFamily = AppFontFamily,
                            textAlign = TextAlign.Center,
                        )
                    }

                    else -> {
                        val visibleCategories = settings.categories.filter {
                            it.type != VkMixCategoryType.HIDDEN
                        }
                        visibleCategories.forEach { category ->
                            Spacer(Modifier.height(26.dp))
                            Text(
                                text = category.title.uppercase(),
                                color = Color.White.copy(alpha = 0.44f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppFontFamily,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(11.dp))
                            when (category.type) {
                                VkMixCategoryType.ICONS -> Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    category.options.forEach { option ->
                                        VkMixPicturedOption(
                                            option = option,
                                            accent = accent,
                                            enabled = !state.applying,
                                            onClick = { onToggle(category.id, option.id) },
                                        )
                                    }
                                }

                                VkMixCategoryType.BUTTONS -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    category.options.forEach { option ->
                                        VkMixButtonOption(
                                            option = option,
                                            accent = accent,
                                            enabled = !state.applying,
                                            onClick = { onToggle(category.id, option.id) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }

                                VkMixCategoryType.HIDDEN -> Unit
                            }
                        }

                        Spacer(Modifier.height(30.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            VkMixSheetAction(
                                label = "Сбросить",
                                enabled = settings.hasVisibleSelection() && !state.applying,
                                filled = false,
                                onClick = onReset,
                                modifier = Modifier.weight(1f),
                            )
                            VkMixSheetAction(
                                label = if (state.applying) "Применение…" else "Применить",
                                enabled = !state.applying,
                                filled = true,
                                onClick = onApply,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VkMixPicturedOption(
    option: com.lmg.vk.engine.VkMixOption,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (option.isSelected) accent.copy(alpha = 0.82f)
                    else Color.White.copy(alpha = 0.08f),
                )
                .liquidClickable(
                    enabled = enabled,
                    pressedScale = LiquidMotion.PressButton,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val iconUrl = option.icon.trim().takeIf { it.isRemoteUrl() }
            var lottieReady by remember(iconUrl) { mutableStateOf(false) }

            // Neutral fallback stays behind the transparent Lottie view. It is
            // visible only while the remote JSON is unavailable or invalid.
            if (!lottieReady) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
                )
            }

            if (iconUrl != null) {
                VkMixRemoteLottieIcon(
                    url = iconUrl,
                    optionId = option.id,
                    selected = option.isSelected,
                    onReadyChanged = { lottieReady = it },
                    modifier = Modifier.size(44.dp),
                )
            }

            option.badgeIconUrl?.trim()?.takeIf { it.isRemoteUrl() }?.let { badgeUrl ->
                AsyncImage(
                    model = badgeUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = option.title,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.55f),
            fontSize = 11.sp,
            fontWeight = if (option.isSelected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private class VkMixLottieViewState {
    var sourceKey: String? = null
    var remoteUrl: String? = null
    var sourceFile: File? = null
    var exportedSourceKey: String? = null
    var optionId: String = ""
    var selected: Boolean = false
    var renderedSelected: Boolean? = null
    var loaded: Boolean = false
}

@Composable
private fun VkMixRemoteLottieIcon(
    url: String,
    optionId: String,
    selected: Boolean,
    onReadyChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localFile by produceState<File?>(initialValue = null, url, optionId) {
        value = withContext(Dispatchers.IO) {
            VkMixLottieStore.getOrDownload(context.applicationContext, optionId, url)
        }
    }
    val file = localFile ?: return

    AndroidView(
        factory = { context ->
            LottieAnimationView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                repeatCount = 0
                setSafeMode(true)
                tag = VkMixLottieViewState()
                setFailureListener { error ->
                    val state = tag as? VkMixLottieViewState
                    state?.loaded = false
                    onReadyChanged(false)
                    state?.sourceFile?.let(VkMixLottieStore::quarantine)
                    DebugLog.add(
                        "VK MIX Lottie failed: option=${state?.optionId.orEmpty()}, " +
                            "${state?.remoteUrl.remoteDescriptor()}, ${error.javaClass.simpleName}",
                    )
                }
                addLottieOnCompositionLoadedListener { composition ->
                    val state = tag as? VkMixLottieViewState ?: return@addLottieOnCompositionLoadedListener
                    state.loaded = true
                    applyVkMixSelection(
                        composition = composition,
                        selected = state.selected,
                        animate = false,
                    )
                    state.renderedSelected = state.selected
                    onReadyChanged(true)
                    val sourceKey = state.sourceKey
                    val sourceFile = state.sourceFile
                    val remoteUrl = state.remoteUrl
                    val exportOptionId = state.optionId
                    if (
                        sourceKey != null &&
                        sourceFile != null &&
                        !remoteUrl.isNullOrBlank() &&
                        state.exportedSourceKey != sourceKey
                    ) {
                        state.exportedSourceKey = sourceKey
                        scope.launch(Dispatchers.IO) {
                            VkMixLottieStore.exportValidated(
                                context = context.applicationContext,
                                optionId = exportOptionId,
                                url = remoteUrl,
                                source = sourceFile,
                            )
                        }
                    }
                    DebugLog.add(
                        "VK MIX Lottie loaded: option=${state.optionId}, " +
                            "${state.remoteUrl.remoteDescriptor()}, frames=${composition.startFrame.toInt()}.." +
                            composition.endFrame.toInt(),
                    )
                }
            }
        },
        update = { view ->
            val state = (view.tag as? VkMixLottieViewState) ?: VkMixLottieViewState().also {
                view.tag = it
            }
            state.optionId = optionId
            val localSource = file.absolutePath
            state.remoteUrl = url
            state.sourceFile = file
            if (state.sourceKey != localSource) {
                state.sourceKey = localSource
                state.selected = selected
                state.renderedSelected = null
                state.loaded = false
                onReadyChanged(false)
                view.cancelAnimation()
                DebugLog.add(
                    "VK MIX Lottie open local: option=$optionId, file=${file.name}, " +
                        url.remoteDescriptor(),
                )
                view.setVkMixAnimation(file)
            } else if (state.selected != selected) {
                state.selected = selected
                if (state.loaded) {
                    view.composition?.let { composition ->
                        view.applyVkMixSelection(
                            composition = composition,
                            selected = selected,
                            animate = state.renderedSelected != null,
                        )
                        state.renderedSelected = selected
                    }
                }
            }
        },
        onRelease = { view ->
            view.cancelAnimation()
            view.removeAllLottieOnCompositionLoadedListener()
            view.setFailureListener(null)
            view.setImageDrawable(null)
        },
        modifier = modifier,
    )
}

private fun LottieAnimationView.setVkMixAnimation(file: File) {
    val zipped = FileInputStream(file).use { input ->
        input.read() == 0x50 && input.read() == 0x4B
    }
    val cacheKey = "vk_mix_${file.nameWithoutExtension}"
    if (zipped) {
        setAnimation(ZipInputStream(FileInputStream(file)), cacheKey)
    } else {
        setAnimation(FileInputStream(file), cacheKey)
    }
}

private fun LottieAnimationView.applyVkMixSelection(
    composition: LottieComposition,
    selected: Boolean,
    animate: Boolean,
) {
    val firstFrame = composition.startFrame.toInt().coerceAtLeast(0)
    val lastFrame = composition.endFrame.toInt().coerceAtLeast(firstFrame)
    val selectedFrame = VK_MIX_SELECTED_FRAME.coerceIn(firstFrame, lastFrame)
    cancelAnimation()
    setMinAndMaxFrame(firstFrame, selectedFrame)
    if (!animate || firstFrame == selectedFrame) {
        frame = if (selected) selectedFrame else firstFrame
        return
    }
    speed = if (selected) 1f else -1f
    frame = if (selected) firstFrame else selectedFrame
    playAnimation()
}

private fun String.isRemoteUrl(): Boolean =
    startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

/** Diagnostic shape only: never log signed query parameters or persist a concrete URL. */
private fun String?.remoteDescriptor(): String {
    if (this.isNullOrBlank()) return "url=blank"
    val uri = Uri.parse(this)
    val fileName = uri.lastPathSegment.orEmpty().substringBefore('?').takeLast(64)
    return "scheme=${uri.scheme.orEmpty()}, host=${uri.host.orEmpty()}, file=$fileName"
}

private const val VK_MIX_SELECTED_FRAME = 20

@Composable
private fun VkMixButtonOption(
    option: com.lmg.vk.engine.VkMixOption,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (option.isSelected) accent.copy(alpha = 0.76f)
                else Color.White.copy(alpha = 0.08f),
            )
            .liquidClickable(
                enabled = enabled,
                pressedScale = LiquidMotion.PressButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.title,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.55f),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = if (option.isSelected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = AppFontFamily,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VkMixSheetAction(
    label: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.White.copy(alpha = 0.06f)
                    filled -> Color.White.copy(alpha = 0.90f)
                    else -> Color.White.copy(alpha = 0.12f)
                },
            )
            .liquidClickable(
                enabled = enabled,
                pressedScale = LiquidMotion.PressButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.38f)
                filled -> Color(0xFF111315)
                else -> Color.White
            },
            fontWeight = FontWeight.Bold,
            fontFamily = AppFontFamily,
        )
    }
}

@Composable
private fun VkMixSheetMessage(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 23.sp,
        fontWeight = FontWeight.Black,
        fontFamily = AppFontFamily,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(9.dp))
    Text(
        text = message,
        color = Color.White.copy(alpha = 0.65f),
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontFamily = AppFontFamily,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(22.dp))
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.13f))
            .liquidClickable(pressedScale = LiquidMotion.PressButton, onClick = onAction)
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Text(
            text = action,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontFamily = AppFontFamily,
        )
    }
}

@Composable
private fun WaveSectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = AppFontFamily,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
    )
}

@Composable
private fun WaveTrackCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    uri: Uri? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .liquidClickable { onClick() }
    ) {
        if (uri != null) {
            AlbumArtImage(
                uri = uri,
                coverUrl = coverUrl,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WaveChartCard(
    chart: Chart,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .liquidClickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(chart.cover)
                    .crossfade(true)
                    .build(),
                contentDescription = chart.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 80f
                        )
                    )
            )
            Text(
                text = chart.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${chart.tracks.size} tracks",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FlatCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .liquidClickable(pressedScale = LiquidMotion.PressButton) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Чип активной именованной волны: «Wave by <name>» + крестик сброса на My Wave. */
@Composable
private fun WaveStationIndicator(name: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Wave by $name",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .liquidClickable(pressedScale = LiquidMotion.PressIcon) { onClear() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.CancelOutline28,
                contentDescription = "Reset to My Wave",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun WaveTopBar(
    onSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onTune: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp)
    ) {
        // Слева — профиль: аватарка пользователя, если залогинен (серый
        // человечек — только для гостя).
        val avatarUrl by com.lmg.vk.engine.backend.MusicAuth
            .avatarUrl.collectAsState()
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .liquidClickable(pressedScale = LiquidMotion.PressIcon) { onOpenProfile() },
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = com.lmg.vk.ui.icons.LmgGlyphs.UserCircleOutline28,
                    contentDescription = "Profile",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Text(
            text = "My Wave",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = AppFontFamily,
            modifier = Modifier.align(Alignment.Center)
        )

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            onTune?.let { tune ->
                Icon(
                    imageVector = com.lmg.vk.ui.icons.LmgGlyphs.SlidersOutline28,
                    contentDescription = "Tune VK Mix",
                    tint = Color.White,
                    modifier = Modifier
                        .size(25.dp)
                        .liquidClickable(pressedScale = LiquidMotion.PressIcon, onClick = tune),
                )
            }
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.SearchOutline28,
                contentDescription = "Search",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .liquidClickable(pressedScale = LiquidMotion.PressIcon) { onSearch() },
            )
        }
    }
}

@Composable
private fun BigPlayButton(loading: Boolean, accent: Color = WaveAccent, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(132.dp)
            .liquidClickable(enabled = !loading, pressedScale = LiquidMotion.PressButton) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = accent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
        } else {
            // Просто большой треугольник, без круга/подложки
            Icon(
                imageVector = com.lmg.vk.ui.icons.LmgGlyphs.Play28,
                contentDescription = "Listen",
                tint = accent,
                modifier = Modifier.size(124.dp)
            )
        }
    }
}

/** Приветствие по времени суток — для idle-состояния волны. */
private fun greetingForNow(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..22 -> "Good evening"
        else -> "Late night waves"
    }

/**
 * Сглаженный бас 0..1 — каскад «огибающая с раздельной атакой/спадом + low-pass»,
 * как у ауры-дыма. Источник — [AudioReactor] (кормится ТОЛЬКО из ExoPlayer-цепочки,
 * т.е. стриминга; на локальном JUCE уровней нет — значение остаётся 0).
 *
 * ANR/энергия: кадровый цикл живёт только пока [playing]; на паузе значение
 * плавно гасится до нуля и цикл ОСТАНАВЛИВАЕТСЯ (никаких вечных покадровых корутин).
 */
@Composable
private fun rememberSmoothedBass(animate: Boolean, playing: Boolean): State<Float> =
    produceState(0f, animate, playing) {
        if (!animate || !playing) {
            var v = value
            while (v > 0.005f) {
                withInfiniteAnimationFrameMillis {
                    v += (0f - v) * 0.12f
                    value = v
                }
            }
            value = 0f
            return@produceState
        }
        var s1 = value
        var s2 = value
        var skip = false
        val halfRate = com.lmg.vk.ui.DeviceTier.lite
        while (true) {
            withInfiniteAnimationFrameMillis {
                val target = AudioReactor.low.coerceIn(0f, 1f)
                val rate = if (target > s1) 0.08f else 0.035f
                s1 += ((target - s1) * rate).coerceIn(-0.022f, 0.022f)
                s2 += (s1 - s2) * 0.15f
                // lite: публикуем через кадр — пульс/кромки перерисовываются на ~30 Гц.
                if (!halfRate || !skip) value = s2
                skip = !skip
            }
        }
    }

/**
 * Одна строка синхронного текста под именем артиста. Загрузка/парсинг — на
 * IO/кэше LyricsParser (тот же путь, что LyricsSheet), main не блокируется.
 * Высота фиксированная — hero не прыгает, когда строка появляется/исчезает.
 */
@Composable
private fun CurrentLyricLine(track: Track) {
    val context = LocalContext.current
    var lyrics by remember(track.id) { mutableStateOf(LyricsParser.getCachedLyrics(track.id)) }
    LaunchedEffect(track.id) {
        if (lyrics == null) {
            lyrics = withContext(Dispatchers.IO) {
                runCatching {
                    LyricsParser.loadLyrics(
                        context = context,
                        uri = track.uri,
                        title = track.title,
                        artist = track.artist,
                        durationMs = track.durationMs,
                        trackId = track.id
                    )
                }.getOrNull()
            }
        }
    }
    val positionMs by PlayerController.currentPositionMs.collectAsState()
    val line = remember(lyrics, positionMs) {
        val l = lyrics
        if (l == null || !l.isSynced || l.lines.isEmpty()) null
        else {
            val i = LyricsParser.findCurrentLine(l, positionMs)
            l.lines.getOrNull(i)?.text?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = line.orEmpty(), label = "lyricLine") { text ->
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    fontFamily = AppFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Official VK Mix dislike/undo chip. */
@Composable
private fun WaveFeedbackChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .liquidClickable(
                enabled = enabled,
                pressedScale = LiquidMotion.PressButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint.copy(alpha = if (enabled) 1f else 0.45f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily
        )
    }
}

/** «Дальше в волне»: до трёх следующих обложек, тап — перескок на трек. */
@Composable
private fun UpNextRow(upNext: List<Pair<Int, Track>>, onPlay: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Next",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = AppFontFamily
        )
        upNext.forEach { (index, t) ->
            AlbumArtImage(
                uri = t.displayArtUri,
                coverUrl = t.coverUrl,
                albumId = t.albumId,
                contentDescription = t.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .liquidClickable(pressedScale = LiquidMotion.PressButton) { onPlay(index) }
            )
        }
    }
}

/**
 * Прогресс трека «жидкостью» в пилюле с названием: пройденная часть заливает
 * пилюлю НА ВСЮ ВЫСОТУ слева направо, правая кромка — живая волна (два слоя
 * с разными фазами + яркая грань). Фаза движется только пока музыка играет
 * (и не в энергосбережении) — видно, что трек идёт.
 *
 * Позиция собирается ЗДЕСЬ (не в родителе), чтобы тики позиции перерисовывали
 * только этот маленький Canvas, а не весь hero.
 */
@Composable
private fun WaveProgressFill(
    durationMs: Long,
    accent: Color,
    playing: Boolean,
    animate: Boolean,
    /** Не-null во время перемотки пальцем: вода следует за пальцем мгновенно. */
    overrideProgress: Float? = null,
    modifier: Modifier = Modifier
) {
    val positionMs by PlayerController.currentPositionMs.collectAsState()
    val rawProgress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // Позиция приходит тиками (~раз в секунду) — без сглаживания фронт ПРЫГАЕТ
    // на каждый тик («звук толкает пилюлю»). Линейная интерполяция чуть длиннее
    // тика — фронт скользит непрерывно, зависит только от времени трека.
    val animated by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(1200, easing = LinearEasing),
        label = "waveProgress"
    )
    val progress = overrideProgress ?: animated

    // Фаза течения: ~2.6 рад/с — волна спокойно ПЛЫВЁТ сама, с постоянной
    // скоростью, от музыки не зависит. Кадровый цикл живёт ТОЛЬКО пока
    // playing && animate — на паузе вода замирает, корутина стоит.
    val phase = produceState(0f, playing, animate) {
        if (!playing || !animate) return@produceState
        var last = -1L
        while (true) {
            withInfiniteAnimationFrameMillis { t ->
                if (last >= 0) value = (value + (t - last) * 0.0026f) % (WAVE_TAU * 840f)
                last = t
            }
        }
    }

    val base = lerp(accent, Color.White, 0.30f)
    Canvas(modifier) {
        if (size.width <= 1f || progress <= 0.002f) return@Canvas
        // Лёгкое покачивание уровня (±1.5dp, медленное) — вода «дышит» сама.
        val split = size.width * progress + 1.5.dp.toPx() * sin(phase.value * 0.45f)
        val waveLen = 30.dp.toPx()      // вертикальный период волны кромки
        val step = 3.dp.toPx()
        val h = size.height

        // Заливка от левого края до волнистой вертикальной кромки на split.
        fun liquid(amp: Float, ph: Float) = Path().apply {
            moveTo(0f, 0f)
            lineTo(split + amp * sin(-ph), 0f)
            var y = 0f
            while (y < h) {
                y = min(y + step, h)
                lineTo(split + amp * sin(y / waveLen * WAVE_TAU - ph), y)
            }
            lineTo(0f, h)
            close()
        }

        // Два слоя воды в ПРОТИВОФАЗЕ (плывут навстречу) — живая вода с глубиной.
        drawPath(liquid(5.dp.toPx(), phase.value), base.copy(alpha = 0.34f))
        drawPath(liquid(8.dp.toPx(), -phase.value * 0.7f + 1.7f), base.copy(alpha = 0.16f))

        // Яркая грань передней волны — читаемый «уровень» прогресса.
        val edgeAmp = 5.dp.toPx()
        val edge = Path().apply {
            moveTo(split + edgeAmp * sin(-phase.value), 0f)
            var y = 0f
            while (y < h) {
                y = min(y + step, h)
                lineTo(split + edgeAmp * sin(y / waveLen * WAVE_TAU - phase.value), y)
            }
        }
        drawPath(
            edge,
            color = lerp(accent, Color.White, 0.6f).copy(alpha = 0.85f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private val WAVE_TAU = (2.0 * PI).toFloat()

// Бледно-зелёный акцент волны (заменил жёлтый — цвет Яндекса убран).
private val WaveAccent = Color(0xFF88C088)
