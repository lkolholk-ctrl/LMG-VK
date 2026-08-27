package com.lmg.vk.ui.lyrics

import android.net.Uri
import android.provider.Settings
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.engine.LyricsFxController
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.LyricsSyncStore
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.lyrics.LyricsContent
import com.lmg.vk.engine.lyrics.LyricsRepository
import com.lmg.vk.ui.lyrics.apple.AppleKaraokeList
import com.lmg.vk.ui.lyrics.apple.AppleLyricsEventProcessor
import com.lmg.vk.ui.lyrics.apple.rememberAppleLyricsEventState
import com.lmg.vk.engine.lyrics.LyricsSource
import com.lmg.vk.engine.lyrics.LyricsSourceStore
import com.lmg.vk.engine.lyrics.LyricsDisplayStore
import com.lmg.vk.engine.lyrics.lyricsSourceTitle
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.AlbumColors
import com.lmg.vk.ui.glass.rememberAlbumColors
import com.lmg.vk.ui.player.BitChordThinSlider
import com.lmg.vk.ui.theme.AppFontFamily
import com.lmg.vk.ui.theme.VkSansDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.PlatformTextStyle
import kotlin.math.abs
import java.util.Locale


/** Насколько высоко поднимать активную строку: доля высоты экрана от верха (0.25–0.35 — верхняя треть). */
private const val ACTIVE_LINE_TOP_BIAS = 0.28f

/**
 * Доля высоты экрана сверху, в которой тап по строке перематывает трек. Ниже
 * строки уходят под растушёвку и почти не читаются — попадание по ним обычно
 * промах, а цена промаха велика.
 */
private const val SEEK_TAP_ZONE = 0.575f

/** Высота зоны заголовка со скрим-градиентом (плотная часть закрывает название+артиста). */
/** Пауза автоследования после ручного скролла: пользователь читает текст —
 *  не дёргаем список обратно, возвращаемся к активной строке через этот таймаут. */
private const val USER_SCROLL_PAUSE_MS = 4000L
private val APPLE_EMPHASIS_INTERPOLATOR = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
private val LYRICS_GLIDE_EASING = CubicBezierEasing(0.40f, 0.00f, 0.20f, 1.00f)
private const val LYRICS_GLIDE_MS = 620

/**
 * Полноэкранный караоке-экран лирики (Apple Music style).
 *
 * Фичи:
 * - Strict left alignment — все строки строго по левому краю, никаких staggered offsets
 * - Text containment — текст никогда не вылезает за края экрана
 * - Fluid gliding scroll — плавный spring-скролл без рывков
 * - Character-level fluid color bleed — посимвольное плавное закрашивание
 * - HSV-boosted фон с blur + scrim
 */
