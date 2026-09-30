package com.lmg.vk.engine

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Names live outside cache payloads; stale metadata is pruned when the browser scans. */
internal object CacheCatalog {
    data class Label(val title: String, val artist: String, val group: String = "")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guard = Any()
    private val pending = HashSet<String>()
    fun hash(value: String, algorithm: String = "SHA-256") = MessageDigest.getInstance(algorithm)
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun directory(context: Context) = File(context.filesDir, "cache_catalog")
    fun remember(context: Context, key: String, title: String, artist: String, group: String = "") {
        val app = context.applicationContext
        val identity = "$key\u0000$title\u0000$artist\u0000$group"
        synchronized(guard) { if (!pending.add(identity)) return }
        scope.launch {
            try {
                synchronized(guard) {
                    val dir = directory(app).apply { mkdirs() }
                    val target = File(dir, hash(key))
                    val json = JSONObject().put("key", key).put("title", title).put("artist", artist).put("group", group).toString()
                    if (!target.exists() || target.readText() != json) {
                        val atomic = AtomicFile(target)
                        val stream = atomic.startWrite()
                        try { stream.write(json.toByteArray()); atomic.finishWrite(stream) }
                        catch (e: Exception) { atomic.failWrite(stream); throw e }
                    }
                }
            } catch (_: Exception) { /* Metadata must never interrupt playback. */ }
            finally { synchronized(guard) { pending.remove(identity) } }
        }
    }
    fun prune(context: Context, stale: (String) -> Boolean) = synchronized(guard) {
        val cutoff = System.currentTimeMillis() - 600_000L
        directory(context).listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { file ->
            runCatching { if (stale(JSONObject(file.readText()).getString("key"))) file.delete() }
        }
    }
    fun labels(context: Context): Map<String, Label> = synchronized(guard) {
        directory(context).listFiles().orEmpty().mapNotNull { file ->
            runCatching { JSONObject(file.readText()).let {
                it.getString("key") to Label(it.optString("title"), it.optString("artist"), it.optString("group"))
            } }.getOrNull()
        }.toMap()
    }
    fun forget(context: Context, key: String) = synchronized(guard) { File(directory(context), hash(key)).delete(); Unit }
}
