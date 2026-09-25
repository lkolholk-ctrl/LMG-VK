package com.lmg.vk.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.BuildConfig
import com.lmg.vk.debug.AppPerformanceCapture
import com.lmg.vk.ui.theme.LiquidTheme

@Composable
internal fun PerformanceCaptureCard() {
    if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < 35) return
    val context = LocalContext.current
    val state by AppPerformanceCapture.state.collectAsState()
    val colors = LiquidTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text("Запись производительности", color = colors.textPrimary,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(state.message, color = colors.textSecondary, fontSize = 12.sp)
        TextButton(
            enabled = state.initialized && !state.pending,
            onClick = { AppPerformanceCapture.setArmed(context, !state.armed) },
        ) {
            Text(if (state.armed) "Отменить запись при запуске" else "Записать при следующем запуске")
        }
        if (state.capture != null) {
            TextButton(enabled = !state.exporting, onClick = { AppPerformanceCapture.share(context) }) {
                Text(if (state.exporting) "Готовим файл…" else if (state.pending || state.armed)
                    "Отправить предыдущую запись" else "Отправить запись")
            }
        }
    }
}
