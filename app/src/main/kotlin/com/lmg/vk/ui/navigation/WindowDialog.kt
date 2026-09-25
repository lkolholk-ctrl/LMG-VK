package com.lmg.vk.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog as PlatformDialog
import androidx.compose.ui.window.DialogProperties

internal val LocalDialogCloseState = staticCompositionLocalOf<WindowCloseState?> { null }

@Composable
internal fun WindowDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    transformContent: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = rememberWindowCloseState(
        enabled = true,
        registerHandler = false,
        onBack = onDismissRequest,
    )
    PlatformDialog(onDismissRequest = state::requestBack, properties = properties) {
        CompositionLocalProvider(LocalDialogCloseState provides state) {
            Box(if (transformContent) Modifier.windowClose(state) else Modifier) { content() }
        }
    }
}
