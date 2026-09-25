package com.lmg.vk.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class WindowCloseState(private val scope: CoroutineScope) {
    val exit = mutableFloatStateOf(0f)
    var completing by mutableStateOf(false)
        private set
    var onBack: () -> Unit = {}
    var fadeOnCommit = true
    private var generation = 0

    fun reset() {
        generation++
        completing = false
        exit.floatValue = 0f
    }

    suspend fun complete() {
        if (completing) return
        completing = true
        val token = ++generation
        try {
            if (fadeOnCommit) {
                animate(0f, 1f, animationSpec = tween(160)) { fraction, _ ->
                    if (token == generation) exit.floatValue = fraction
                }
            }
            if (token == generation) onBack()
        } catch (cancelled: CancellationException) {
            if (token == generation) reset()
            throw cancelled
        }
    }

    fun requestBack() { scope.launch { complete() } }
}

@Composable
internal fun rememberWindowCloseState(
    enabled: Boolean,
    fadeOnCommit: Boolean = true,
    registerHandler: Boolean = true,
    onBack: () -> Unit,
): WindowCloseState {
    val scope = rememberCoroutineScope()
    val state = remember { WindowCloseState(scope) }
    val latestBack by rememberUpdatedState(onBack)
    SideEffect {
        state.onBack = { latestBack() }
        state.fadeOnCommit = fadeOnCommit
    }
    LaunchedEffect(enabled) { if (enabled) state.reset() }
    BackHandler(enabled = enabled && registerHandler, onBack = state::requestBack)
    return state
}

internal fun Modifier.windowClose(state: WindowCloseState): Modifier = graphicsLayer {
    val fraction = state.exit.floatValue.coerceIn(0f, 1f)
    translationX = 20.dp.toPx() * fraction
    alpha = 1f - fraction
}

@Composable
internal fun WindowCloseSurface(
    enabled: Boolean = true,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(requestBack: () -> Unit) -> Unit,
) {
    val state = rememberWindowCloseState(enabled, onBack = onBack)
    Box(modifier.fillMaxSize().background(LiquidTheme.colors.settingsBackground)) {
        Box(Modifier.fillMaxSize().windowClose(state)) { content(state::requestBack) }
    }
}

@Composable
internal fun <T> WindowPageHost(
    page: T,
    enabled: Boolean,
    onBack: () -> Unit,
    content: @Composable BoxScope.(T, requestBack: () -> Unit) -> Unit,
) {
    val state = rememberWindowCloseState(enabled, onBack = onBack)
    LaunchedEffect(page) { state.reset() }
    Box(Modifier.fillMaxSize().background(LiquidTheme.colors.settingsBackground)) {
        key(page) {
            Box(Modifier.fillMaxSize().windowClose(state)) {
                content(page, state::requestBack)
            }
        }
    }
}
