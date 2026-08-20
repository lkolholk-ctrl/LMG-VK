package com.lmg.vk.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lmg.vk.network.ValidationPrompt
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidTheme

/**
 * Официальный диалог «Я не робот» (VK SmartCaptcha) из VK X (C5847l / C4169l).
 *
 * Отображает интерактивный виджет SmartCaptcha от VK с заголовком X-Requested-With: com.vkontakte.android.
 * При нажатии «Я не робот» перехватывает ?success= и завершает проверку.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VkWebValidationDialog(
    prompt: ValidationPrompt?,
    onDismiss: () -> Unit,
    onComplete: (Map<String, String>) -> Unit,
) {
    if (prompt == null) return

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
        val dialogBg = if (isDark) Color(0xFF19191A) else Color.White

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .imePadding()
                    .navigationBarsPadding()
                    .clip(RoundedCornerShape(28.dp))
                    .background(dialogBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* absorb click */ },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f))
                                .liquidClickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = lmgVector(LmgDrawables.CancelOutline28),
                                contentDescription = "Close",
                                tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    setBackgroundColor(0x00000000)
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 VKAndroidApp/8.14.1-100136"
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            checkRedirect(url)
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                        ): Boolean {
                                            val targetUrl = request?.url?.toString().orEmpty()
                                            return checkRedirect(targetUrl)
                                        }

                                        @Deprecated("Deprecated in Java")
                                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                            return checkRedirect(url.orEmpty())
                                        }

                                        private fun checkRedirect(targetUrl: String): Boolean {
                                            if (targetUrl.isBlank()) return false
                                            val normalized = targetUrl.replace('#', '?')
                                            val uri = runCatching { Uri.parse(normalized) }.getOrNull()
                                            if (uri != null) {
                                                if (uri.getQueryParameter("success") != null || targetUrl.contains("success=1")) {
                                                    val params = mutableMapOf<String, String>()
                                                    for (name in uri.queryParameterNames) {
                                                        uri.getQueryParameter(name)?.let { params[name] = it }
                                                    }
                                                    onComplete(params)
                                                    return true
                                                }
                                                if (uri.getQueryParameter("cancel") != null || targetUrl.contains("cancel=1")) {
                                                    onDismiss()
                                                    return true
                                                }
                                            }
                                            return false
                                        }
                                    }
                                    loadUrl(
                                        prompt.redirectUri,
                                        mapOf("X-Requested-With" to "com.vkontakte.android"),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        AnimatedVisibility(
                            visible = isLoading,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(dialogBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF0077FF),
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
