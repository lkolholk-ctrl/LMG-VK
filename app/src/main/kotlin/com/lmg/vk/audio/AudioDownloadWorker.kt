package com.lmg.vk.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.lmg.vk.MainActivity
import com.lmg.vk.R
import com.lmg.vk.engine.AudioDownloadManager
import com.lmg.vk.data.local.PublicDownloads
import com.lmg.vk.data.local.db.FavoriteTrackDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class AudioDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val track = runCatching { DownloadQueue.decode(inputData.getString("track") ?: "") }.getOrElse { return@withContext Result.failure() }
        try {
            DownloadQueue.slots.withPermit {
                DownloadQueue.lock(track.id).withLock {
                    setForeground(foreground(track.title))
                    AudioDownloadManager.downloadAndStore(applicationContext, track)
                }
            }
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            com.lmg.vk.debug.DebugLog.add("DOWNLOAD failed id=${track.id} attempt=$runAttemptCount ${e.javaClass.simpleName}: ${e.message}")
            if (runAttemptCount < 2) Result.retry() else {
                val row = FavoriteTrackDatabase.getInstance(applicationContext).getDownloadedTrack(track.id)
                val saved = row != null && PublicDownloads.exists(applicationContext, row.localPath)
                Result.failure(workDataOf("audioSaved" to saved,
                    "warning" to if (saved) "Аудио сохранено, но обложка или моушен не докачаны. Повторите загрузку для завершения." else "Не удалось скачать аудио. Повторите загрузку."))
            }
        } finally { AudioDownloadManager.reportProgress(track.id, null) }
    }
    private fun foreground(title: String): ForegroundInfo {
        val context = applicationContext
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("offline_audio", "Скачивание музыки", NotificationManager.IMPORTANCE_LOW))
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, "offline_audio")
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle("Скачивание музыки")
            .setContentText(title).setContentIntent(intent).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .addAction(0, "Отмена", WorkManager.getInstance(context).createCancelPendingIntent(id)).build()
        return ForegroundInfo(4000 + (id.hashCode() and 0xffff), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
