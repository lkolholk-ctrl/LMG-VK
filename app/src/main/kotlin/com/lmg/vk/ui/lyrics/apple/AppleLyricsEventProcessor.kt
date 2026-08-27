package com.lmg.vk.ui.lyrics.apple

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument
import com.lmg.vk.engine.lyrics.apple.AppleLyricsLine
import com.lmg.vk.engine.lyrics.apple.AppleLyricPiece
import com.lmg.vk.engine.lyrics.apple.ApplePieceRole
import com.lmg.vk.engine.lyrics.apple.AppleTimingType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

data class AppleKaraokeUiState(
    val activeLineIndex: Int = -1,
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

            // Pronunciation pieces
            line.pronunciation.filter { !it.isWhitespace }.forEach { piece ->
                val b = (piece.beginMs + wordOffset).coerceAtLeast(0L)
                list += AppleLyricsEvent.PieceStart(piece = piece, lineIndex = index, atMs = b)
                list += AppleLyricsEvent.PieceEnd(piece = piece, lineIndex = index, atMs = piece.endMs)
            }
        }

        events = list.sorted()
    }

    /**
     * Analytically evaluates the active state for any [positionMs].
     * Deterministic, zero allocations during seek/scrubbing.
     */
    fun evaluateAt(positionMs: Long, epoch: Long = 0L): AppleKaraokeUiState {
        if (lines.isEmpty()) {
            return AppleKaraokeUiState(playbackEpoch = epoch)
        }

        val wordOffset = if (document.timing == AppleTimingType.WORD) -100L else 0L

        // 1. Find active line
        var activeIdx = -1
        for (i in lines.indices) {
            val line = lines[i]
            val lineLeadMs = if (i == 0) 500L else appleLineMoveDuration(lines[i - 1].endMs, line.beginMs).toLong()
            val start = (line.beginMs - lineLeadMs).coerceAtLeast(0L)
            if (positionMs in start..line.endMs) {
                activeIdx = i
                break
            }
        }

        // If between lines, find if we are in past lines or before first line
        if (activeIdx == -1) {
            val lastPast = lines.indexOfLast { it.endMs <= positionMs }
            activeIdx = if (lastPast >= 0) lastPast else 0
        }

        val activeLine = lines.getOrNull(activeIdx)

        // 2. Active pieces within the active line
        val mainActive = mutableSetOf<String>()
        val bgActive = mutableSetOf<String>()
        val pronActive = mutableSetOf<String>()

        activeLine?.main?.filter { !it.isWhitespace }?.forEach { piece ->
            val b = (piece.beginMs + wordOffset).coerceAtLeast(0L)
            if (positionMs in b..piece.endMs) {
                mainActive += piece.id
            }
        }

        activeLine?.background?.filter { !it.isWhitespace }?.forEach { piece ->
            val b = (piece.beginMs + wordOffset).coerceAtLeast(0L)
            if (positionMs in b..piece.endMs) {
                bgActive += piece.id
            }
        }

        activeLine?.pronunciation?.filter { !it.isWhitespace }?.forEach { piece ->
            val b = (piece.beginMs + wordOffset).coerceAtLeast(0L)
            if (positionMs in b..piece.endMs) {
                pronActive += piece.id
            }
        }

        // 3. Interlude check: if no line is singing and next line begin is >= 7000ms away
        var isInterlude = false
        val nextLine = lines.firstOrNull { it.beginMs > positionMs }
        if (nextLine != null) {
            val isCurrentSinging = activeLine != null && positionMs in activeLine.beginMs..activeLine.endMs
            if (!isCurrentSinging && (nextLine.beginMs - positionMs) >= 7000L) {
                isInterlude = true
            }
        }

        // 4. Next boundary timestamp
        val nextEvent = events.firstOrNull { it.atMs > positionMs }

        return AppleKaraokeUiState(
            activeLineIndex = activeIdx,
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
    discontinuityEpoch: Long = 0L
): State<AppleKaraokeUiState> {
    val state = remember(processor, discontinuityEpoch) {
        mutableStateOf(processor?.evaluateAt(currentPositionMs, discontinuityEpoch) ?: AppleKaraokeUiState())
    }

    LaunchedEffect(processor, discontinuityEpoch) {
        if (processor == null) return@LaunchedEffect
        var localPos = currentPositionMs

        while (isActive) {
            val eval = processor.evaluateAt(localPos, discontinuityEpoch)
            state.value = eval

            val nextAt = eval.nextEventAtMs
            if (nextAt != null && nextAt > localPos) {
                val delayMs = (nextAt - localPos).coerceIn(16L, 1000L)
                delay(delayMs)
                localPos += delayMs
            } else {
                delay(33L)
                localPos += 33L
            }
        }
    }

    // Immediate sync on external currentPositionMs changes (e.g. seek)
    LaunchedEffect(currentPositionMs) {
        processor?.let {
            state.value = it.evaluateAt(currentPositionMs, discontinuityEpoch)
        }
    }

    return state
}
