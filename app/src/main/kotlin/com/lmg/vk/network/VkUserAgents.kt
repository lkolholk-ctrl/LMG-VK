package com.lmg.vk.network

import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import java.util.Locale

/** User-Agent официального Android-клиента в формате, который использует VK auth. */
internal object VkUserAgents {
    @Volatile
    private var appContext: Context? = null

    val api: String by lazy { build(RecoveredServiceConfig.VK_ANDROID_USER_AGENT) }
    val auth: String by lazy { build(RecoveredServiceConfig.VK_ANDROID_AUTH_USER_AGENT) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun build(prefix: String): String {
        val screenSize = screenSize()
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { Build.CPU_ABI }
        return String.format(
            Locale.US,
            "%s (Android %s; SDK %d; ru; %s; %s %s; %dx%d)",
            prefix,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
            abi,
            Build.MANUFACTURER,
            Build.MODEL,
            screenSize.x,
            screenSize.y,
        )
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Point {
        val metrics = Resources.getSystem().displayMetrics
        val fallback = Point(metrics.widthPixels, metrics.heightPixels)
        val context = appContext ?: return fallback
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return fallback
        return runCatching {
            Point().also { windowManager.defaultDisplay.getRealSize(it) }
                .takeIf { it.x > 0 && it.y > 0 }
        }.getOrNull() ?: fallback
    }
}
