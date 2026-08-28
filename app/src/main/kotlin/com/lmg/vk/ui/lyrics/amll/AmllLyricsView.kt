package com.lmg.vk.ui.lyrics.amll

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.lmg.vk.R
import com.lmg.vk.debug.DebugLog
import com.lmg.vk.engine.LyricsParser
import com.lmg.vk.engine.lyrics.LyricsContent
import com.lmg.vk.engine.lyrics.apple.AppleLyricPiece
import com.lmg.vk.engine.lyrics.apple.AppleLyricsDocument
import com.lmg.vk.engine.lyrics.apple.AppleLyricsLine
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

private const val AMLL_ASSET_HOST = "appassets.androidplatform.net"
private const val AMLL_ASSET_PATH = "/assets/amll/index.html"
private const val AMLL_FONT_PATH = "/fonts/golos_text.ttf"
private const val AMLL_ASSET_URL = "https://$AMLL_ASSET_HOST$AMLL_ASSET_PATH"

/**
 * Hosts the original Apple Music Like Lyrics DOM renderer inside a transparent
 * hardware-accelerated WebView. Compose remains responsible for the surrounding
 * LMG player chrome; AMLL exclusively owns lyric layout and animation.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AmllLyricsView(
    content: LyricsContent,
    isPlaying: Boolean,
    playbackEpoch: Long,
    showTranslations: Boolean,
    showPronunciations: Boolean,
    positionProvider: () -> Long,
    onSeek: (Long) -> Unit,
    onShareLine: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestPositionProvider by rememberUpdatedState(positionProvider)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnShareLine by rememberUpdatedState(onShareLine)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            WebView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = true
                settings.blockNetworkLoads = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false

                val mainHandler = Handler(Looper.getMainLooper())
                addJavascriptInterface(
                    AmllAndroidBridge(
                        handler = mainHandler,
                        handleReady = { pageReady = true },
                        handleLineClick = { latestOnSeek(it) },
                        handleLineLongClick = { latestOnShareLine(it) },
                    ),
                    "Android",
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val uri = request.url
                        if (uri.scheme != "https" || uri.host != AMLL_ASSET_HOST) {
                            return null
                        }
                        if (uri.path == AMLL_ASSET_PATH) {
                            return runCatching {
                                WebResourceResponse(
                                    "text/html",
                                    "UTF-8",
                                    viewContext.assets.open("amll/index.html"),
                                )
                            }.onFailure { error ->
                                DebugLog.add("AMLL asset open error: ${error.message}")
                            }.getOrNull()
                        }
                        if (uri.path == AMLL_FONT_PATH) {
                            return runCatching {
                                WebResourceResponse(
                                    "font/ttf",
                                    null,
                                    viewContext.resources.openRawResource(R.font.golos_text),
                                )
                            }.onFailure { error ->
                                DebugLog.add("AMLL font open error: ${error.message}")
                            }.getOrNull()
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        view.setBackgroundColor(Color.TRANSPARENT)
                        // Do not depend solely on a JavaScript-interface callback:
                        // some Android WebView releases can miss it while loading a
                        // local module. Probe the exported API after page completion.
                        view.evaluateJavascript(
                            "Boolean(window.LMG_AMLL)",
                        ) { ready ->
                            if (ready == "true") {
                                pageReady = true
                            } else {
                                DebugLog.add("AMLL page finished without renderer API")
                            }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            DebugLog.add("AMLL page load error ${error.errorCode}: ${error.description}")
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            DebugLog.add(
                                "AMLL console ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} " +
                                    consoleMessage.message()
                            )
                        }
                        return true
                    }
                }
                loadUrl(AMLL_ASSET_URL)
                webView = this
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            pageReady = false
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeJavascriptInterface("Android")
                destroy()
            }
            webView = null
        }
    }

    LaunchedEffect(pageReady, content) {
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        val position = latestPositionProvider().coerceAtLeast(0L)
        when (val payload = content.toAmllPayload()) {
            is AmllPayload.Ttml -> view.callAmll(
                "setTtml",
                quote(payload.value.toBase64()),
                position.toString(),
            )
            is AmllPayload.Lines -> view.callAmll(
                "setLines",
                quote(payload.value.toBase64()),
                position.toString(),
            )
        }
    }

    LaunchedEffect(pageReady, showTranslations, showPronunciations) {
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        view.callAmll(
            "setDisplayOptions",
            showTranslations.toString(),
            showPronunciations.toString(),
        )
    }

    LaunchedEffect(pageReady, playbackEpoch) {
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        view.callAmll(
            "setPosition",
            latestPositionProvider().coerceAtLeast(0L).toString(),
            "true",
        )
    }

    LaunchedEffect(pageReady, isPlaying, content) {
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        view.callAmll(
            "setPlaying",
            isPlaying.toString(),
            latestPositionProvider().coerceAtLeast(0L).toString(),
        )
    }
}

private class AmllAndroidBridge(
    private val handler: Handler,
    private val handleReady: () -> Unit,
    private val handleLineClick: (Long) -> Unit,
    private val handleLineLongClick: (String) -> Unit,
) {
    @JavascriptInterface
    fun onPageReady() {
        handler.post { handleReady() }
    }

    @JavascriptInterface
    fun onLineClick(positionMs: Long) {
        handler.post { handleLineClick(positionMs.coerceAtLeast(0L)) }
    }

    @JavascriptInterface
    fun onLineLongClick(text: String) {
        handler.post { handleLineLongClick(text) }
    }

    @JavascriptInterface
    fun onRendererError(message: String) {
        DebugLog.add("AMLL renderer error: $message")
    }
}

private fun WebView.callAmll(method: String, vararg arguments: String) {
    evaluateJavascript(
        "window.LMG_AMLL?.$method(${arguments.joinToString(",")})",
        null,
    )
}

private fun quote(value: String): String = JSONObject.quote(value)

private fun String.toBase64(): String = Base64.encodeToString(
    toByteArray(StandardCharsets.UTF_8),
    Base64.NO_WRAP,
)

private sealed interface AmllPayload {
    data class Ttml(val value: String) : AmllPayload
    data class Lines(val value: String) : AmllPayload
}

private fun LyricsContent.toAmllPayload(): AmllPayload = when (this) {
    is LyricsContent.RawTtml -> AmllPayload.Ttml(value)
    is LyricsContent.Rich -> document.rawTtml?.takeIf(String::isNotBlank)
        ?.let(AmllPayload::Ttml)
        ?: AmllPayload.Lines(document.toAmllJson())
    is LyricsContent.Legacy -> AmllPayload.Lines(lyrics.toAmllJson())
}

private fun AppleLyricsDocument.toAmllJson(): String {
    val output = JSONArray()
    val primaryAgent = allLines.firstNotNullOfOrNull { it.agentId }
    allLines.forEach { line ->
        output.put(line.toAmllJson(primaryAgent, isBackground = false))
        if (line.background.any { it.text.isNotEmpty() }) {
            output.put(line.toAmllJson(primaryAgent, isBackground = true))
        }
    }
    return output.toString()
}

private fun AppleLyricsLine.toAmllJson(
    primaryAgent: String?,
    isBackground: Boolean,
): JSONObject {
    val pieces = if (isBackground) background else main
    val fallbackStart = pieces.minOfOrNull { it.beginMs } ?: beginMs
    val fallbackEnd = pieces.maxOfOrNull { it.endMs } ?: endMs
    val translated = if (isBackground) translationBackground else translation
    val pronounced = if (isBackground) pronunciationBackground else pronunciation
    return JSONObject()
        .put("words", pieces.toWordsJson(fallbackStart, fallbackEnd))
        .put("translatedLyric", translated.joinToString("") { it.text })
        .put("romanLyric", pronounced.joinToString("") { it.text })
        .put("startTime", fallbackStart.coerceAtLeast(0L))
        .put("endTime", fallbackEnd.coerceAtLeast(fallbackStart + 1L))
        .put("isBG", isBackground)
        .put("isDuet", agentId != null && primaryAgent != null && agentId != primaryAgent)
}

private fun List<AppleLyricPiece>.toWordsJson(
    fallbackStart: Long,
    fallbackEnd: Long,
): JSONArray = JSONArray().also { words ->
    if (isEmpty()) {
        words.put(wordJson("", fallbackStart, fallbackEnd))
    } else {
        forEach { piece ->
            words.put(wordJson(piece.text, piece.beginMs, piece.endMs))
        }
    }
}

private fun LyricsParser.Lyrics.toAmllJson(): String {
    val output = JSONArray()
    val primaryAgent = lines.firstNotNullOfOrNull { it.agentId }
    lines.forEachIndexed { index, line ->
        val start = line.timeMs.coerceAtLeast(0L)
        val nextStart = lines.getOrNull(index + 1)?.timeMs?.takeIf { it > start }
        val end = line.endMs.takeIf { it > start }
            ?: line.words.maxOfOrNull { it.endMs }?.takeIf { it > start }
            ?: nextStart
            ?: (start + 2_000L)
        output.put(
            JSONObject()
                .put("words", line.toWordsJson(start, end))
                .put(
                    "translatedLyric",
                    line.translations.values.firstOrNull()?.text.orEmpty(),
                )
                .put(
                    "romanLyric",
                    line.pronunciations.values.firstOrNull()?.text.orEmpty(),
                )
                .put("startTime", start)
                .put("endTime", end)
                .put("isBG", false)
                .put(
                    "isDuet",
                    line.agentId != null && primaryAgent != null && line.agentId != primaryAgent,
                )
        )
        line.backgroundLayers.forEach { layer ->
            val backgroundStart = layer.words.firstOrNull()?.timeMs ?: start
            val backgroundEnd = layer.words.maxOfOrNull { it.endMs }
                ?.takeIf { it > backgroundStart } ?: end
            output.put(
                JSONObject()
                    .put(
                        "words",
                        if (layer.words.isEmpty()) {
                            JSONArray().put(wordJson(layer.text, backgroundStart, backgroundEnd))
                        } else {
                            JSONArray().also { words ->
                                layer.words.forEach { word ->
                                    words.put(
                                        wordJson(
                                            word.text,
                                            word.timeMs,
                                            word.endMs.takeIf { it > word.timeMs } ?: backgroundEnd,
                                        )
                                    )
                                }
                            }
                        },
                    )
                    .put("translatedLyric", layer.translations.values.firstOrNull()?.text.orEmpty())
                    .put("romanLyric", layer.pronunciations.values.firstOrNull()?.text.orEmpty())
                    .put("startTime", backgroundStart)
                    .put("endTime", backgroundEnd)
                    .put("isBG", true)
                    .put("isDuet", false)
            )
        }
    }
    return output.toString()
}

private fun LyricsParser.LyricLine.toWordsJson(
    lineStart: Long,
    lineEnd: Long,
): JSONArray {
    if (words.isEmpty()) return JSONArray().put(wordJson(text, lineStart, lineEnd))

    val result = JSONArray()
    var cursor = 0
    var previousEnd = lineStart
    words.forEachIndexed { index, timedWord ->
        val explicitStart = timedWord.charStart.takeIf { it in 0 until text.length }
        val start = explicitStart
            ?: text.indexOf(timedWord.text, cursor).takeIf { it >= 0 }
            ?: cursor
        val explicitEnd = timedWord.charEnd.takeIf { it in (start + 1)..text.length }
        val end = explicitEnd ?: (start + timedWord.text.length).coerceAtMost(text.length)
        val wordStart = timedWord.timeMs.coerceAtLeast(lineStart)
        if (start > cursor) {
            result.put(wordJson(text.substring(cursor, start), previousEnd, wordStart))
        }
        val nextStart = words.getOrNull(index + 1)?.timeMs?.takeIf { it > wordStart }
        val wordEnd = timedWord.endMs.takeIf { it > wordStart } ?: nextStart ?: lineEnd
        val renderedText = if (end > start) text.substring(start, end) else timedWord.text
        result.put(wordJson(renderedText, wordStart, wordEnd))
        cursor = end.coerceAtLeast(cursor)
        previousEnd = wordEnd
    }
    if (cursor < text.length) {
        result.put(wordJson(text.substring(cursor), previousEnd, lineEnd))
    }
    return result
}

private fun wordJson(text: String, start: Long, end: Long): JSONObject = JSONObject()
    .put("word", text)
    .put("startTime", start.coerceAtLeast(0L))
    .put("endTime", end.coerceAtLeast(start + 1L))
