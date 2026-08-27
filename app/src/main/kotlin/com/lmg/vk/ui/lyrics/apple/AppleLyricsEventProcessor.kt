package com.lmg.vk.ui.lyrics.apple

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument
import com.lmg.vk.engine.lyrics.apple.AppleLyricsLine
import com.lmg.vk.engine.lyrics.apple.AppleLyricPiece
import com.lmg.vk.engine.lyrics.apple.AppleTimingType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

data class AppleKaraokeUiState(
    val activeLineIndex: Int = -1,
    val scrollTargetLineIndex: Int = -1,
    val activeLineId: String? = null,
    val activeMainPieceIds: Set<String> = emptySet(),
    val activeBgPieceIds: Set<String> = emptySet(),
    val activePronunciationPieceIds: Set<String> = emptySet(),
    val isInterlude: Boolean = false,
    val nextEventAtMs: Long? = null,
    val currentPositionMs: Long = 0L,
    val playbackEpoch: Long = 0L
)

sealed interface AppleLyricsEvent : Comparable<AppleLyricsEvent> {
    val atMs: Long

    data class LineStart(val lineIndex: Int, val line: AppleLyricsLine, override val atMs: Long) : AppleLyricsEvent
    data class LineEnd(val lineIndex: Int, val line: AppleLyricsLine, override val atMs: Long) : AppleLyricsEvent
    data class PieceStart(val piece: AppleLyricPiece, val lineIndex: Int, override val atMs: Long) : AppleLyricsEvent
    data class PieceEnd(val piece: AppleLyricPiece, val lineIndex: Int, override val atMs: Long) : AppleLyricsEvent

    override fun compareTo(other: AppleLyricsEvent): Int {
        val cmp = atMs.compareTo(other.atMs)
        if (cmp != 0) return cmp
        // Tie-breaker: Ends before Starts at the same millisecond to avoid flicker
        return when {
            this is LineEnd && other !is LineEnd -> -1
            this !is LineEnd && other is LineEnd -> 1
            this is PieceEnd && other !is PieceEnd -> -1
            this !is PieceEnd && other is PieceEnd -> 1
            else -> 0
        }
    }
}