@Composable
fun LyricsScreen(
    audioFileUri: Uri?,
    lrcText: String?,
    currentPositionMs: Long,
    trackTitle: String = "",
    trackArtist: String = "",
    trackDurationMs: Long = 0L,
    albumArtUri: Uri? = null,
    coverUrl: String? = null,
    albumId: Long = -1L,
    trackId: String? = null,
    albumColors: AlbumColors? = null,
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onClose: () -> Unit = {},
    // Split-режим (альбомная ориентация, правая половина): СВОЙ фон не рисуем —
    // общий фон плеера уже под нами (иначе жёсткий вертикальный шов-«полоса»).
    splitMode: Boolean = false
) {
    val context = LocalContext.current
    val resolvedColors = albumColors ?: rememberAlbumColors(albumArtUri, coverUrl)

    val resolvedTrackId = remember(trackId, audioFileUri) {
        trackId ?: com.lmg.vk.engine.VkAudioIdentity.trackIdFromUri(audioFileUri).orEmpty()
    }

    // ── Lyrics loading ──
    val cachedLyrics = remember(resolvedTrackId) {
        LyricsParser.getCachedLyrics(resolvedTrackId)
    }

    var lyricsContent by remember { mutableStateOf<LyricsContent?>(null) }
    var lyrics by remember { mutableStateOf(cachedLyrics ?: LyricsParser.Lyrics.EMPTY) }
    var isLoading by remember { mutableStateOf(cachedLyrics == null && lrcText.isNullOrBlank()) }
    var enabledSources by remember { mutableStateOf(LyricsSourceStore.enabled(context)) }
    var showTranslations by remember { mutableStateOf(LyricsDisplayStore.translation(context)) }
    var showPronunciations by remember { mutableStateOf(LyricsDisplayStore.pronunciation(context)) }
    var sourceRevision by remember { mutableIntStateOf(0) }
    var showSources by remember { mutableStateOf(false) }
    var seekEpoch by remember { mutableLongStateOf(0L) }
    val animationsEnabled = remember(context) { systemLyricsAnimationsEnabled(context) }

    LaunchedEffect(Unit) {
        PlayerController.positionDiscontinuity.collect {
            seekEpoch++
        }
    }

    LaunchedEffect(audioFileUri, lrcText, trackTitle, trackArtist, resolvedTrackId, sourceRevision) {
        if (!lrcText.isNullOrBlank()) {
            val parsed = withContext(Dispatchers.Default) {
                LyricsParser.parseLyrics(lrcText)
            }
            lyrics = parsed
            lyricsContent = LyricsContent.Legacy(parsed)
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            LyricsRepository.load(
                context = context,
                uri = audioFileUri,
                title = trackTitle,
                artist = trackArtist,
                durationMs = trackDurationMs,
                trackId = resolvedTrackId
            )
        }
        lyricsContent = loaded
        lyrics = when (loaded) {
            is LyricsContent.Rich -> LyricsRepository.toLegacyProjection(
                loaded.document,
                trackTitle,
                trackArtist,
                loaded.sourceId,
            )
            is LyricsContent.Legacy -> loaded.lyrics
        }
        isLoading = false
    }

    // ── Time processor for line-level sync ──
    val isRichLyrics = lyricsContent is LyricsContent.Rich
    val timeProcessor = remember(lyrics, isRichLyrics) {
        if (!isRichLyrics && lyrics.lines.isNotEmpty()) LyricsTimeProcessor(lyrics) else null
    }

    // Reset processor when track changes
    LaunchedEffect(resolvedTrackId) {
        timeProcessor?.reset()
    }

    val isInterlude by timeProcessor?.isInterlude?.collectAsState() ?: remember { mutableStateOf(false) }
    val interludeProgress by timeProcessor?.interludeProgress?.collectAsState()
        ?: remember { mutableFloatStateOf(0f) }

    // ── Ручная подстройка синхры (± мс, память на трек) ──
    // Лечит кривые таймкоды источника: + = лирика раньше, − = позже.
    var syncOffsetMs by remember(resolvedTrackId) {
        mutableLongStateOf(LyricsSyncStore.get(context, resolvedTrackId))
    }
    var syncUiOpen by remember { mutableStateOf(false) }
    val wordTimingOffsetMs = if (lyrics.source == LyricsSource.APPLE_TTML.id) -100L else 0L
    // Долгий тап по строке лирики → карточка «поделиться» (как у Apple).
    var shareLine by remember { mutableStateOf<String?>(null) }
    fun adjustSync(deltaMs: Long) {
        syncOffsetMs = (syncOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        LyricsSyncStore.set(context, resolvedTrackId, syncOffsetMs)
        seekEpoch++
        // Сброс процессора: монотонный курсор не пускает позицию назад,
        // без сброса сдвиг «−» применился бы только на следующей строке.
        timeProcessor?.reset()
    }

    // ── Smooth 60/120 FPS position ticker ──
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val lyricClock = remember(resolvedTrackId) { mutableLongStateOf(currentPositionMs) }

    // Sync with coarse position only when paused — во время игры позицией
    // владеет покадровый тикер (getSmoothPositionMs), иначе грубые апдейты
    // дёргали бы плавный sweep.
    LaunchedEffect(currentPositionMs, syncOffsetMs) {
        if (!isPlaying) {
            lyricClock.longValue = currentPositionMs
            timeProcessor?.updatePosition(currentPositionMs + syncOffsetMs)
        }
    }

    // High-frequency frame-synced ticker for butter-smooth animation.
    // Цикл живёт всё время (не пересоздаётся на смене isPlaying) — состояние
    // воспроизведения проверяется ВНУТРИ кадра, чтобы не терять кадры на
    // рестарте корутины при паузе/возобновлении.
    //
    // ВАЖНО: тикер запускается с ключом Unit и держит первую лямбду, поэтому
    // напрямую он бы замкнул timeProcessor на момент старта (часто ещё null,
    // пока лирика грузится). rememberUpdatedState даёт всегда СВЕЖУЮ ссылку на
    // процессор — иначе закрас не полз бы с первого открытия.
    val currentProcessor by rememberUpdatedState(timeProcessor)

    LaunchedEffect(resolvedTrackId, isRichLyrics) {
        if (isRichLyrics) return@LaunchedEffect
        while (isActive) {
            withFrameMillis { _ ->
                if (PlayerController.isPlaying.value) {
                    lyricClock.longValue = PlayerController.getSmoothPositionMs()
                    // syncOffsetMs — state: тикер всегда читает свежий сдвиг.
                    currentProcessor?.updatePosition(lyricClock.longValue + syncOffsetMs)
                }
            }
        }
    }

    val currentLineIndex by timeProcessor?.currentLineIndex?.collectAsState() ?: remember { mutableIntStateOf(-1) }
    // Подсветка идёт по currentLineIndex, а наводка списка — по этому: он бежит
    // с упреждением, чтобы строка встала на место к началу пения.
    val scrollLineIndex by timeProcessor?.scrollLineIndex?.collectAsState() ?: remember { mutableIntStateOf(-1) }
    val visualLineIndex by remember(lyrics) {
        derivedStateOf { lyrics.lines.indexOfLast { it.timeMs <= lyricClock.longValue + syncOffsetMs } }
    }

    val lineEffect = LyricsFxController.WordEffect.FILL

    // Waiting считает сам LyricsTimeProcessor (сегментная модель): строка докрашена
    // ПОЛНОСТЬЮ + до следующей строки реальный разрыв > WAIT_GAP_MS. VAD выключен.
    val showWaiting = isInterlude

    // ── Auto-scroll с fluid gliding ──
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    // Доступная ширина строки лирики. В split (альбом/планшет) лирика живёт в
    // ПРАВОЙ половине — считаем от неё, иначе текст рассчитан на полный экран и
    // обрезается справа. Плюс левый отступ меньше (текст ближе к обложке-шву).
    val lineHPadding = if (splitMode) 16.dp else 30.dp
    val lyricColumnWidthDp = if (splitMode) (configuration.screenWidthDp * 0.5f) else configuration.screenWidthDp.toFloat()
    val lineMaxWidthPx = with(density) { (lyricColumnWidthDp.dp - lineHPadding * 2).toPx().toInt() }
    // В узкой split-колонке шрифт строк меньше, чтобы влезал без обрезки.
    val lineFontScale = if (splitMode) 0.66f else 1f
    val edgeSoftPx = with(density) { 10.dp.toPx() }

    /** Ниже этой отметки тап по строке перемотку не делает (см. обработчик тапа). */
    val seekTapLimitPx = screenHeightPx * SEEK_TAP_ZONE

    // Ручной скролл (drag) ставит автоследование на паузу — фиксируем момент касания.
    var userScrolledAt by remember { mutableLongStateOf(0L) }
    var browsing by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userScrolledAt = System.currentTimeMillis()
                browsing = true
            }
        }
    }

    LaunchedEffect(browsing, listState.isScrollInProgress) {
        if (browsing && !listState.isScrollInProgress) {
            delay(5000)
            browsing = false
        }
    }

    LaunchedEffect(scrollLineIndex, userScrolledAt) {
        if (scrollLineIndex >= 0) {
            // Если пользователь недавно листал руками — ждём остаток паузы,
            // потом плавно возвращаемся (новый drag перезапустит эффект и ожидание).
            val sinceTouch = System.currentTimeMillis() - userScrolledAt
            if (sinceTouch < USER_SCROLL_PAUSE_MS) delay(USER_SCROLL_PAUSE_MS - sinceTouch)
            // Возврат к автоследованию ждёт остановки списка. Раньше решал только
            // таймер, и если он истекал посреди инерции от броска, список дёргало
            // обратно прямо под рукой.
            while (listState.isScrollInProgress) delay(120)
            // Поднимаем активную строку в верхнюю треть (см. ACTIVE_LINE_TOP_BIAS).
            // lineToItem: нулевой элемент списка — распорка шапки, поэтому индекс
            // строки и индекс элемента не совпадают. Без сдвига список наводился
            // на ПРЕДЫДУЩУЮ строку, и подсвеченная всегда стояла ниже якоря.
            val lineIndex = scrollLineIndex.coerceAtMost((lyrics.lines.size - 1).coerceAtLeast(0))
            val targetIndex = lineIndex + 1
            // Высоту берём у самого списка, а не у экрана: в split и с вырезами
            // экранная высота не равна видимой области, и якорь промахивается.
            val viewportPx = listState.layoutInfo.viewportSize.height
                .takeIf { it > 0 }?.toFloat() ?: screenHeightPx
            val aboveCenterOffset = (viewportPx * ACTIVE_LINE_TOP_BIAS).toInt()
            if (!animationsEnabled) {
                listState.scrollToItem(
                    index = targetIndex,
                    scrollOffset = -aboveCenterOffset,
                )
            } else {
                val info = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == targetIndex }
                if (info != null) {
                    listState.animateScrollBy(
                        info.offset.toFloat() - aboveCenterOffset,
                        tween(durationMillis = LYRICS_GLIDE_MS, easing = LYRICS_GLIDE_EASING),
                    )
                } else {
                    listState.animateScrollToItem(
                        index = targetIndex,
                        scrollOffset = -aboveCenterOffset,
                    )
                }
            }
        }
    }

    // ── Duet detection ──
    val isDuet = remember(lyrics) {
        lyrics.lines.mapNotNull { it.agentId }.distinct().size > 1 || lyrics.lines.any { line ->
            line.text.contains(Regex("""\[(M|F|D|Male|Female|Duet):?\s*""", RegexOption.IGNORE_CASE))
        }
    }
    val primaryAgentId = remember(lyrics) { lyrics.lines.firstNotNullOfOrNull { it.agentId } }

    // ── Colors ──
    // Фон здесь всегда из обложки, а не из темы приложения, поэтому цвет текста
    // выбираем по яркости фона. Раньше он был белым намертво, и на светлой
    // обложке (фон поднимается почти до максимальной яркости) выходило белое по
    // белому — экран лирики про светлую тему не знал вовсе.
    // BitChord/Apple lyrics surface keeps the foreground white over its art scrim.
    val lyricInk = Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ═══ Background layers ═══
        if (!splitMode) {
            LyricsBackground(
                albumArtUri = albumArtUri,
                coverUrl = coverUrl,
                audioFileUri = audioFileUri,
                albumId = albumId,
                albumColors = resolvedColors,
                saturationBoost = LyricsTimeProcessor.SATURATION_BOOST * (2f / 3f),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Split: только мягкий скрим для читаемости, левая кромка
            // растушёвана — никакого шва с обложкой слева.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.00f to Color.Transparent,
                            0.14f to Color.Black.copy(alpha = 0.30f),
                            1.00f to Color.Black.copy(alpha = 0.42f)
                        )
                    )
            )
        }

        // ═══ Content ═══
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = lyricInk.copy(alpha = 0.5f),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                lyricsContent is LyricsContent.Rich -> {
                    val richDocument = (lyricsContent as LyricsContent.Rich).document
                    val eventProcessor = remember(richDocument) {
                        AppleLyricsEventProcessor(richDocument)
                    }
                    val eventState by rememberAppleLyricsEventState(
                        processor = eventProcessor,
                        currentPositionMs = currentPositionMs + syncOffsetMs,
                        isPlaying = isPlaying,
                        discontinuityEpoch = seekEpoch,
                        positionProvider = { PlayerController.getSmoothPositionMs() + syncOffsetMs },
                    )
                    AppleKaraokeList(
                        document = richDocument,
                        uiState = eventState,
                        currentPositionMs = eventState.currentPositionMs,
                        isPlaying = isPlaying,
                        showTranslations = showTranslations,
                        showPronunciations = showPronunciations,
                        primaryTextColor = lyricInk.copy(alpha = 0.94f),
                        unsungTextColor = lyricInk.copy(alpha = 0.18f),
                        glowColor = Color.White,
                        onSeek = { targetMs -> PlayerController.seekTo(targetMs) },
                        onShareLine = { line ->
                            val text = line.main.joinToString("") { it.text }
                            shareLine = text
                        }
                    )
                }

                lyrics.lines.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_lyrics),
                            color = lyricInk.copy(alpha = 0.5f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Header spacer
                        item { Spacer(Modifier.height(100.dp)) }

                        // Waiting dots before first line starts
                        if (lyrics.isSynced && currentLineIndex < 0 && isInterlude) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    WaitingDots(dotColor = lyricInk, progress = interludeProgress)
                                }
                            }
                        }

                        itemsIndexed(lyrics.lines) { index, line ->
                            // Несинхронная лирика — обычный текст: мельче и кучнее
                            // (32sp-строки с воздухом рассчитаны на караоке-свип,
                            // без таймкодов они просто раздувают простыню).
                            if (!lyrics.isSynced) {
                                Text(
                                    text = line.text,
                                    color = lyricInk.copy(alpha = 0.82f),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppFontFamily,
                                    lineHeight = 24.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 4.dp)
                                )
                                return@itemsIndexed
                            }

                            val sectionTitle = line.songPart?.trim()
                                ?.takeIf { part ->
                                    part.isNotEmpty() &&
                                        lyrics.lines.getOrNull(index - 1)?.songPart?.trim() != part
                                }
                            if (sectionTitle != null) {
                                Spacer(Modifier.height(18.dp))
                                Text(
                                    text = sectionTitle.uppercase(Locale.ROOT),
                                    color = lyricInk.copy(alpha = 0.55f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = AppFontFamily,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(horizontal = lineHPadding),
                                )
                            }

                            val isCurrent = index == visualLineIndex
                            val isPast = index < visualLineIndex
                            val isAlternateAgent = isDuet && line.agentId != null &&
                                primaryAgentId != null && line.agentId != primaryAgentId

                            val cleanText = line.text.replace(
                                Regex("""\[(M|F|D|Male|Female|Duet):?\s*""", RegexOption.IGNORE_CASE), ""
                            )
                            val pronunciationLayer = if (showPronunciations) {
                                line.pronunciations.preferredLyricsLayer()
                            } else null
                            val displayText = pronunciationLayer?.text?.takeIf(String::isNotBlank) ?: cleanText
                            val displayLine = remember(line, pronunciationLayer, displayText) {
                                when {
                                    pronunciationLayer == null -> line
                                    pronunciationLayer.words.isNotEmpty() -> line.copy(
                                        text = displayText,
                                        words = pronunciationLayer.words,
                                    )
                                    else -> null
                                }
                            }
                            val rtl = displayText.isRtlLyricsText()
                            val centeredAgent = line.agentType.equals("group", true) ||
                                line.agentType.equals("other", true)
                            val rowAlignment = when {
                                centeredAgent -> Alignment.CenterHorizontally
                                isAlternateAgent && rtl -> Alignment.Start
                                isAlternateAgent -> Alignment.End
                                rtl -> Alignment.End
                                else -> Alignment.Start
                            }
                            val transformOriginX = when (rowAlignment) {
                                Alignment.End -> 1f
                                Alignment.CenterHorizontally -> 0.5f
                                else -> 0f
                            }
                            val rowMaxWidthPx = if (isAlternateAgent) (lineMaxWidthPx * 0.85f).toInt()
                            else lineMaxWidthPx

                            val fillProgress = when {
                                isPast -> 1f
                                isCurrent && displayLine?.words?.isNotEmpty() == true -> 0f
                                isCurrent -> 1f
                                else -> 0f
                            }

                            val base = Color.White

                            // Глубина списка: чем дальше строка от активной, тем сильнее
                            // растворяется (ближние читаемы, дальние — «туман»). До первой
                            // строки фокус уже стоит на первой, поэтому blur виден сразу.
                            // Считаем напрямую: раньше на КАЖДУЮ строку висел свой
                            // animateFloatAsState, то есть десятки параллельных
                            // анимаций на один список. Плавность перехода даёт
                            // анимация цвета ниже, а отдельная анимация глубины
                            // только грузила прокрутку.
                            val blurFocusIndex = visualLineIndex.coerceAtLeast(0)
                            val dist = abs(index - blurFocusIndex)
                            val lineBlur by animateDpAsState(
                                targetValue = when {
                                    browsing || isCurrent -> 0.dp
                                    else -> (dist * 1.6f).coerceAtMost(7f).dp
                                },
                                animationSpec = if (animationsEnabled) androidx.compose.animation.core.spring() else snap(),
                                label = "lyricsLineBlur",
                            )
                            val lineAlpha by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = when {
                                    browsing || isCurrent -> 1f
                                    else -> (0.5f - dist * 0.06f).coerceAtLeast(0.22f)
                                },
                                animationSpec = if (animationsEnabled) androidx.compose.animation.core.spring() else snap(),
                                label = "lyricsLineAlpha",
                            )
                            val sungColor = base
                            val unsungColor = base.copy(alpha = if (isCurrent) 0.45f else 1f)

                            // Где строка стоит на экране. Держим в массиве, а не в
                            // состоянии: значение нужно только внутри обработчика
                            // касания, а состояние вызывало бы рекомпозицию строки
                            // на каждом кадре прокрутки.
                            val rowTop = remember { floatArrayOf(Float.MAX_VALUE) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { alpha = lineAlpha }
                                    .blur(lineBlur, BlurredEdgeTreatment.Unbounded)
                                    .onGloballyPositioned { rowTop[0] = it.positionInRoot().y }
                                    // Тап по строке = перемотка на неё (Apple Music).
                                    // Только для синхронной лирики.
                                    .then(
                                        // Тап = перемотка на строку; долгий тап =
                                        // карточка «поделиться» (Apple Music).
                                        if (lyrics.isSynced) Modifier.pointerInput(line.timeMs) {
                                            detectTapGestures(
                                                onTap = {
                                                    // Нижняя часть экрана перемотку не
                                                    // принимает: там строки едва видны
                                                    // под растушёвкой, и попадание по
                                                    // ним почти всегда промах, а цена
                                                    // промаха — прыжок по треку.
                                                    if (rowTop[0] < seekTapLimitPx) {
                                                        PlayerController.seekTo(line.timeMs)
                                                    }
                                                },
                                                onLongPress = { shareLine = cleanText }
                                            )
                                        }
                                        else Modifier.pointerInput(line.text) {
                                            detectTapGestures(onLongPress = { shareLine = cleanText })
                                        }
                                    )
                                    .padding(horizontal = lineHPadding, vertical = if (splitMode) 6.dp else 10.dp),
                                horizontalAlignment = rowAlignment
                            ) {
                                LyricLineSweep(
                                    text = displayText,
                                    fillProgress = fillProgress,
                                    sungColor = sungColor,
                                    unsungColor = unsungColor,
                                    isActive = isCurrent,
                                    maxWidthPx = rowMaxWidthPx,
                                    glowColor = base,
                                    effect = lineEffect,
                                    edgeSoftPx = if (isCurrent) edgeSoftPx else 0f,
                                    fontSizeSp = 27f * lineFontScale,
                                    lineHeightSp = 33f * lineFontScale,
                                    timedLine = displayLine?.takeIf { it.words.isNotEmpty() },
                                    positionState = lyricClock,
                                    positionOffsetMs = syncOffsetMs + wordTimingOffsetMs,
                                    transformOriginX = transformOriginX,
                                    animationsEnabled = animationsEnabled,
                                )
                                line.backgroundLayers.forEach { layer ->
                                    val backgroundPronunciation = if (showPronunciations) {
                                        layer.pronunciations.preferredLyricsLayer()
                                    } else null
                                    val backgroundText = backgroundPronunciation?.text
                                        ?.takeIf(String::isNotBlank) ?: layer.text
                                    val backgroundLine = remember(line, layer, backgroundPronunciation) {
                                        val timedWords = backgroundPronunciation?.words
                                            ?.takeIf { it.isNotEmpty() } ?: layer.words
                                        LyricsParser.LyricLine(
                                            timeMs = timedWords.firstOrNull()?.timeMs ?: line.timeMs,
                                            text = backgroundText,
                                            words = timedWords,
                                            endMs = timedWords.lastOrNull()?.endMs ?: line.endMs,
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    LyricLineSweep(
                                        text = backgroundText,
                                        fillProgress = if (isPast) 1f else 0f,
                                            sungColor = base.copy(alpha = 0.35f),
                                            unsungColor = base.copy(alpha = 0.16f),
                                        isActive = isCurrent,
                                        maxWidthPx = rowMaxWidthPx,
                                        glowColor = base,
                                        fontSizeSp = 20f * lineFontScale,
                                        timedLine = backgroundLine.takeIf { it.words.isNotEmpty() },
                                        positionState = lyricClock,
                                        positionOffsetMs = syncOffsetMs + wordTimingOffsetMs,
                                        transformOriginX = transformOriginX,
                                        animationsEnabled = animationsEnabled,
                                        isBackground = true,
                                    )
                                    if (showTranslations) {
                                        layer.translations.preferredLyricsLayer()?.text
                                            ?.takeIf { !it.equals(backgroundText, true) }
                                            ?.let { translation ->
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    text = translation,
                                                    color = base.copy(alpha = if (isCurrent) 0.46f else 0.28f),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFamily = AppFontFamily,
                                                    lineHeight = 18.sp,
                                                )
                                            }
                                    }
                                }
                                val secondaryLines = buildList {
                                    if (pronunciationLayer != null && !cleanText.equals(displayText, true)) {
                                        add(cleanText)
                                    }
                                    if (showTranslations) {
                                        line.translations.preferredLyricsLayer()?.text
                                            ?.takeIf { translation ->
                                                !translation.equals(displayText, true) &&
                                                    none { it.equals(translation, true) }
                                            }
                                            ?.let(::add)
                                    }
                                }
                                secondaryLines.forEach { secondary ->
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        text = secondary,
                                        color = base.copy(alpha = if (isCurrent) 0.72f else 0.38f),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = AppFontFamily,
                                        lineHeight = 21.sp,
                                        modifier = Modifier.widthIn(
                                            max = with(density) { rowMaxWidthPx.toDp() }
                                        ),
                                    )
                                }
                                // Точки ожидания во время инструментального проигрыша
                                // (сегментная модель LyricsTimeProcessor, VAD не используется).
                                // progress: точки наливаются по мере проигрыша и схлопываются
                                // перед возвратом вокала.
                                if (isCurrent && showWaiting) {
                                    Spacer(Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        WaitingDots(
                                            dotColor = lyricInk,
                                            progress = interludeProgress
                                        )
                                    }
                                }
                            }
                        }

                        // Нижняя распорка. 200dp не хватало: под лирикой лежит
                        // растушёвка высотой 460dp, и последние строки физически
                        // нельзя было поднять из-под неё — они дочитывались уже
                        // в затемнении.
                        item { Spacer(Modifier.height((configuration.screenHeightDp * 0.42f).dp)) }
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(width = 38.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumArtImage(
                    uri = albumArtUri,
                    audioFileUri = audioFileUri,
                    albumId = albumId,
                    coverUrl = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (splitMode) 42.dp else 54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        ),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = trackTitle,
                        color = lyricInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = trackArtist,
                        color = lyricInk.copy(alpha = 0.62f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                if (!splitMode) {
                    LyricsHeaderButton(onClick = onFavoriteClick) {
                        Icon(
                            imageVector = if (isFavorite) com.lmg.vk.ui.icons.LmgGlyphs.Favorite28
                            else com.lmg.vk.ui.icons.LmgGlyphs.FavoriteOutline28,
                            contentDescription = stringResource(R.string.action_like),
                            tint = lyricInk,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    LyricsHeaderButton(onClick = onMoreClick) {
                        Icon(
                            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.MoreHorizontal28,
                            contentDescription = stringResource(R.string.track_actions),
                            tint = lyricInk,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }

            // ── Подстройка синхры: бейдж SYNC в правом верхнем углу ──
            // Тап раскрывает чипы −0.5s / +0.5s / Reset; сдвиг хранится на трек.
            if (lyrics.isSynced) {
                // Авто-скрытие чипов через 6с бездействия.
                LaunchedEffect(syncUiOpen, syncOffsetMs) {
                    if (syncUiOpen) {
                        delay(6000)
                        syncUiOpen = false
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 100.dp, end = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    val badgeActive = syncUiOpen || syncOffsetMs != 0L
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { syncUiOpen = !syncUiOpen }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (syncOffsetMs == 0L) stringResource(R.string.sync_chip)
                                   else stringResource(R.string.sync_chip_offset, syncOffsetMs / 1000f),
                            color = Color.White.copy(alpha = if (badgeActive) 0.95f else 0.45f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = com.lmg.vk.ui.icons.LmgGlyphs.ChevronDownOutline28,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = if (badgeActive) 0.95f else 0.45f),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    if (syncUiOpen) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SyncChip("−0.5s") { adjustSync(-500L) }
                            SyncChip("+0.5s") { adjustSync(+500L) }
                            SyncChip(stringResource(R.string.action_reset)) { adjustSync(-syncOffsetMs) }
                        }
                    }
                }
            }

            if (lyrics.lines.isNotEmpty() && !splitMode) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LyricsProgress(
                        positionMs = currentPositionMs,
                        durationMs = trackDurationMs,
                        color = lyricInk,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.lyrics_by_source, lyrics.source.lyricsSourceTitle()),
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.10f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showSources = true }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        // Оверлей «поделиться строкой лирики» (долгий тап по строке).
        shareLine?.let { txt ->
            LyricShareOverlay(
                lineText = txt,
                trackTitle = trackTitle,
                trackArtist = trackArtist,
                albumArtUri = albumArtUri,
                coverUrl = coverUrl,
                albumColors = resolvedColors,
                onDismiss = { shareLine = null }
            )
        }

        if (showSources) {
            LyricsSourcesDialog(
                selected = enabledSources,
                showTranslations = showTranslations,
                showPronunciations = showPronunciations,
                onToggle = { source ->
                    val updated = if (source in enabledSources) enabledSources - source
                    else enabledSources + source
                    enabledSources = updated
                    LyricsSourceStore.setEnabled(context, updated)
                    sourceRevision++
                },
                onTranslationToggle = {
                    showTranslations = !showTranslations
                    LyricsDisplayStore.setTranslation(context, showTranslations)
                },
                onPronunciationToggle = {
                    showPronunciations = !showPronunciations
                    LyricsDisplayStore.setPronunciation(context, showPronunciations)
                },
                onDismiss = { showSources = false },
            )
        }
    }
}

@Composable
private fun LyricsHeaderButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun LyricsProgress(
    positionMs: Long,
    durationMs: Long,
    color: Color,
) {
    val actualProgress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f
    var scrubbing by remember { mutableStateOf(false) }
    var shown by remember { mutableFloatStateOf(actualProgress) }
    LaunchedEffect(actualProgress, scrubbing) {
        if (!scrubbing) shown = actualProgress
    }
    BitChordThinSlider(
        value = shown,
        onValueChange = {
            scrubbing = true
            shown = it
        },
        onValueChangeFinished = {
            if (durationMs > 0L) PlayerController.seekTo((durationMs * shown).toLong())
            scrubbing = false
        },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .offset(y = (-9).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val shownPosition = (durationMs * shown).toLong()
        Text(formatLyricsTime(shownPosition), color = color.copy(alpha = 0.55f), fontSize = 11.sp)
        Text(
            "-${formatLyricsTime((durationMs - shownPosition).coerceAtLeast(0L))}",
            color = color.copy(alpha = 0.55f),
            fontSize = 11.sp,
        )
    }
}

private fun formatLyricsTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

private fun systemLyricsAnimationsEnabled(context: android.content.Context): Boolean = runCatching {
    val resolver = context.contentResolver
    Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) > 0f &&
        Settings.Global.getFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f) > 0f &&
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
}.getOrDefault(true)

private fun Map<String, LyricsParser.LyricLayer>.preferredLyricsLayer(): LyricsParser.LyricLayer? {
    if (isEmpty()) return null
    val locale = Locale.getDefault()
    val language = locale.language.lowercase(Locale.ROOT)
    val tag = locale.toLanguageTag().lowercase(Locale.ROOT)
    return entries.firstOrNull { it.key.lowercase(Locale.ROOT) == tag }?.value
        ?: entries.firstOrNull {
            val key = it.key.lowercase(Locale.ROOT)
            key == language || key.startsWith("$language-")
        }?.value
        ?: values.firstOrNull()
}

private fun String.isRtlLyricsText(): Boolean {
    var index = 0
    while (index < length) {
        val codePoint = Character.codePointAt(this, index)
        when (Character.getDirectionality(codePoint)) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return false
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return true
        }
        index += Character.charCount(codePoint)
    }
    return false
}

