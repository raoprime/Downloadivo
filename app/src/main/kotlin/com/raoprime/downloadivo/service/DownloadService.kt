package com.raoprime.downloadivo.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.raoprime.downloadivo.DownloadivoApp
import com.raoprime.downloadivo.MainActivity
import com.raoprime.downloadivo.model.DownloadStatus
import com.raoprime.downloadivo.model.DownloadTask
import com.raoprime.downloadivo.network.DownloadEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.OutputStream

class DownloadService : Service() {

    companion object {
        const val ACTION_START_DOWNLOAD = "com.raoprime.downloadivo.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.raoprime.downloadivo.CANCEL_DOWNLOAD"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FORMAT = "extra_format"

        private val _currentTask = MutableStateFlow(DownloadTask())
        val currentTask = _currentTask.asStateFlow()

        fun startDownload(context: Context, url: String, title: String, format: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT, format)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var downloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Downloadivo:WakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: ""
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Media"
                val format = intent.getStringExtra(EXTRA_FORMAT) ?: "best"
                if (url.isNotBlank()) {
                    beginDownload(url, title, format)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun beginDownload(url: String, title: String, format: String) {
        downloadJob?.cancel()

        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes */)

        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9._ -]"), "").take(80).ifBlank { "download" }
        val isAudio = format.lowercase() == "mp3"
        val extension = if (isAudio) "mp3" else "mp4"
        val fileName = "$cleanTitle.$extension"

        _currentTask.value = DownloadTask(
            id = System.currentTimeMillis().toString(),
            title = cleanTitle,
            url = url,
            format = format,
            status = DownloadStatus.DOWNLOADING
        )

        startForegroundNotification(cleanTitle, 0, "Starting download...")

        downloadJob = scope.launch(Dispatchers.IO) {
            var mediaUri: Uri? = null
            var outStream: OutputStream? = null

            try {
                // MediaStore Target Setup (Scoped Storage)
                val resolver = applicationContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (isAudio) "audio/mpeg" else "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            if (isAudio) "Music/Downloadivo" else "Movies/Downloadivo"
                        )
                    }
                }

                val collectionUri = if (isAudio) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                }

                mediaUri = resolver.insert(collectionUri, contentValues)
                    ?: throw Exception("Could not allocate file in MediaStore")

                outStream = resolver.openOutputStream(mediaUri)
                    ?: throw Exception("Could not open destination stream")

                val result = DownloadEngine.downloadStream(
                    targetUrl = url,
                    format = format,
                    outputStream = outStream,
                    onProgress = { percent, speed, current, total ->
                        _currentTask.value = _currentTask.value.copy(
                            progress = percent,
                            speed = speed,
                            bytesDownloaded = current,
                            totalBytes = total,
                            status = DownloadStatus.DOWNLOADING
                        )
                        updateForegroundNotification(cleanTitle, percent, speed)
                    }
                )

                if (result.isSuccess) {
                    // Mark file complete in MediaStore (instant visibility in Gallery/Photos)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(mediaUri, contentValues, null, null)
                    }

                    _currentTask.value = _currentTask.value.copy(
                        progress = 100,
                        status = DownloadStatus.SUCCESS,
                        filePath = mediaUri.toString()
                    )
                    showCompletionNotification(cleanTitle, true)
                } else {
                    throw result.exceptionOrNull() ?: Exception("Download aborted")
                }

            } catch (e: Exception) {
                // Delete partial file on error
                mediaUri?.let { uri ->
                    try { applicationContext.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }

                _currentTask.value = _currentTask.value.copy(
                    status = DownloadStatus.ERROR,
                    error = e.localizedMessage ?: "Download failed"
                )
                showCompletionNotification(cleanTitle, false)
            } finally {
                try { outStream?.close() } catch (_: Exception) {}
                if (wakeLock?.isHeld == true) wakeLock?.release()
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    private fun cancelDownload() {
        downloadJob?.cancel()
        _currentTask.value = _currentTask.value.copy(
            status = DownloadStatus.IDLE,
            progress = 0
        )
        if (wakeLock?.isHeld == true) wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification(title: String, progress: Int, speed: String) {
        val notification = buildProgressNotification(title, progress, speed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                1001,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1001, notification)
        }
    }

    private fun updateForegroundNotification(title: String, progress: Int, speed: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(1001, buildProgressNotification(title, progress, speed))
    }

    private fun buildProgressNotification(title: String, progress: Int, speed: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DownloadivoApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (progress >= 0) "$progress% • $speed" else "Streaming • $speed")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, if (progress >= 0) progress else 0, progress < 0)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCompletionNotification(title: String, isSuccess: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, DownloadivoApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (isSuccess) "⚡ Download completed successfully!" else "Download failed")
            .setSmallIcon(if (isSuccess) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        manager.notify(1002, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
}
