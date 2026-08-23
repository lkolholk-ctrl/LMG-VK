package com.lmg.vk.network

import android.content.res.Resources
import android.os.Build
import java.util.Locale

/** User-Agent официального Android-клиента в формате, который использует VK auth. */
internal object VkUserAgents {
    val api: String by lazy { build(RecoveredServiceConfig.VK_ANDROID_USER_AGENT) }
    val auth: String by lazy { build(RecoveredServiceConfig.VK_ANDROID_AUTH_USER_AGENT) }

    private fun build(prefix: String): String {
        val metrics = Resources.getSystem().displayMetrics
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
            metrics.widthPixels,
            metrics.heightPixels,
        )
    }
}