@Composable
private fun LyricsSourcesDialog(
    selected: Set<LyricsSource>,
    showTranslations: Boolean,
    showPronunciations: Boolean,
    onToggle: (LyricsSource) -> Unit,
    onTranslationToggle: () -> Unit,
    onPronunciationToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_sources_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.lyrics_sources_description),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                LyricsSource.entries.forEach { source ->
                    val checked = source in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onToggle(source) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(source.title, fontWeight = FontWeight.SemiBold)
                            Text(source.description, fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(
                            text = if (checked) "✓" else "",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                LyricsOptionRow(
                    title = stringResource(R.string.lyrics_translation),
                    description = stringResource(R.string.lyrics_translation_description),
                    checked = showTranslations,
                    onClick = onTranslationToggle,
                )
                LyricsOptionRow(
                    title = stringResource(R.string.lyrics_pronunciation),
                    description = stringResource(R.string.lyrics_pronunciation_description),
                    checked = showPronunciations,
                    onClick = onPronunciationToggle,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        },
    )
}

@Composable
private fun LyricsOptionRow(
    title: String,
    description: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 12.sp, color = Color.Gray)
        }
        Text(
            text = if (checked) "✓" else "",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Чип панели подстройки синхры лирики. */
@Composable
private fun SyncChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

/**
 * Line-level караоке-sweep v2 — корректно под перенос строк.
 *
 * Рисуем текст дважды: снизу inactive, сверху active, обрезанный [clipPath]
 * по визуальным рядам в порядке чтения (пройденные ряды — целиком, текущий —
 * до курсора, будущие — пусто). Одна непрерывная волна, без «подстрок».
 */
@Composable
internal fun LyricLineSweep(
    text: String,
    fillProgress: Float,
    sungColor: Color,
    unsungColor: Color,
    isActive: Boolean,
    maxWidthPx: Int,
    glowColor: Color,
    effect: LyricsFxController.WordEffect = LyricsFxController.WordEffect.FILL,
    edgeSoftPx: Float = 0f,
    fontSizeSp: Float = 32f,
    lineHeightSp: Float = fontSizeSp * 1.375f,
    timedLine: LyricsParser.LyricLine? = null,
    positionState: MutableLongState? = null,
    positionOffsetMs: Long = 0L,
    transformOriginX: Float = 0f,
    animationsEnabled: Boolean = true,
    isBackground: Boolean = false,
) {
    if (text.isEmpty()) return

    val measurer = rememberTextMeasurer()
    // Стиль ОДИН для активной и неактивной строки: смена 30↔32sp была
    // перевёрсткой (другие переносы, прыжок высоты — дёргался весь список).
    // «Укрупнение» активной строки теперь чисто визуальное — spring-scale
    // через graphicsLayer ниже, вёрстка не меняется никогда.
    val style = TextStyle(
        fontSize = fontSizeSp.sp,
        // BitChord uses its 800-weight display face for the lyric panel.
        fontWeight = FontWeight.ExtraBold,
        fontFamily = VkSansDisplay,
        lineHeight = lineHeightSp.sp,
        letterSpacing = (-0.7).sp,
        textAlign = TextAlign.Start,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val layout = remember(text, style, maxWidthPx) {
        measurer.measure(
            text = text,
            style = style,
            constraints = Constraints(maxWidth = maxWidthPx),
            maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }

    val density = LocalDensity.current
    val wDp = with(density) { layout.size.width.toDp() }
    val hDp = with(density) { layout.size.height.toDp() }

    val lineScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isActive && !isBackground) 1.04f else 1f,
        animationSpec = if (animationsEnabled) androidx.compose.animation.core.spring() else snap(),
        label = "lyricScale",
    )

    Canvas(
        modifier = Modifier
            .size(wDp, hDp)
            .graphicsLayer {
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(transformOriginX, 0.5f)
            }
    ) {
        if (timedLine != null && positionState != null) {
            val position = positionState.longValue + positionOffsetMs
            drawText(layout, color = unsungColor)
            drawTimedLyric(
                layout = layout,
                line = timedLine,
                positionMs = position,
                text = text,
                sungColor = sungColor,
                featherPx = 0f,
            )
            return@Canvas
        }

        drawText(layout, color = unsungColor)

        val p = fillProgress.coerceIn(0f, 1f)
        if (p <= 0f) return@Canvas

        // 2) активный текст, обрезанный по «спетой» области в порядке чтения.
        //    Заливка считается НЕПРЕРЫВНО по суммарной ширине рядов (sub-pixel),
        //    а не по целым символам — поэтому плавно, без «лесенки».
        if (p >= 1f) {
            drawText(layout, color = sungColor)
            return@Canvas
        }

        var totalWidth = 0f
        for (i in 0 until layout.lineCount) {
            totalWidth += layout.getLineRight(i) - layout.getLineLeft(i)
        }
        val swept = p * totalWidth

        // FILL без мягкого края — ПРЕЖНИЙ жёсткий sweep (line-level не меняется).
        if (effect == LyricsFxController.WordEffect.FILL && edgeSoftPx <= 0f) {
            val clip = Path()
            var acc = 0f
            for (i in 0 until layout.lineCount) {
                val left = layout.getLineLeft(i)
                val right = layout.getLineRight(i)
                val top = layout.getLineTop(i)
                val bottom = layout.getLineBottom(i)
                val w = right - left
                when {
                    swept >= acc + w -> clip.addRect(Rect(left, top, right, bottom))
                    swept <= acc -> { }
                    else -> clip.addRect(Rect(left, top, left + (swept - acc), bottom))
                }
                acc += w
            }
            clipPath(clip) { drawText(layout, color = sungColor) }
            return@Canvas
        }

        // Мягкий край / эффекты (word-level): построчно с фезером у курсора.
        val soft = (if (effect == LyricsFxController.WordEffect.FADE) edgeSoftPx * 2.2f else edgeSoftPx)
            .coerceAtLeast(1f)
        var acc = 0f
        for (i in 0 until layout.lineCount) {
            val left = layout.getLineLeft(i)
            val right = layout.getLineRight(i)
            val top = layout.getLineTop(i)
            val bottom = layout.getLineBottom(i)
            val w = right - left
            val rowStart = acc
            val rowEnd = acc + w
            when {
                swept >= rowEnd ->
                    clipRect(left, top, right, bottom) { drawText(layout, color = sungColor) }
                swept <= rowStart -> { /* ряд ещё не начат */ }
                else -> {
                    val edge = left + (swept - rowStart)            // курсор в ряду
                    val hard = (edge - soft).coerceAtLeast(left)
                    if (hard > left) clipRect(left, top, hard, bottom) { drawText(layout, color = sungColor) }
                    if (edge > hard) {
                        val feather = Brush.horizontalGradient(
                            0f to sungColor, 1f to sungColor.copy(alpha = 0f),
                            startX = hard, endX = edge
                        )
                        clipRect(hard, top, edge, bottom) { drawText(layout, brush = feather) }
                    }
                    if (effect == LyricsFxController.WordEffect.RUNNING) {
                        val gL = (edge - soft * 0.7f).coerceAtLeast(left)
                        val gR = (edge + soft * 0.5f).coerceAtMost(right)
                        if (gR > gL) {
                            val glow = Brush.horizontalGradient(
                                0f to glowColor.copy(alpha = 0f),
                                0.5f to glowColor.copy(alpha = 0.85f),
                                1f to glowColor.copy(alpha = 0f),
                                startX = gL, endX = gR
                            )
                            clipRect(gL, top, gR, bottom) { drawText(layout, brush = glow) }
                        }
                    }
                }
            }
            acc += w
        }
    }
}

private fun DrawScope.drawTimedLyric(
    layout: androidx.compose.ui.text.TextLayoutResult,
    line: LyricsParser.LyricLine,
    positionMs: Long,
    text: String,
    sungColor: Color,
    featherPx: Float,
) {
    val wordEnd = line.wordEndMs()
    when {
        positionMs >= wordEnd ->
            drawText(layout, color = sungColor)
        positionMs > line.timeMs -> drawAppleSweptText(
            layout = layout,
            // Karaoke timing is semantic playback feedback, so it stays smooth
            // even when Android's decorative animation scale is disabled.
            revealedChars = line.revealedChars(positionMs, text, animated = true),
            color = sungColor,
            featherPx = featherPx,
            rushProgress = 0f,
        )
    }
}

private data class LyricEmphasis(
    val start: Int,
    val end: Int,
    val scale: Float,
    val liftPx: Float,
)

private data class LyricLift(
    val start: Int,
    val end: Int,
    val liftPx: Float,
)

private data class WordMotion(
    val stretch: LyricEmphasis?,
    val lifts: List<LyricLift>,
)

private data class EmphasisGroup(
    val start: Int,
    val end: Int,
    val startMs: Long,
    val endMs: Long,
    val stretchable: Boolean,
    val wordSpans: List<Pair<Int, Int>>,
)

private data class ResolvedLyricWord(
    val start: Int,
    val end: Int,
    val timeMs: Long,
    val endMs: Long,
)

private const val EMPHASIS_GROUP_MAX_MS = 3_000L
private const val LONG_NOTE_MS = 1_000L
private const val EMPHASIS_TAIL_MS = 300L
private const val STAGGER_MAX_MS = 500L
private const val RELEASE_TIME_SCALE = 1.35f
private const val LIFT_SETTLE_MS = 2_200L

private val LIFT_ZETA = 0.93f
private val LIFT_OMEGA = sqrt(25f)
private val LIFT_OMEGA_D = LIFT_OMEGA * sqrt(1f - LIFT_ZETA * LIFT_ZETA)

private fun liftSpringStep(tMs: Float): Float {
    val t = (tMs.coerceAtLeast(0f)) / 1000f
    val decay = exp(-LIFT_ZETA * LIFT_OMEGA * t)
    return 1f - decay * (cos(LIFT_OMEGA_D * t) +
        (LIFT_ZETA * LIFT_OMEGA / LIFT_OMEGA_D) * sin(LIFT_OMEGA_D * t))
}

private fun liftSpringStepVelocity(tMs: Float): Float {
    val t = (tMs.coerceAtLeast(0f)) / 1000f
    return exp(-LIFT_ZETA * LIFT_OMEGA * t) * sin(LIFT_OMEGA_D * t) *
        (LIFT_OMEGA_D + LIFT_ZETA * LIFT_ZETA * LIFT_OMEGA * LIFT_OMEGA / LIFT_OMEGA_D)
}

private fun liftSpringRelease(position: Float, velocityPerSec: Float, tMs: Float): Float {
    val t = (tMs.coerceAtLeast(0f)) / 1000f
    val zetaOmega = LIFT_ZETA * LIFT_OMEGA
    val decay = exp(-zetaOmega * t)
    return decay * (position * cos(LIFT_OMEGA_D * t) +
        ((velocityPerSec + zetaOmega * position) / LIFT_OMEGA_D) * sin(LIFT_OMEGA_D * t))
}

private fun wordLiftFraction(nowMs: Long, onMs: Long, offMs: Long): Float = when {
    nowMs < onMs -> 0f
    nowMs <= offMs -> liftSpringStep((nowMs - onMs).toFloat())
    else -> {
        val held = (offMs - onMs).coerceAtLeast(0L).toFloat()
        liftSpringRelease(
            liftSpringStep(held),
            liftSpringStepVelocity(held),
            (nowMs - offMs).toFloat() / RELEASE_TIME_SCALE,
        )
    }
}

private fun LyricsParser.LyricLine.buildEmphasisGroups(
    resolved: List<ResolvedLyricWord>,
    renderedText: String,
): List<EmphasisGroup> {
    if (resolved.isEmpty()) return emptyList()
    val groups = mutableListOf<EmphasisGroup>()
    var index = 0
    while (index < resolved.size) {
        var last = index
        val firstWord = resolved[index]
        var startMs = firstWord.timeMs
        var endMs = firstWord.endMs
        if (endMs - startMs < LONG_NOTE_MS) {
            while (last + 1 < resolved.size) {
                val next = resolved[last + 1]
                if (next.endMs - next.timeMs >= LONG_NOTE_MS) break
                val mergedEnd = maxOf(endMs, next.endMs)
                if (mergedEnd - startMs > EMPHASIS_GROUP_MAX_MS) break
                last += 1
                endMs = mergedEnd
            }
        }
        val start = firstWord.start
        val end = resolved[last].end
        groups += if (start in 0 until end && end <= renderedText.length) {
            val groupText = renderedText.substring(start, end)
            val duration = endMs - startMs
            EmphasisGroup(
                start = start,
                end = end,
                startMs = startMs,
                endMs = endMs,
                stretchable = duration >= LONG_NOTE_MS &&
                    groupText.codePointCount(0, groupText.length) <= 7 &&
                    !groupText.hasNonStretchScript(),
                wordSpans = (index..last).map { offset ->
                    resolved[offset].start to resolved[offset].end
                },
            )
        } else {
            EmphasisGroup(start, end, startMs, endMs, stretchable = false, wordSpans = emptyList())
        }
        index = last + 1
    }
    return groups
}

private fun evaluateWordMotion(
    groups: List<EmphasisGroup>,
    positionMs: Long,
    density: androidx.compose.ui.unit.Density,
    animated: Boolean,
): WordMotion {
    if (groups.isEmpty()) return WordMotion(null, emptyList())
    val unitLiftPx = with(density) { (-2.5).dp.toPx() }
    var stretch: LyricEmphasis? = null
    val lifts = mutableListOf<LyricLift>()
    for (group in groups) {
        if (positionMs < group.startMs ||
            positionMs > group.endMs + LIFT_SETTLE_MS ||
            group.wordSpans.isEmpty()
        ) continue
        if (group.stretchable) {
            val duration = (group.endMs - group.startMs).coerceAtLeast(1L)
            val fraction = when {
                !animated -> 1f
                positionMs <= group.endMs -> appleEmphasisCurve(
                    ((positionMs - group.startMs).toFloat() / duration).coerceIn(0f, 1f)
                )
                else -> 1f - appleEmphasisCurve(
                    ((positionMs - group.endMs).toFloat() / EMPHASIS_TAIL_MS).coerceIn(0f, 1f)
                )
            }
            if (fraction <= 0f) continue
            val durationSeconds = (duration / 1_000f).coerceIn(1f, 2f)
            val maxScale = 1f + 0.14f * (durationSeconds - 1f)
            val hold = if (animated) wordLiftFraction(positionMs, group.startMs, group.endMs) else 1f
            stretch = LyricEmphasis(
                start = group.start,
                end = group.end,
                scale = 1f + (maxScale - 1f) * fraction,
                liftPx = unitLiftPx * hold,
            )
        } else {
            if (!animated) continue
            val count = group.wordSpans.size
            val stagger = (((group.endMs - group.startMs) * 0.5f) / count)
                .coerceAtMost(STAGGER_MAX_MS.toFloat())
            for ((wordIndex, span) in group.wordSpans.withIndex()) {
                val on = group.startMs + (wordIndex * stagger).toLong()
                val fraction = wordLiftFraction(positionMs, on, group.endMs)
                if (abs(fraction) > 0.002f) {
                    lifts += LyricLift(span.first, span.second, unitLiftPx * fraction)
                }
            }
        }
    }
    return WordMotion(stretch, lifts)
}

private fun LyricsParser.LyricLine.resolvedWords(renderedText: String): List<ResolvedLyricWord> {
    var offset = 0
    return words.mapIndexed { index, word ->
        val start = word.charStart.takeIf { it in 0 until renderedText.length }
            ?: renderedText.indexOf(word.text, offset).takeIf { it >= 0 }
            ?: offset.coerceAtMost(renderedText.length)
        val end = word.charEnd.takeIf { it in (start + 1)..renderedText.length }
            ?: (start + word.text.length).coerceAtMost(renderedText.length)
        val next = words.getOrNull(index + 1)
        val resolvedEnd = word.endMs.takeIf { it > word.timeMs }
            ?: next?.timeMs?.takeIf { it > word.timeMs }
            ?: endMs.takeIf { it > word.timeMs }
            ?: (word.timeMs + (word.text.length * 72L).coerceIn(500L, 5_000L))
        offset = end
        ResolvedLyricWord(start, end, word.timeMs, resolvedEnd)
    }
}

private fun appleEmphasisCurve(value: Float): Float {
    return APPLE_EMPHASIS_INTERPOLATOR.getInterpolation(value.coerceIn(0f, 1f))
}

private fun String.hasNonStretchScript(): Boolean {
    var index = 0
    while (index < length) {
        val codePoint = Character.codePointAt(this, index)
        when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.ARABIC,
            Character.UnicodeScript.THAI,
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.BOPOMOFO -> return true
            else -> Unit
        }
        index += Character.charCount(codePoint)
    }
    return false
}

