package com.lmg.vk.ui.lyrics

import com.lmg.vk.ui.glass.GlassDialog
import com.lmg.vk.ui.glass.GlassDialogButton
import com.lmg.vk.ui.theme.LiquidTheme
import androidx.compose.ui.platform.LocalConfiguration
import com.lmg.vk.ui.navigation.WindowCloseSurface
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.R
import com.lmg.vk.debug.PlayerOpeningTrace
import com.lmg.vk.debug.traceLyricsOpening
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.LyricsSyncStore
import com.lmg.vk.engine.PlayerController
import com.lmg.vk.engine.VkAudioIdentity
import com.lmg.vk.engine.lyrics.*
import com.lmg.vk.ui.glass.AlbumArtImage
import com.lmg.vk.ui.glass.AlbumColors
import com.lmg.vk.ui.glass.rememberAlbumColors
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.player.BitChordThinSlider
import com.lmg.vk.ui.theme.AppFontFamily
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.SyncedLineText
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.utils.isRtl
import kotlinx.coroutines.CancellationException

/** Native Accompanist lyrics, connected to the existing LMG player and providers. */
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
    splitMode: Boolean = false,
    sharedBackground: Boolean = false,
    contentReady: Boolean = true,
    onArtworkPositioned: ((LayoutCoordinates) -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = albumColors ?: rememberAlbumColors(albumArtUri, coverUrl)
    val resolvedTrackId = trackId ?: VkAudioIdentity.trackIdFromUri(audioFileUri).orEmpty()
    val trackKey = resolvedTrackId.ifBlank { "$audioFileUri|$trackTitle|$trackArtist" }
    var enabledSources by remember { mutableStateOf(LyricsSourceStore.enabled(context)) }
    var showTranslations by remember { mutableStateOf(LyricsDisplayStore.translation(context)) }
    var showPronunciations by remember { mutableStateOf(LyricsDisplayStore.pronunciation(context)) }
    var showSources by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var showSync by remember(trackKey) { mutableStateOf(false) }
    var shareLine by remember(trackKey) { mutableStateOf<String?>(null) }
    var syncOffsetMs by remember(trackKey) { mutableLongStateOf(LyricsSyncStore.get(context, trackKey)) }
    var seekRevision by remember(trackKey) { mutableIntStateOf(0) }
    val lyricsKey = remember(trackKey, trackTitle, trackArtist, trackDurationMs, lrcText, enabledSources) {
        LoadedLyricsKey(trackKey, trackTitle, trackArtist, trackDurationMs, lrcText,
            enabledSources.toSet(), java.util.Locale.getDefault().toLanguageTag())
    }
    var data by remember(lyricsKey, revision) { mutableStateOf(LoadedLyricsStore.peek(lyricsKey)) }
    var loading by remember(lyricsKey, revision) { mutableStateOf(data == null) }
    var failed by remember(lyricsKey, revision) { mutableStateOf(false) }
    com.lmg.vk.debug.ObservePlayerStartup(trackKey to revision, "Lyrics")
    // Cold layout waits for the entrance; cached lyrics are available from the first frame.
    var contentAdmitted by remember { mutableStateOf(contentReady || data != null) }
    LaunchedEffect(contentReady) { if (contentReady) contentAdmitted = true }
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val position = rememberLyricsPosition(trackKey, currentPositionMs, isPlaying, syncOffsetMs, trackDurationMs)
    SideEffect {
        PlayerOpeningTrace.markOnce("screen_committed")
        if (contentAdmitted) PlayerOpeningTrace.markOnce("entrance_ready")
        if (data != null && !loading) PlayerOpeningTrace.markOnce("data_committed")
    }

    LaunchedEffect(lyricsKey, revision) {
        com.lmg.vk.debug.PlayerStartupTrace.mark("lyricsData=" + if (data != null) "cacheHit" else "cacheMiss")
        if (data != null) return@LaunchedEffect
        val loadTrace = com.lmg.vk.debug.PlayerStartupTrace.begin("lyrics_load")
        loading = true
        failed = false
        try {
            val appContext = context.applicationContext
            data = LoadedLyricsStore.load(lyricsKey) {
                val content = if (!lrcText.isNullOrBlank()) {
                    val trimmed = lrcText.trimStart()
                    if (trimmed.startsWith("<tt", true) ||
                        (trimmed.startsWith("<?xml", true) && trimmed.contains("<tt", true))) {
                        LyricsContent.RawTtml(lrcText, "embedded", "Embedded")
                    } else {
                        LyricsContent.Legacy(LyricsParser.parseLyrics(lrcText))
                    }
                } else {
                    LyricsRepository.load(appContext, audioFileUri, trackTitle, trackArtist,
                        trackDurationMs, resolvedTrackId, enabledSources = lyricsKey.sources)
                }
                val parseTrace = com.lmg.vk.debug.PlayerStartupTrace.begin("lyrics_parse")
                try {
                    AccompanistLyricsAdapter.convert(content, trackDurationMs)
                } finally {
                    parseTrace?.end()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DebugLog.add("Accompanist lyrics load failed: ${error.javaClass.simpleName}")
            failed = true
        } finally {
            loadTrace?.end()
            loading = false
        }
    }
    LaunchedEffect(trackKey) {
        PlayerController.positionDiscontinuity.collect { seekRevision++ }
    }

    fun adjustSync(delta: Long) {
        syncOffsetMs = (syncOffsetMs + delta).coerceIn(-10_000L, 10_000L)
        LyricsSyncStore.set(context, trackKey, syncOffsetMs)
        seekRevision++
    }

    Box(Modifier.fillMaxSize().traceLyricsOpening("screen")) {
        if (!splitMode && !sharedBackground) {
            LyricsBackground(albumArtUri, coverUrl, audioFileUri, albumId, modifier = Modifier.fillMaxSize())
        }
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(LmgGlyphs.ChevronDownOutline28, stringResource(R.string.action_close), tint = Color.White)
                }
                AlbumArtImage(
                    uri = albumArtUri, audioFileUri = audioFileUri, albumId = albumId, coverUrl = coverUrl,
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(if (splitMode) 36.dp else 48.dp).clip(RoundedCornerShape(10.dp))
                        .onGloballyPositioned { onArtworkPositioned?.invoke(it) },
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(trackTitle, color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(trackArtist, color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!splitMode) {
                    LyricsHeaderButton(onFavoriteClick) {
                        Icon(if (isFavorite) LmgGlyphs.Favorite28 else LmgGlyphs.FavoriteOutline28,
                            stringResource(R.string.action_like), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    LyricsHeaderButton(onMoreClick) {
                        Icon(LmgGlyphs.MoreHorizontal28, stringResource(R.string.track_actions),
                            tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                val loaded = data
                when {
                    loading || !contentAdmitted -> CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.Center),
                        color = Color.White, strokeWidth = 2.dp)
                    failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.lyrics_load_error), color = Color.White.copy(alpha = 0.7f))
                        TextButton(onClick = { LoadedLyricsStore.invalidate(lyricsKey); revision++ }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                    loaded == null || loaded.synced.lines.isEmpty() -> Text(
                        stringResource(R.string.no_lyrics), color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> key(trackKey, loaded) {
                        AccompanistLyricsBody(
                            data = loaded, position = position, showTranslations = showTranslations,
                            showPronunciations = showPronunciations, splitMode = splitMode,
                            seekRevision = seekRevision,
                            onSeek = { line ->
                                val target = (line.start.toLong() - syncOffsetMs).coerceAtLeast(0L)
                                PlayerController.seekTo(if (trackDurationMs > 0) target.coerceAtMost(trackDurationMs) else target)
                                seekRevision++
                            },
                            onShare = { shareLine = it.lyricText() },
                        )
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                LyricsProgress(currentPositionMs, trackDurationMs, Color.White)
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = data?.source?.takeIf { it != "none" }?.let {
                            if (it == "embedded") it.lyricsSourceTitle()
                            else stringResource(R.string.lyrics_by_source, it.lyricsSourceTitle())
                        } ?: stringResource(R.string.lyrics_sources_title),
                        color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .clickable { showSources = true }.padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                    if (data?.isSynced == true) {
                        TextButton(onClick = { showSync = true }) {
                            Text(if (syncOffsetMs == 0L) stringResource(R.string.sync_chip)
                                else stringResource(R.string.sync_chip_offset, syncOffsetMs / 1000f),
                                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        shareLine?.let { text ->
            WindowCloseSurface(onBack = { shareLine = null }) { requestBack ->
                LyricShareOverlay(text, trackTitle, trackArtist, albumArtUri, coverUrl, colors, requestBack)
            }
        }
    }
    if (showSync) {
        GlassDialog(
            visible = true,
            onDismiss = { showSync = false },
            title = stringResource(R.string.sync_chip),
            dissolveOnPrimaryClick = true,
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.sync_chip_offset, syncOffsetMs / 1000f), color = LiquidTheme.colors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = LiquidTheme.colors.accent), onClick = { adjustSync(-500L) }) { Text("-0.5s") }
                        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = LiquidTheme.colors.accent), onClick = { adjustSync(500L) }) { Text("+0.5s") }
                        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = LiquidTheme.colors.accent), onClick = { adjustSync(-syncOffsetMs) }) { Text(stringResource(R.string.action_reset)) }
                    }
                }
            },
            primaryButton = GlassDialogButton(stringResource(R.string.action_done), { showSync = false },
                backgroundColor = LiquidTheme.colors.accent),
        )
    }
    if (showSources) {
        LyricsSourcesDialog(
            selected = enabledSources, showTranslations = showTranslations, showPronunciations = showPronunciations,
            onToggle = { source ->
                enabledSources = if (source in enabledSources) enabledSources - source else enabledSources + source
                LyricsSourceStore.setEnabled(context, enabledSources)
                revision++
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

@Composable
internal fun AccompanistLyricsBody(
    data: AccompanistLyrics,
    position: () -> Int,
    showTranslations: Boolean = true,
    showPronunciations: Boolean = true,
    splitMode: Boolean = false,
    seekRevision: Int = 0,
    onSeek: (ISyncedLine) -> Unit,
    onShare: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = TextStyle(fontFamily = LyricsFontFamily, fontWeight = FontWeight.ExtraBold,
        fontSize = if (splitMode) 26.sp else 34.sp, textMotion = TextMotion.Animated)
    if (data.isSynced) {
        KaraokeLyricsView(
            listState = rememberLazyListState(), lyrics = data.synced, currentPosition = position,
            onLineClicked = onSeek, onLinePressed = onShare,
            modifier = modifier.fillMaxSize().traceLyricsOpening("body")
                .padding(horizontal = if (splitMode) 4.dp else 12.dp),
            normalLineTextStyle = style,
            accompanimentLineTextStyle = style.copy(fontSize = if (splitMode) 17.sp else 20.sp),
            phoneticTextStyle = style.copy(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Normal),
            showTranslation = showTranslations, showPhonetic = showPronunciations,
            useBlurEffect = false,
            blendMode = BlendMode.SrcOver,
            scrollResetKey = seekRevision,
            bottomContent = if (data.songwriters.isEmpty()) null else {
                { LyricsSongwriters(data.songwriters, splitMode) }
            },
        )
    } else {
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
            items(data.synced.lines) { line ->
                SyncedLineText(
                    line = SyncedLine(line.lyricText(), when (line) {
                        is SyncedLine -> line.translation
                        is com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine -> line.translation
                        else -> null
                    }, 0, 1),
                    isLineRtl = line.lyricText().isRtl(), textStyle = style, textColor = Color.White,
                    showTranslation = showTranslations,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { onShare(line) }),
                )
            }
            if (data.songwriters.isNotEmpty()) {
                item("LyricsFooter") { LyricsSongwriters(data.songwriters, splitMode) }
            }
        }
    }
}

@Composable
internal fun LyricsSongwriters(songwriters: List<String>, splitMode: Boolean = false) {
    Text(
        text = stringResource(R.string.lyrics_songwriters, songwriters.joinToString(", ")),
        color = Color.White.copy(alpha = 0.5f),
        style = TextStyle(
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = if (splitMode) 16.sp else 20.sp,
            lineHeight = if (splitMode) 23.sp else 29.sp,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
    )
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
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    GlassDialog(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.lyrics_sources_title),
        dissolveOnPrimaryClick = true,
        content = {
            Column(Modifier.heightIn(max = maxContentHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.lyrics_sources_description),
                    color = LiquidTheme.colors.textSecondary,
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
                            Text(source.title, fontWeight = FontWeight.SemiBold, color = LiquidTheme.colors.textPrimary)
                            Text(source.description, fontSize = 12.sp, color = LiquidTheme.colors.textSecondary)
                        }
                        Text(
                            text = if (checked) "✓" else "",
                            color = LiquidTheme.colors.accent,
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
        primaryButton = GlassDialogButton(stringResource(R.string.action_done), onDismiss,
            backgroundColor = LiquidTheme.colors.accent),
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
            Text(title, fontWeight = FontWeight.SemiBold, color = LiquidTheme.colors.textPrimary)
            Text(description, fontSize = 12.sp, color = LiquidTheme.colors.textSecondary)
        }
        Text(
            text = if (checked) "✓" else "",
                            color = LiquidTheme.colors.accent,
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
