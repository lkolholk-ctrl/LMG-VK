package com.lmg.vk.engine.backend

import android.content.Context
import com.lmg.vk.engine.VkAudioIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object WaveSignalQueue {
    private const val PREFS_NAME = "vk_playback_stats"
    private const val MAX_STORED_EVENTS = 500
    private const val MAX_BATCH_EVENTS = 50
    private const val MAX_BATCH_CHARS = 120_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainMutex = Mutex()
    private val playbackIds = ConcurrentHashMap<String, String>()
    private val scheduledDrains = ConcurrentHashMap<Long, Job>()
    private val retryAfterByAccount = ConcurrentHashMap<Long, Long>()
    private val storageLock = Any()

    @Volatile
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun sendStart(
        accountId: Long,
        trackId: String,
        source: String,
        shuffle: Boolean,
        repeat: String,
        streamingType: String,
        streamingUrlType: String,
        previousTrackId: String? = null,
    ) {
        if (accountId <= 0L || !VkAudioIdentity.isFullId(trackId)) return
        val stableId = VkAudioIdentity.stableFullId(trackId)
        val playbackId = UUID.randomUUID().toString()
        playbackIds[playbackKey(accountId, stableId)] = playbackId
        enqueue(
            accountId,
            baseEvent(
                name = "music_start_playback",
                trackId = stableId,
                playbackId = playbackId,
                source = source,
                shuffle = shuffle,
                repeat = repeat,
                streamingType = streamingType,
                streamingUrlType = streamingUrlType,
            ).apply {
                put("reason", "auto")
                put("start_time", System.currentTimeMillis() / 1000L)
                put("playback_started_at", System.currentTimeMillis())
                previousTrackId
                    ?.takeIf(VkAudioIdentity::isFullId)
                    ?.let { put("prev_audio_id", VkAudioIdentity.stableFullId(it)) }
            },
        )
    }

    fun sendPlayback(
        accountId: Long,
        trackId: String,
        playedSeconds: Double = 0.0,
        completed: Boolean = false,
        skipped: Boolean = false,
        source: String = "other",
        shuffle: Boolean = false,
        repeat: String = "none",
        streamingType: String = "online",
        streamingUrlType: String = "none",
    ) {
        if (accountId <= 0L || !VkAudioIdentity.isFullId(trackId)) return
        val stableId = VkAudioIdentity.stableFullId(trackId)
        val playbackId = playbackIds.remove(playbackKey(accountId, stableId))
            ?: UUID.randomUUID().toString()
        enqueue(
            accountId,
            baseEvent(
                name = "music_stop_playback",
                trackId = stableId,
                playbackId = playbackId,
                source = source,
                shuffle = shuffle,
                repeat = repeat,
                streamingType = streamingType,
                streamingUrlType = streamingUrlType,
            ).apply {
                put("duration", playedSeconds.coerceAtLeast(0.0))
                put("reason", if (skipped) "skip" else if (completed) "end" else "stop")
            },
        )
    }

    fun sendFeedback(accountId: Long, trackId: String, kind: String) {
        if (accountId <= 0L || !VkAudioIdentity.isFullId(trackId)) return
        enqueue(
            accountId,
            JSONObject()
                .put("e", "audio_player")
                .put("audio_id", VkAudioIdentity.stableFullId(trackId))
                .put("action", kind)
                .put("client_event_microsec", System.nanoTime() / 1_000L),
        )
    }

    fun drain(accountId: Long = com.lmg.vk.data.local.db.AppDatabase.activeAccountId()) {
        val appContext = context ?: return
        if (accountId <= 0L) return
        if (System.currentTimeMillis() < (retryAfterByAccount[accountId] ?: 0L)) return
        scope.launch {
            drainMutex.withLock {
                while (true) {
                    val batch = synchronized(storageLock) {
                        loadEvents(appContext, accountId).toBatch()
                    }
                    if (batch.length() == 0) return@withLock
                    val delivered = runCatching {
                        MusicBackend.sendTrackEvents(batch.toString())
                    }.getOrDefault(false)
                    if (!delivered) {
                        retryAfterByAccount[accountId] = System.currentTimeMillis() + 60_000L
                        return@withLock
                    }
                    retryAfterByAccount.remove(accountId)
                    val removed = synchronized(storageLock) {
                        val queued = loadEvents(appContext, accountId)
                        val prefixMatches = queued.length() >= batch.length() &&
                            (0 until batch.length()).all { index ->
                                queued.get(index).toString() == batch.get(index).toString()
                            }
                        if (prefixMatches) {
                            val remaining = JSONArray()
                            for (index in batch.length() until queued.length()) {
                                remaining.put(queued.get(index))
                            }
                            saveEvents(appContext, accountId, remaining)
                        }
                        prefixMatches
                    }
                    if (!removed) return@withLock
                }
            }
        }
    }

    fun onNetworkAvailable(
        accountId: Long = com.lmg.vk.data.local.db.AppDatabase.activeAccountId(),
    ) {
        retryAfterByAccount.remove(accountId)
        drain(accountId)
    }

    private fun enqueue(accountId: Long, event: JSONObject) {
        val appContext = context ?: return
        synchronized(storageLock) {
            val queued = loadEvents(appContext, accountId)
            queued.put(event)
            val trimmed = if (queued.length() <= MAX_STORED_EVENTS) {
                queued
            } else {
                JSONArray().apply {
                    for (index in queued.length() - MAX_STORED_EVENTS until queued.length()) {
                        put(queued.get(index))
                    }
                }
            }
            saveEvents(appContext, accountId, trimmed)
        }
        scheduledDrains.compute(accountId) { _, existing ->
            if (existing?.isActive == true) existing
            else scope.launch {
                delay(5_000L)
                drain(accountId)
                scheduledDrains.remove(accountId)
            }
        }
    }

    private fun baseEvent(
        name: String,
        trackId: String,
        playbackId: String,
        source: String,
        shuffle: Boolean,
        repeat: String,
        streamingType: String,
        streamingUrlType: String,
    ) = JSONObject()
        .put("e", name)
        .put("audio_id", trackId)
        .put("uuid", playbackId)
        .put("shuffle", shuffle)
        .put("repeat", repeat)
        .put("state", "app")
        .put("source", source)
        .put("streaming_type", streamingType)
        .put("streaming_url_type", streamingUrlType)
        .put("client_event_microsec", System.nanoTime() / 1_000L)

    private fun JSONArray.toBatch(): JSONArray {
        val batch = JSONArray()
        for (index in 0 until length().coerceAtMost(MAX_BATCH_EVENTS)) {
            val next = get(index)
            batch.put(next)
            if (batch.toString().length > MAX_BATCH_CHARS) {
                batch.remove(batch.length() - 1)
                break
            }
        }
        return batch
    }

    private fun loadEvents(context: Context, accountId: Long): JSONArray {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(queueKey(accountId), null)
            ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun saveEvents(context: Context, accountId: Long, events: JSONArray) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (events.length() == 0) editor.remove(queueKey(accountId))
        else editor.putString(queueKey(accountId), events.toString())
        editor.apply()
    }

    private fun queueKey(accountId: Long) = "events_$accountId"

    private fun playbackKey(accountId: Long, trackId: String) = "$accountId:$trackId"
}
