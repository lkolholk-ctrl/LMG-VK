package com.lmg.vk.audio

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.*
import com.lmg.vk.engine.AudioDownloadManager
import com.lmg.vk.engine.Track
import com.lmg.vk.artwork.OfflineMotionIndex
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit

/** Persistent unique work: duplicate taps and playlist/single requests share the same download. */
internal object DownloadQueue {
    const val TAG = "lmg-audio-download-v2"
    val slots = Semaphore(2)
    private val enqueueLock = Mutex()
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private val cancelledVersions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val shownWarnings = mutableSetOf<java.util.UUID>()
    private val locks = Array(64) { Mutex() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    fun lock(id: String) = locks[(id.hashCode() and Int.MAX_VALUE) % locks.size]
    private fun name(id: String) = "audio-" + OfflineMotionIndex.key(id)

    fun enqueue(context: Context, track: Track, complete: (Boolean) -> Unit) {
        val app = context.applicationContext
        val epoch = generation.get()
        val version = cancelledVersions[track.id] ?: 0L
        AudioDownloadManager.reportProgress(track.id, 0f)
        scope.launch {
            try {
                val wm = WorkManager.getInstance(app)
                val id = withContext(Dispatchers.IO) {
                    enqueueLock.withLock {
                        if (epoch != generation.get() || version != (cancelledVersions[track.id] ?: 0L))
                            return@withLock null
                        wm.getWorkInfosForUniqueWork(name(track.id)).get().firstOrNull { !it.state.isFinished }?.id
                            ?: OneTimeWorkRequestBuilder<AudioDownloadWorker>()
                                .setInputData(workDataOf("track" to encode(track)))
                                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                                .addTag(TAG).build().also {
                                    wm.enqueueUniqueWork(name(track.id), ExistingWorkPolicy.KEEP, it).result.get()
                                }.id
                    }
                }
                if (id == null) {
                    AudioDownloadManager.reportProgress(track.id, null)
                    complete(false)
                    return@launch
                }
                val live = wm.getWorkInfoByIdLiveData(id)
                lateinit var observer: Observer<WorkInfo>
                observer = Observer { info ->
                    if (info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.BLOCKED)
                        AudioDownloadManager.reportProgress(track.id, 0f)
                    if (info.state.isFinished) {
                        live.removeObserver(observer)
                        AudioDownloadManager.reportProgress(track.id, null)
                        val warning = info.outputData.getString("warning")
                        AudioDownloadManager.reportWarning(track.id, warning)
                        if (warning != null && shownWarnings.add(id))
                            android.widget.Toast.makeText(app, warning, android.widget.Toast.LENGTH_LONG).show()
                        complete(info.state == WorkInfo.State.SUCCEEDED || info.outputData.getBoolean("audioSaved", false))
                    }
                }
                live.observeForever(observer)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                AudioDownloadManager.reportProgress(track.id, null)
                AudioDownloadManager.reportWarning(track.id, e.message)
                complete(false)
            }
        }
    }
    fun cancel(context: Context, id: String) {
        cancelledVersions.merge(id, 1L) { previous, increment -> previous + increment }
        scope.launch(Dispatchers.IO) {
            enqueueLock.withLock { WorkManager.getInstance(context).cancelUniqueWork(name(id)).result.get() }
        }
    }
    suspend fun cancelAndWait(context: Context, id: String) = withContext(Dispatchers.IO) {
        cancelledVersions.merge(id, 1L) { previous, increment -> previous + increment }
        enqueueLock.withLock { WorkManager.getInstance(context).cancelUniqueWork(name(id)).result.get() }
        Unit
    }
    suspend fun cancelAll(context: Context) = withContext(Dispatchers.IO) {
        generation.incrementAndGet()
        enqueueLock.withLock { WorkManager.getInstance(context).cancelAllWorkByTag(TAG).result.get() }
        Unit
    }
    private fun encode(t: Track) = buildJsonObject {
        put("id", t.id); put("title", t.title); put("artist", t.artist); put("album", t.albumName)
        put("duration", t.durationMs); put("uri", if (t.isOnlineTrack) "" else t.uri.toString()); put("cover", t.coverUrl); put("source", t.source); put("genre", t.genre)
    }.toString()
    fun decode(text: String): Track {
        val o = Json.parseToJsonElement(text).jsonObject
        fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
        return Track(s("id"), s("title"), s("artist"), s("album"), android.net.Uri.parse(s("uri")),
            o["duration"]?.jsonPrimitive?.longOrNull ?: 0L, -1L, s("cover").ifBlank { null }, source = s("source"), genre = s("genre").ifBlank { null })
    }
}
