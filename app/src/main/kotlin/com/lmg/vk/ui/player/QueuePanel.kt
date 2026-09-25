package com.lmg.vk.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.lmg.vk.ui.effects.DustDissolve
import kotlinx.coroutines.delay
import com.lmg.vk.R
import com.lmg.vk.engine.Track
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.icons.LiquidGlyphs
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.VkSansDisplay
import kotlin.math.abs

@Composable
internal fun InlineQueue(
    queue: List<Pair<Track, Boolean>>,
    currentIndex: Int,
    autoplayEnabled: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var removal by remember { mutableStateOf<QueueDustSnapshot?>(null) }
    val displayedQueue = removal?.rows ?: queue
    val displayedCurrentIndex = removal?.currentIndex ?: currentIndex
    val listState = rememberLazyListState()
    LaunchedEffect(removal?.rows) {
        if (removal != null) {
            delay(1500)
            removal = null
        }
    }
    fun finishRemoval(index: Int) {
        removal?.let { active ->
            val remaining = active.remaining - index
            removal = if (remaining.isEmpty()) null else active.copy(remaining = remaining)
        }
    }
    fun removeRow(index: Int) {
        if (removal != null || index !in queue.indices || index == currentIndex) return
        removal = QueueDustSnapshot(queue.toList(), currentIndex, setOf(index))
        onRemove(index)
    }
    val keepScroll = remember(listState) { keepScrollInQueueList(listState) }
    val autoplayStart = remember(displayedQueue, displayedCurrentIndex) {
        autoplaySectionStart(displayedQueue.map { it.second }, displayedCurrentIndex)
    }
    LaunchedEffect(displayedCurrentIndex) {
        if (displayedCurrentIndex in displayedQueue.indices) {
            listState.scrollToItem(displayedCurrentIndex + if (displayedCurrentIndex >= autoplayStart) 1 else 0)
        }
    }

    val manualRows = displayedQueue.subList(0, autoplayStart)
    val autoplayRows = displayedQueue.subList(autoplayStart, displayedQueue.size)
    val manualKeys = remember(manualRows) { manualRows.stableQueueKeys() }
    val autoplayKeys = remember(autoplayRows) { autoplayRows.stableQueueKeys("autoplay/") }

    val headingShown = autoplayEnabled || autoplayStart < displayedQueue.size
    val headingCount = if (headingShown) 1 else 0
    val firstMovable = (displayedCurrentIndex + 1).coerceIn(0, autoplayStart)
    val manualDrag = rememberQueueDragState(
        listState = listState,
        lazyRange = firstMovable until autoplayStart,
        lazyOffset = 0,
        onMove = { from, to -> if (removal == null) onMove(from, to) },
    )
    val autoplayDrag = rememberQueueDragState(
        listState = listState,
        lazyRange = (autoplayStart + headingCount) until (autoplayStart + headingCount + autoplayRows.size),
        lazyOffset = headingCount,
        onMove = { from, to -> if (removal == null) onMove(from, to) },
    )

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.queue_title),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontFamily = VkSansDisplay,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.action_clear),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = VkSansDisplay,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(enabled = removal == null) {
                        val indices = queueDustRemovalIndices(
                            queue.size, currentIndex,
                            listState.layoutInfo.visibleItemsInfo.map { it.index },
                            autoplayStart, headingShown,
                        )
                        if (indices.isNotEmpty()) {
                            removal = QueueDustSnapshot(queue.toList(), currentIndex, indices)
                        }
                        onClear()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            state = listState,
            userScrollEnabled = removal == null,
            modifier = Modifier
                .fillMaxWidth()
                .bleedHorizontally(QUEUE_GUTTER)
                .nestedScroll(keepScroll)
                .fadingEdges(),
            contentPadding = PaddingValues(horizontal = QUEUE_GUTTER),
        ) {
            itemsIndexed(
                items = manualRows,
                key = { index, _ -> manualKeys[index] },
            ) { index, entry ->
                val key = manualKeys[index]
                val dragging = manualDrag.draggedKey == key
                DustDissolve(
                    dissolving = removal?.indices?.contains(index) == true,
                    onFinished = { finishRemoval(index) },
                    durationMillis = 780,
                    maxParticles = 2200,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) manualDrag.dragOffset else 0f }
                        .then(if (dragging) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)),
                ) {
                    InlineQueueRow(
                        track = entry.first,
                        isCurrent = index == displayedCurrentIndex,
                        enabled = removal == null,
                        onClick = { if (removal == null) onJumpTo(index) },
                        onRemove = { removeRow(index) },
                        draggable = index >= firstMovable,
                        dragging = dragging,
                        onDragStart = { manualDrag.onDragStart(key) },
                        onDrag = manualDrag::onDrag,
                        onDragEnd = manualDrag::onDragEnd,
                    )
                }
            }
            if (autoplayEnabled || autoplayStart < displayedQueue.size) {
                item(key = "autoplay-heading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            QueueInfinityIcon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.queue_autoplay),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontFamily = VkSansDisplay,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = Color.White,
                            )
                            Text(
                                text = if (autoplayStart < displayedQueue.size) {
                                    stringResource(R.string.queue_autoplay_queued)
                                } else {
                                    stringResource(R.string.queue_autoplay_continues)
                                },
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = VkSansDisplay,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
            itemsIndexed(
                items = autoplayRows,
                key = { index, _ -> autoplayKeys[index] },
            ) { index, entry ->
                val at = autoplayStart + index
                val key = autoplayKeys[index]
                val dragging = autoplayDrag.draggedKey == key
                DustDissolve(
                    dissolving = removal?.indices?.contains(at) == true,
                    onFinished = { finishRemoval(at) },
                    durationMillis = 780,
                    maxParticles = 2200,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) autoplayDrag.dragOffset else 0f }
                        .then(if (dragging) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)),
                ) {
                    InlineQueueRow(
                        track = entry.first,
                        isCurrent = at == displayedCurrentIndex,
                        enabled = removal == null,
                        onClick = { if (removal == null) onJumpTo(at) },
                        onRemove = { removeRow(at) },
                        draggable = true,
                        dragging = dragging,
                        onDragStart = { autoplayDrag.onDragStart(key) },
                        onDrag = autoplayDrag::onDrag,
                        onDragEnd = autoplayDrag::onDragEnd,
                    )
                }
            }
        }
    }
}

