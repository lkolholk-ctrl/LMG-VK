package com.lmg.vk.engine.dsp

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DspController {
    private val mutableSettings = MutableStateFlow(DspSettings())
    val settings = mutableSettings.asStateFlow()
    private val mutableAvailable = MutableStateFlow(true)
    val available = mutableAvailable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private var initialized = false

    @Synchronized fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.applicationContext.getSharedPreferences("player_dsp_v1", Context.MODE_PRIVATE)
        mutableSettings.value = runCatching {
            json.decodeFromString<DspSettings>(prefs.getString("snapshot", null) ?: "{}").normalized()
        }.getOrDefault(DspSettings())
        mutableAvailable.value = NativeDsp.available
        scope.launch {
            // This sole producer compiles coefficients away from the audio callback.
            // collectLatest + retry preserves the final slider value even if paused/full.
            settings.collectLatest { snapshot ->
                prefs.edit().putString("snapshot", json.encodeToString(snapshot)).apply()
                if (NativeDsp.available) {
                    val result = NativeDsp.nativePublish(snapshot.toNativeParameters())
                    if (result < 0) { mutableAvailable.value = false; return@collectLatest }
                    mutableAvailable.value = true
                    if (result == 0) do { delay(25) } while (!NativeDsp.nativeRetry())
                }
            }
        }
    }

    fun update(transform: (DspSettings) -> DspSettings) {
        mutableSettings.update { transform(it).normalized() }
    }
    fun reset() { mutableSettings.value = DspSettings() }
}