private fun DrawScope.drawAppleWordMotion(
    layout: androidx.compose.ui.text.TextLayoutResult,
    motion: WordMotion,
    drawLayer: DrawScope.() -> Unit,
) {
    val textLength = layout.layoutInput.text.length

    fun clearRange(start: Int, end: Int) {
        val from = start.coerceIn(0, textLength)
        val to = end.coerceIn(from, textLength)
        if (to <= from) return
        drawPath(layout.getPathForRange(from, to), Color.Transparent, blendMode = BlendMode.Clear)
    }

    for (lift in motion.lifts) {
        val start = lift.start.coerceIn(0, textLength)
        val end = lift.end.coerceIn(start, textLength)
        if (end <= start) continue
        clearRange(start, end)
        withTransform({ translate(top = lift.liftPx) }) {
            clipPath(layout.getPathForRange(start, end)) { drawLayer() }
        }
    }

    val emphasis = motion.stretch ?: return
    val start = emphasis.start.coerceIn(0, textLength)
    val end = emphasis.end.coerceIn(start, textLength)
    if (end <= start) return
    val sourceText = layout.layoutInput.text
    val firstBox = layout.getBoundingBox(start)
    val lastBox = layout.getBoundingBox((end - 1).coerceAtLeast(start))
    val anchorX = firstBox.left + (lastBox.right - firstBox.left) * 0.4f
    val spanX = (lastBox.right - anchorX).coerceAtLeast(1f)
    val visualLine = layout.getLineForOffset(start.coerceAtMost((textLength - 1).coerceAtLeast(0)))
    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
    val afterPath = if (end < lineEnd) layout.getPathForRange(end, lineEnd) else null
    clearRange(start, end)
    afterPath?.let { clearRange(end, lineEnd) }
    var totalGrow = 0f
    withTransform({ translate(top = emphasis.liftPx) }) {
        var offset = start
        var cursorX = 0f
        var rowTop = Float.NaN
        while (offset < end) {
            val step = Character.charCount(Character.codePointAt(sourceText, offset))
            val rangeEnd = (offset + step).coerceAtMost(end)
            val box = layout.getBoundingBox(offset)
            if (box.top != rowTop) {
                rowTop = box.top
                cursorX = box.left
            }
            val center = (box.left + box.right) * 0.5f
            val t = ((center - anchorX) / spanX).coerceIn(0f, 1f)
            val wave = t * t * (3f - 2f * t)
            val charScale = 1f + (emphasis.scale - 1f) * wave
            val charBase = layout.getLineBaseline(layout.getLineForOffset(offset))
            withTransform({
                translate(left = cursorX, top = 0f)
                scale(
                    scaleX = charScale,
                    scaleY = charScale,
                    pivot = Offset(cursorX, charBase),
                )
            }) {
                clipPath(layout.getPathForRange(offset, rangeEnd)) { drawLayer() }
            }
            totalGrow += box.width * (charScale - 1f)
            cursorX += box.width * charScale
            offset = rangeEnd
        }
    }
    afterPath?.let { path ->
        withTransform({ translate(left = totalGrow) }) { clipPath(path) { drawLayer() } }
    }
}