class AppleLyricsEventProcessor(
    val document: AppleLyricsDocument
) {
    private val lines: List<AppleLyricsLine> = document.allLines
    private val events: List<AppleLyricsEvent>

    init {
        val wordOffset = if (document.timing == AppleTimingType.WORD) -100L else 0L
        val list = mutableListOf<AppleLyricsEvent>()

        lines.forEachIndexed { index, line ->
            // Dynamic line offset: initial line uses -500ms, subsequent lines use gap calculation
            val lineLeadMs = if (index == 0) {
                500L
            } else {
                val prevEnd = lines[index - 1].endMs
                appleLineMoveDuration(prevEnd, line.beginMs).toLong()
            }

            val lineStartMs = (line.beginMs - lineLeadMs).coerceAtLeast(0L)
            list += AppleLyricsEvent.LineStart(lineIndex = index, line = line, atMs = lineStartMs)
            list += AppleLyricsEvent.LineEnd(lineIndex = index, line = line, atMs = line.endMs)

            // Main pieces
            line.main.filter { !it.isWhitespace }.forEach { piece ->
                val b = (piece.beginMs + wordOffset).coerceAtLeast(0L)
                list += AppleLyricsEvent.PieceStart(piece = piece, lineIndex = index, atMs = b)
                list += AppleLyricsEvent.PieceEnd(piece = piece, lineIndex = index, atMs = piece.endMs)
            }

            // Background pieces
            line.background.filter { !it.isWhitespace }.forEach { piece ->
                val b = (piece.beginMs + wordOffset).coerceAtLeast(0L)
                list += AppleLyricsEvent.PieceStart(piece = piece, lineIndex = index, atMs = b)
                list += AppleLyricsEvent.PieceEnd(piece = piece, lineIndex = index, atMs = piece.endMs)
            }

            // Pronunciation and pronunciation-background are separate native channels.
            (line.pronunciation + line.pronunciationBackground)
                .filter { !it.isWhitespace }
                .forEach { piece ->
                val b = (piece.beginMs + wordOffset).coerceAtLeast(0L)
                list += AppleLyricsEvent.PieceStart(piece = piece, lineIndex = index, atMs = b)
                list += AppleLyricsEvent.PieceEnd(piece = piece, lineIndex = index, atMs = piece.endMs)
            }
        }

        events = list.sorted()
    }

    /**
     * Analytically evaluates the active state for any [positionMs].
     * It does not replay the event history, so seek/scrubbing is deterministic.
     */
    fun evaluateAt(positionMs: Long, epoch: Long = 0L): AppleKaraokeUiState {
        if (lines.isEmpty() || document.timing == AppleTimingType.NONE) {
            return AppleKaraokeUiState(
                currentPositionMs = positionMs,
                playbackEpoch = epoch,
            )
        }

        val wordOffset = if (document.timing == AppleTimingType.WORD) -100L else 0L

        // Singing state and pre-lead scroll state are deliberately separate. Apple can
        // relocate the next line before it becomes the active lyric callback.
        val activeIdx = lines.indexOfLast { line ->
            positionMs >= line.beginMs && positionMs < line.endMs
        }
        var scrollTargetIdx = -1
        lines.forEachIndexed { index, line ->
            val lead = if (index == 0) 500L else {
                appleLineMoveDuration(lines[index - 1].endMs, line.beginMs).toLong()
            }
            if (positionMs >= (line.beginMs - lead).coerceAtLeast(0L)) {
                scrollTargetIdx = index
            }
        }

        val activeLine = lines.getOrNull(activeIdx)

        // 2. Active pieces within the active line
        val mainActive = mutableSetOf<String>()
        val bgActive = mutableSetOf<String>()
        val pronActive = mutableSetOf<String>()

        activeLine?.main?.filter { !it.isWhitespace }?.forEach { piece ->
            val b = (piece.beginMs + wordOffset).coerceAtLeast(0L)
            if (positionMs >= b && positionMs < piece.endMs) {
                mainActive += piece.id
            }
        }

        activeLine?.background?.filter { !it.isWhitespace }?.forEach { piece ->
            val b = (piece.beginMs + wordOffset).coerceAtLeast(0L)
            if (positionMs >= b && positionMs < piece.endMs) {
                bgActive += piece.id
            }
        }

        (activeLine?.pronunciation.orEmpty() + activeLine?.pronunciationBackground.orEmpty())
            .filter { !it.isWhitespace }
            .forEach { piece ->
                val b = (piece.beginMs + wordOffset).coerceAtLeast(0L)
                if (positionMs >= b && positionMs < piece.endMs) {
                    pronActive += piece.id
                }
            }

        // 3. Interlude check: if no line is singing and next line begin is >= 7000ms away
        var isInterlude = false
        val nextLine = lines.firstOrNull { it.beginMs > positionMs }
        if (nextLine != null) {
            val isCurrentSinging = activeLine != null
            if (!isCurrentSinging && (nextLine.beginMs - positionMs) >= 7000L) {
                isInterlude = true
            }
        }

        // 4. Next boundary timestamp
        val nextEvent = events.firstOrNull { it.atMs > positionMs }

        return AppleKaraokeUiState(
            activeLineIndex = activeIdx,
            scrollTargetLineIndex = scrollTargetIdx,
            activeLineId = activeLine?.id,
            activeMainPieceIds = mainActive,
            activeBgPieceIds = bgActive,
            activePronunciationPieceIds = pronActive,
            isInterlude = isInterlude,
            nextEventAtMs = nextEvent?.atMs,
            currentPositionMs = positionMs,
            playbackEpoch = epoch
        )
    }
}

/**
 * Confirmed Apple formula for dynamic line movement duration (480ms..750ms).
 */
fun appleLineMoveDuration(currentEnd: Long, nextBegin: Long): Int {
    val gap = (nextBegin - currentEnd).coerceIn(200L, 750L)
    val t = (gap - 200L).toFloat() / 550f
    return (480f + (750f - 480f) * t).roundToInt()
}

@Composable
fun rememberAppleLyricsEventState(
    processor: AppleLyricsEventProcessor?,
    currentPositionMs: Long,
    isPlaying: Boolean,
    discontinuityEpoch: Long = 0L,
    positionProvider: () -> Long,
): State<AppleKaraokeUiState> {
    val state = remember(processor, discontinuityEpoch) {
        mutableStateOf(processor?.evaluateAt(currentPositionMs, discontinuityEpoch) ?: AppleKaraokeUiState())
    }

    val latestPositionProvider = rememberUpdatedState(positionProvider)

    LaunchedEffect(processor, discontinuityEpoch, isPlaying) {
        if (processor == null) return@LaunchedEffect
        state.value = processor.evaluateAt(latestPositionProvider.value(), discontinuityEpoch)
        if (!isPlaying) return@LaunchedEffect

        while (isActive) {
            val playerPosition = latestPositionProvider.value()
            val eval = processor.evaluateAt(playerPosition, discontinuityEpoch)
            state.value = eval

            val nextAt = eval.nextEventAtMs
            // The job is cancelled immediately on pause/seek by LaunchedEffect keys.
            // Local piece animators continue at display refresh rate; this scheduler only
            // wakes at a confirmed TTML boundary.
            delay(if (nextAt != null) (nextAt - playerPosition).coerceAtLeast(1L) else 1_000L)
        }
    }

    // While paused there is no scheduler. Keep scrubbing/manual position updates exact.
    LaunchedEffect(processor, currentPositionMs, discontinuityEpoch, isPlaying) {
        if (!isPlaying) {
            processor?.let {
                state.value = it.evaluateAt(currentPositionMs, discontinuityEpoch)
            }
        }
    }

    return state
}
