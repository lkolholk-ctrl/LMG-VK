package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmg.vk.ui.theme.LiquidTheme

private val AccentBlue = Color(0xFF0088CC)

/**
 * Экран входа (временная точка входа): реальный VK-флоу (silent-авторизация /
 * пароль) подключается позже; сейчас кнопка «Продолжить» сразу завершает вход.
 */
@Composable
fun EmailAuthSheet(
    onSuccess: () -> Unit,
    onClose: () -> Unit,
) {
    val lc = LiquidTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sign in",
            color = lc.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "VK authorization flow is coming soon.\nFor now, continue as guest.",
            color = lc.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(AccentBlue, RoundedCornerShape(percent = 50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSuccess() },
            contentAlignment = Alignment.Center
        ) {
            Text("Continue as guest", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Cancel",
            color = lc.textTertiary, fontSize = 13.sp,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
                .padding(8.dp)
        )
    }
}