private fun LyricsParser.LyricLine.wordEndMs(): Long {
    val last = words.lastOrNull() ?: return endMs.coerceAtLeast(timeMs)
    return last.endMs.takeIf { it > last.timeMs }
        ?: endMs.takeIf { it > last.timeMs }
        ?: (last.timeMs + (last.text.length * 72L).coerceIn(500L, 5_000L))
}

private fun LyricsParser.LyricLine.revealedChars(
    positionMs: Long,
    renderedText: String,
    animated: Boolean,
): Float {
    if (words.isEmpty()) return if (positionMs >= timeMs) renderedText.length.toFloat() else 0f
    var offset = 0
    words.forEachIndexed { index, word ->
        val start = word.charStart.takeIf { it in 0..renderedText.length }
            ?: renderedText.indexOf(word.text, offset).takeIf { it >= 0 }
            ?: offset
        val end = word.charEnd.takeIf { it in (start + 1)..renderedText.length }
            ?: (start + word.text.length).coerceAtMost(renderedText.length)
        val next = words.getOrNull(index + 1)
        val wordEnd = word.endMs.takeIf { it > word.timeMs }
            ?: next?.timeMs?.takeIf { it > word.timeMs }
            ?: endMs.takeIf { it > word.timeMs }
            ?: (word.timeMs + (word.text.length * 72L).coerceIn(500L, 5_000L))
        if (positionMs < word.timeMs) return start.toFloat()
        if (positionMs < wordEnd) {
            if (!animated) return end.toFloat()
            val span = (wordEnd - word.timeMs).coerceAtLeast(1L)
            val through = (positionMs - word.timeMs).toFloat() / span
            return start + through * (end - start)
        }
        if (next != null && positionMs < next.timeMs) {
            if (!animated) return end.toFloat()
            val gapStart = next.charStart.takeIf { it in end..renderedText.length }
                ?: renderedText.indexOf(next.text, end).takeIf { it >= 0 }
                ?: end
            val pause = (next.timeMs - wordEnd).coerceAtLeast(1L)
            val through = (positionMs - wordEnd).toFloat() / pause
            return end + through * (gapStart - end)
        }
        offset = end
    }
    return renderedText.length.toFloat()
}