private data class QueueDustSnapshot(
    val rows: List<Pair<Track, Boolean>>,
    val currentIndex: Int,
    val indices: Set<Int>,
    val remaining: Set<Int> = indices,
)

private fun List<Pair<Track, Boolean>>.stableQueueKeys(prefix: String = ""): List<String> {
    val seen = HashMap<String, Int>()
    return map { entry ->
        val id = entry.first.id
        val n = seen.getOrDefault(id, 0)
        seen[id] = n + 1
        if (n == 0) "$prefix$id" else "$prefix$id#$n"
    }
}

private fun autoplaySectionStart(fromAutoplay: List<Boolean>, currentIndex: Int): Int {
    val after = (currentIndex + 1).coerceIn(0, fromAutoplay.size)
    return (after until fromAutoplay.size).firstOrNull { fromAutoplay[it] }
        ?: fromAutoplay.size
}

@Composable
private fun rememberQueueDragState(
    listState: LazyListState,
    lazyRange: IntRange,
    lazyOffset: Int,
    onMove: (Int, Int) -> Unit,
): QueueDragState {
    val state = remember(listState) { QueueDragState(listState) }
    state.lazyRange = lazyRange
    state.lazyOffset = lazyOffset
    state.onMove = onMove
    return state
}

private class QueueDragState(private val listState: LazyListState) {
    var lazyRange: IntRange = IntRange.EMPTY
    var lazyOffset: Int = 0
    var onMove: (Int, Int) -> Unit = { _, _ -> }

    var draggedKey by mutableStateOf<Any?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    private var awaiting: Int? = null

    fun onDragStart(key: Any) {
        draggedKey = key
        dragOffset = 0f
        awaiting = null
    }

    fun onDrag(deltaY: Float) {
        val key = draggedKey ?: return
        dragOffset += deltaY
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.find { it.key == key } ?: return
        awaiting?.let { if (dragged.index != it) return else awaiting = null }
        val draggedCenter = dragged.offset + dragged.size / 2f + dragOffset
        val target = items
            .filter { it.index in lazyRange && it.index != dragged.index }
            .minByOrNull { abs((it.offset + it.size / 2f) - draggedCenter) }
            ?: return
        if (abs(draggedCenter - (target.offset + target.size / 2f)) > target.size / 2f) return
        onMove(dragged.index - lazyOffset, target.index - lazyOffset)
        dragOffset += (dragged.offset - target.offset)
        awaiting = target.index
    }

    fun onDragEnd() {
        draggedKey = null
        dragOffset = 0f
        awaiting = null
    }
}

@Composable
private fun InlineQueueRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    draggable: Boolean = false,
    dragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (dragging) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (draggable) {
            Icon(
                lmgVector(LmgDrawables.Reorder24),
                contentDescription = stringResource(R.string.queue_drag_reorder),
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(20.dp)
                    .offset(x = (-4).dp)
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                        )
                    },
            )
            Spacer(Modifier.width(4.dp))
        }
        com.lmg.vk.ui.glass.AlbumArtImage(
            uri = track.albumArtUri, coverUrl = track.coverUrl,
            artworkQuery = com.lmg.vk.artwork.ArtworkQuery(track.title, track.artist, track.durationMs),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .thumbnailBorder(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontFamily = VkSansDisplay,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontFamily = VkSansDisplay,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Icon(
                imageVector = LiquidGlyphs.GraphicEq,
                contentDescription = stringResource(R.string.queue_now_playing),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled && !isCurrent, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = LmgGlyphs.CancelOutline28,
                contentDescription = stringResource(R.string.remove_from_queue),
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun keepScrollInQueueList(listState: LazyListState) = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y > 0f && !listState.canScrollBackward) available else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

private fun Modifier.bleedHorizontally(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val widened = if (constraints.hasBoundedWidth) {
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        )
    } else {
        constraints
    }
    val placeable = measurable.measure(widened)
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) {
        placeable.place(-(placeable.width - width) / 2, 0)
    }
}

private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 28.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fade,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private fun Modifier.thumbnailBorder(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.border(width = 1.dp, color = Color.White.copy(alpha = 0.15f), shape = shape)

private val QUEUE_GUTTER = 30.dp
