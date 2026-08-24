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
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lmg.vk.R
import com.lmg.vk.network.ValidationPrompt
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

private const val VK_CAPTCHA_BRIDGE_NAME = "AndroidBridge"

class VkCaptchaJavascriptBridge(
    private val onToken: (String) -> Unit,
    private val onClose: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val finished = AtomicBoolean(false)

    @JavascriptInterface
    fun VKCaptchaGetResult(payload: String?) {
        val token = try {
            JSONObject(payload.orEmpty()).getString("token")
        } catch (error: JSONException) {
            Log.e("VKCaptchaWebView", "Failed to parse SmartCaptcha result", error)
            return
        }
        if (token.isBlank()) return
        if (!finished.compareAndSet(false, true)) return

        mainHandler.post { onToken(token) }
    }

    @JavascriptInterface
    fun VKCaptchaCloseCaptcha(payload: String?) {
        if (!finished.compareAndSet(false, true)) return
        mainHandler.post { onClose() }
    }

    @JavascriptInterface
    fun VKCaptchaListenSensorsStart(payload: String?) = Unit

    @JavascriptInterface
    fun VKCaptchaListenSensorsStop(payload: String?) = Unit
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VkWebValidationDialog(
    prompt: ValidationPrompt?,
    onDismiss: () -> Unit,
    onComplete: (Map<String, String>) -> Unit,
) {
    if (prompt == null) return

    var isLoading by remember(prompt.redirectUri) { mutableStateOf(true) }
    var loadError by remember(prompt.redirectUri) { mutableStateOf<String?>(null) }
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            key(prompt.redirectUri) {
                AndroidView(
                    factory = { context ->
                        val appContext = context.applicationContext
                        WebView(context).apply {
                            captchaWebView[0] = this
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setBackgroundColor(0x00000000)
                            isVerticalScrollBarEnabled = false
                            overScrollMode = View.OVER_SCROLL_NEVER
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                            }
                            addJavascriptInterface(
                                VkCaptchaJavascriptBridge(
                                    onToken = { token ->
                                        onComplete(mapOf("success_token" to token))
                                    },
                                    onClose = onDismiss,
                                ),
                                VK_CAPTCHA_BRIDGE_NAME,
                            )
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    loadError = null
                                    checkRedirect(url.orEmpty())
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    checkRedirect(url.orEmpty())
                                }

                                override fun onPageCommitVisible(view: WebView?, url: String?) {
                                    super.onPageCommitVisible(view, url)
                                    isLoading = false
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
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                    loadError = error?.description?.toString()
                                        ?.takeIf(String::isNotBlank)
                                        ?: appContext.getString(R.string.validation_load_failed)
                            }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?,
                                ) {
                                    handler?.cancel()
                                    if (view?.url == error?.url) {
                                        isLoading = false
                                        loadError = appContext.getString(R.string.validation_ssl_error)
                                    }
                                }

                                private fun checkRedirect(targetUrl: String): Boolean {
                                    if (targetUrl.isBlank()) return false
                                    val normalized = targetUrl.replace('#', '?')
                                    val uri = runCatching { Uri.parse(normalized) }.getOrNull()
                                    if (uri != null) {
                                        if (uri.getQueryParameter("success") != null || targetUrl.contains("success=1")) {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isLoading) 0f else 1f),
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    color = Color(0xFF0077FF),
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                )
            }

            loadError?.let { message ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = message, color = Color.White)
                    TextButton(
                        onClick = {
                            loadError = null
                            isLoading = true
                            captchaWebView[0]?.loadUrl(
                                prompt.redirectUri,
                                mapOf("X-Requested-With" to "com.vkontakte.android"),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.action_retry), color = Color(0xFF0077FF))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