private fun horizontalAt(
    layout: androidx.compose.ui.text.TextLayoutResult,
    chars: Float,
    lineStart: Int,
    lineEnd: Int,
): Float {
    val index = chars.toInt().coerceIn(lineStart, lineEnd)
    val here = layout.getHorizontalPosition(index, usePrimaryDirection = true)
    val next = layout.getHorizontalPosition(
        (index + 1).coerceAtMost(lineEnd),
        usePrimaryDirection = true,
    )
    return here + (next - here) * (chars - index)
}

private fun DrawScope.drawAppleSweptText(
    layout: androidx.compose.ui.text.TextLayoutResult,
    revealedChars: Float,
    color: Color,
    featherPx: Float,
    rushProgress: Float,
) {
    if (revealedChars <= 0f) return
    val textLength = layout.layoutInput.text.length
    val boundary = revealedChars.coerceIn(0f, textLength.toFloat())
    val boundaryLine = if (boundary >= textLength) layout.lineCount - 1
    else layout.getLineForOffset(boundary.toInt().coerceIn(0, (textLength - 1).coerceAtLeast(0)))
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        if (visualLine > boundaryLine || boundary <= start) return
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        val left = layout.getLineLeft(visualLine)
        val right = layout.getLineRight(visualLine)
        if (visualLine < boundaryLine) {
            clipRect(left, layout.getLineTop(visualLine), right, layout.getLineBottom(visualLine)) {
                drawText(layout, color = color)
            }
            continue
        }
        val directionOffset = start.coerceIn(0, (textLength - 1).coerceAtLeast(0))
        val rtl = layout.getParagraphDirection(directionOffset) == ResolvedTextDirection.Rtl
        val edge = if (boundary >= end) {
            if (rtl) left - featherPx * rushProgress else right + featherPx * rushProgress
        } else horizontalAt(layout, boundary, start, end)
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        if (rtl) {
            val hard = edge + featherPx
            if (hard < right) {
                clipRect(hard.coerceAtLeast(left), top, right, bottom) {
                    drawText(layout, color = color)
                }
            }
            if (hard > edge) {
                val feather = Brush.horizontalGradient(
                    0f to color.copy(alpha = 0f),
                    1f to color,
                    startX = edge,
                    endX = hard,
                )
                val fadeLeft = edge.coerceAtLeast(left)
                val fadeRight = hard.coerceAtMost(right)
                if (fadeRight > fadeLeft) {
                    clipRect(fadeLeft, top, fadeRight, bottom) {
                        drawText(layout, brush = feather)
                    }
                }
            }
        } else {
            val hard = edge - featherPx
            if (hard > left) {
                clipRect(left, top, hard.coerceAtMost(right), bottom) {
                    drawText(layout, color = color)
                }
            }
            if (edge > hard) {
                val feather = Brush.horizontalGradient(
                    0f to color,
                    1f to color.copy(alpha = 0f),
                    startX = hard,
                    endX = edge,
                )
                val fadeLeft = hard.coerceAtLeast(left)
                val fadeRight = edge.coerceAtMost(right)
                if (fadeRight > fadeLeft) {
                    clipRect(fadeLeft, top, fadeRight, bottom) {
                        drawText(layout, brush = feather)
                    }
                }
            }
        }
    }
}
