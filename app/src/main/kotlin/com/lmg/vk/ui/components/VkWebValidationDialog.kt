package com.lmg.vk.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import org.json.JSONException
import org.json.JSONObject

private const val VK_CAPTCHA_BRIDGE_NAME = "AndroidBridge"

/**
 * JS contract used by VK SmartCaptcha 3 in VK X 8.14.1 (C3680l/C4058l).
 * The page returns the proof as `{ "token": "..." }`; the API expects it
 * in the repeated request under `success_token`.
 */
private class VkCaptchaJavascriptBridge(
    private val onToken: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun VKCaptchaGetResult(payload: String?) {
        val token = try {
            JSONObject(payload.orEmpty()).getString("token")
        } catch (error: JSONException) {
            Log.e("VKCaptchaWebView", "Failed to parse SmartCaptcha result", error)
            return
        }
        if (token.isBlank()) return

        mainHandler.post { onToken(token) }
    }

    @JavascriptInterface
    fun VKCaptchaCloseCaptcha(payload: String?) {
        mainHandler.post { onClose() }
    }

    @JavascriptInterface
    fun VKCaptchaListenSensorsStart(payload: String?) = Unit

    @JavascriptInterface
    fun VKCaptchaListenSensorsStop(payload: String?) = Unit
}

/**
 * Официальный диалог «Я не робот» (VK SmartCaptcha) из VK X
 * (C10995l — WebView, C3680l — JavaScript bridge, C8060l — redirects).
 *
 * Отображает интерактивный виджет SmartCaptcha от VK с заголовком
 * X-Requested-With: com.vkontakte.android. Основной результат приходит через
 * AndroidBridge.VKCaptchaGetResult и передаётся повторному API-запросу как
 * success_token. Redirect `?success=` — официальный резервный сигнал без токена.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VkWebValidationDialog(
    prompt: ValidationPrompt?,
    onDismiss: () -> Unit,
    onComplete: (Map<String, String>) -> Unit,
) {
    if (prompt == null) return

    var isLoading by remember(prompt.redirectUri) { mutableStateOf(true) }
    val captchaWebView = remember(prompt.redirectUri) { arrayOfNulls<WebView>(1) }

    DisposableEffect(prompt.redirectUri) {
        onDispose {
            captchaWebView[0]?.apply {
                stopLoading()
                removeJavascriptInterface(VK_CAPTCHA_BRIDGE_NAME)
                destroy()
            }
            captchaWebView[0] = null
        }
    }

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
                        key(prompt.redirectUri) {
                            AndroidView(
                                factory = { context ->
                                    WebView(context).apply {
                                        captchaWebView[0] = this
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        setBackgroundColor(0x00000000)
                                        isVerticalScrollBarEnabled = false
                                        overScrollMode = View.OVER_SCROLL_NEVER
                                        settings.javaScriptEnabled = true
                                        addJavascriptInterface(
                                            VkCaptchaJavascriptBridge(
                                                onToken = { token ->
                                                    onComplete(mapOf("success_token" to token))
                                                },
                                                onClose = onDismiss,
                                            ),
                                            VK_CAPTCHA_BRIDGE_NAME,
                                        )
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                super.onPageStarted(view, url, favicon)
                                                checkRedirect(url.orEmpty())
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                isLoading = false
                                                checkRedirect(url.orEmpty())
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

                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: WebResourceError?,
                                            ) {
                                                super.onReceivedError(view, request, error)
                                                if (request?.isForMainFrame == true) onDismiss()
                                            }

                                            override fun onReceivedSslError(
                                                view: WebView?,
                                                handler: SslErrorHandler?,
                                                error: SslError?,
                                            ) {
                                                super.onReceivedSslError(view, handler, error)
                                                if (view?.url == error?.url) onDismiss()
                                            }

                                            private fun checkRedirect(targetUrl: String): Boolean {
                                                if (targetUrl.isBlank()) return false
                                                val normalized = targetUrl.replace('#', '?')
                                                val uri = runCatching { Uri.parse(normalized) }.getOrNull()
                                                if (uri != null) {
                                                    if (uri.getQueryParameter("success") != null || targetUrl.contains("success=1")) {
                                                        // VK X repeats the request without extra params when
                                                        // the fallback redirect has no bridge token.
                                                        onComplete(emptyMap())
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
                        }

                        if (isLoading) {
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
