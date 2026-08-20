package com.lmg.vk.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lmg.vk.network.ValidationPrompt
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText

private val VkBlue = Color(0xFF0077FF)

/**
 * Стеклянный диалог веб-валидации VK (SmartCaptcha, чекбокс «Я не робот», подтверждение безопасности).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VkWebValidationDialog(
    prompt: ValidationPrompt?,
    onDismiss: () -> Unit,
    onComplete: (Map<String, String>) -> Unit,
) {
    if (prompt == null) return

    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val colors = LiquidTheme.colors
        val isDark = colors.isDark
        val dialogBg = if (isDark) Color(0xFF1C1C1E) else Color.White
        val dialogBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .height(560.dp)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(dialogBg)
                    .border(1.dp, dialogBorder, RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* prevent dismiss inside */ },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(VkBlue.copy(alpha = if (isDark) 0.18f else 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = lmgVector(LmgDrawables.CheckShieldOutline28),
                                    contentDescription = null,
                                    tint = VkBlue,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Security check",
                                    fontFamily = VkSansDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Complete the SmartCaptcha verification",
                                    fontFamily = VkSansText,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                )
                            }
                        }

                        // Close button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                .liquidClickable(
                                    pressedScale = LiquidMotion.PressIcon,
                                    onClick = onDismiss,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = lmgVector(LmgDrawables.DismissOutline20),
                                contentDescription = "Close",
                                tint = colors.iconDefault,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    // Progress bar
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(loadingProgress.coerceIn(0.1f, 1f))
                                    .height(2.dp)
                                    .background(VkBlue),
                            )
                        }
                    }

                    // WebView content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.setSupportZoom(false)

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            loadingProgress = newProgress / 100f
                                            if (newProgress >= 100) isLoading = false
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            isLoading = true
                                            url?.let { checkValidationUrl(it, onComplete, onDismiss) }
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            return checkValidationUrl(url, onComplete, onDismiss)
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            url?.let { checkValidationUrl(it, onComplete, onDismiss) }
                                        }
                                    }

                                    loadUrl(prompt.redirectUri)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

private fun checkValidationUrl(
    url: String,
    onComplete: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
): Boolean {
    if (url.contains("blank.html") || url.contains("oauth.vk.com/blank") || url.contains("oauth.vk.ru/blank") ||
        url.contains("#success") || url.contains("success=1") || url.contains("access_token=")
    ) {
        val params = extractParamsFromUri(url)
        onComplete(params)
        return true
    }
    if (url.contains("cancel") || url.contains("fail=1") || url.contains("error=access_denied")) {
        onDismiss()
        return true
    }
    return false
}

private fun extractParamsFromUri(url: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    runCatching {
        val uri = Uri.parse(url)
        for (name in uri.queryParameterNames) {
            uri.getQueryParameter(name)?.let { result[name] = it }
        }
        val fragment = uri.fragment
        if (!fragment.isNullOrBlank()) {
            for (pair in fragment.split("&")) {
                val parts = pair.split("=", limit = 2)
                if (parts.isNotEmpty()) {
                    val k = parts[0]
                    val v = if (parts.size > 1) parts[1] else ""
                    if (k.isNotBlank()) result[k] = v
                }
            }
        }
    }
    return result
}
