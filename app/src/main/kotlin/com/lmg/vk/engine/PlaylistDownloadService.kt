package com.lmg.vk.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.lmg.vk.audio.DownloadQueue
import kotlinx.coroutines.*

/** Playlist and single downloads use the same persistent, cancellable queue. */
class PlaylistDownloadService : Service() {
    companion object {
        fun start(context: Context, tracks: List<Track>, playlistName: String? = null) {
            tracks.distinctBy { it.id }.forEach { AudioDownloadManager.downloadTrack(context, it) }
        }
        fun cancel(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { DownloadQueue.cancelAll(context.applicationContext) }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
