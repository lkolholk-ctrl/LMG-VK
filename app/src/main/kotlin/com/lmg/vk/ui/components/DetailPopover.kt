package com.lmg.vk.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lmg.vk.R
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgGlyphs
import com.lmg.vk.ui.theme.LiquidTheme
import kotlin.math.roundToInt

internal fun detailMenuPosition(anchor: Rect, window: IntSize, menu: IntSize, margin: Int): IntOffset {
    val x = (anchor.right.roundToInt() - menu.width).coerceIn(margin, (window.width - menu.width - margin).coerceAtLeast(margin))
    val gap = (margin / 2).coerceAtLeast(1)
    val below = anchor.bottom.roundToInt() + gap
    val y = (if (below + menu.height + margin <= window.height) below else anchor.top.roundToInt() - menu.height - gap)
        .coerceIn(margin, (window.height - menu.height - margin).coerceAtLeast(margin))
    return IntOffset(x, y)
}

internal fun detailAnchorInPopup(anchor: Rect, sourceWindowOrigin: Offset, popupOrigin: Offset): Rect =
    anchor.translate(sourceWindowOrigin - popupOrigin)

/** Opaque, bordered popover. Its transform origin is the actual triggering button. */
@Composable
fun DetailPopover(anchor: Rect, onDismiss: () -> Unit, content: @Composable (close: () -> Unit) -> Unit) {
    val dark = LiquidTheme.colors.isDark
    var closing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val dismiss by rememberUpdatedState(onDismiss)
    val density = LocalDensity.current
    val sourceView = LocalView.current
    val screenLocation = IntArray(2)
    val windowLocation = IntArray(2)
    sourceView.getLocationOnScreen(screenLocation)
    sourceView.getLocationInWindow(windowLocation)
    val sourceOrigin = Offset((screenLocation[0] - windowLocation[0]).toFloat(), (screenLocation[1] - windowLocation[1]).toFloat())
    var popupOrigin by remember { mutableStateOf(Offset.Zero) }
    val margin = with(density) { 12.dp.roundToPx() }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val provider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
        }
    }
    val localAnchor = detailAnchorInPopup(anchor, sourceOrigin, popupOrigin)
    val position = detailMenuPosition(localAnchor, windowSize, menuSize, margin)
    val origin = TransformOrigin((localAnchor.center.x - position.x) / menuSize.width.coerceAtLeast(1),
        (localAnchor.center.y - position.y) / menuSize.height.coerceAtLeast(1))
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(190, easing = CubicBezierEasing(.5f, 0f, .8f, .3f)))
            dismiss()
        } else {
            progress.animateTo(1f, keyframes {
                durationMillis = 440
                0f at 0 using CubicBezierEasing(.4f, 0f, .2f, 1f)
                1.008f at 378
                1f at 440
            })
        }
    }
    val close = { closing = true }
    val shape = RoundedCornerShape(22.dp)
    Popup(popupPositionProvider = provider, onDismissRequest = close, properties = PopupProperties(focusable = true)) {
        // A full-window transparent host lets the shrinking menu reach an anchor outside
        // its final rectangle without being clipped by a small native popup window.
        Box(Modifier.fillMaxSize().onSizeChanged { windowSize = it }
            .onGloballyPositioned { popupOrigin = it.positionOnScreen() }) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { close() } })
            Column(
                Modifier.width((LocalConfiguration.current.screenWidthDp - 32).coerceAtMost(280).dp)
                    .heightIn(max = with(density) { (windowSize.height - margin * 2).coerceAtLeast(1).toDp() })
                    .offset { position }.onSizeChanged { menuSize = it }
                    .graphicsLayer {
                        scaleX = .08f + .92f * progress.value
                        scaleY = .1f + .9f * progress.value
                        alpha = (progress.value * 5f).coerceIn(0f, 1f)
                        transformOrigin = origin
                    }
                    .clip(shape).background(if (dark) Color(0xFF303030) else Color(0xFFF9F9FA))
                    .border(1.dp, if (dark) Color(0xFF505050) else Color(0xFFD5D5D8), shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
            ) { content(close) }
        }
    }

}

data class DetailMenuAction(val label: String, val icon: ImageVector, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun DetailMenuButton(title: String, actions: List<DetailMenuAction>, circular: Boolean = false) {
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var expanded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    Box(Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) {
        if (circular) DetailCircleButton(LmgGlyphs.MoreHorizontal28, stringResource(R.string.track_actions), onClick = { expanded = true })
        else Box(Modifier.size(44.dp).detailClickable(onClick = { expanded = true }), contentAlignment = Alignment.Center) {
            Icon(LmgGlyphs.MoreHorizontal28, stringResource(R.string.track_actions),
                tint = DetailStyle.muted(LiquidTheme.colors.isDark), modifier = Modifier.size(21.dp))
        }
        if (expanded) DetailPopover(anchor, onDismiss = {
            expanded = false
            val action = pending
            pending = null
            action?.invoke()
        }) { close ->
            Text(title, color = LiquidTheme.colors.textSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            actions.forEach { action ->
                Row(Modifier.fillMaxWidth().alpha(if (action.enabled) 1f else .4f)
                    .detailClickable(enabled = action.enabled, onClick = { if (pending == null) { pending = action.onClick; close() } })
                    .padding(horizontal = 20.dp).heightIn(min = 48.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(action.icon, null, tint = LiquidTheme.colors.textPrimary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(15.dp))
                    Text(action.label, color = LiquidTheme.colors.textPrimary, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun DetailCircleButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dark = LiquidTheme.colors.isDark
    Box(modifier.size(44.dp).clip(CircleShape)
        .background(if (dark) Color(0xFF303030) else Color(0xFFF4F4F5))
        .border(1.dp, if (dark) Color(0xFF505050) else Color(0xFFD5D5D8), CircleShape)
        .detailClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = LiquidTheme.colors.textPrimary, modifier = Modifier.size(23.dp))
    }
}

/** Native click semantics and keyboard handling without a Material ripple. */
internal fun Modifier.detailClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
        enabled = enabled, role = Role.Button, onClick = onClick)
}
