package com.lmg.vk.ui.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun WindowAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val state = rememberWindowCloseState(enabled = true, registerHandler = false, onBack = onDismissRequest)
    AlertDialog(
        onDismissRequest = state::requestBack,
        modifier = Modifier.windowClose(state),
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
    )
}
