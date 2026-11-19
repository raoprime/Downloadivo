package com.raoprime.downloadivo.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
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

data class QueuedDownload(
    val id: String,
    val url: String,
    val title: String,
    val format: String,
    val thumbnail: String = "",
    val startTime: String? = null,
    val endTime: String? = null
)

class DownloadService : Service() {

    companion object {
        const val ACTION_START_DOWNLOAD = "com.raoprime.downloadivo.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.raoprime.downloadivo.CANCEL_DOWNLOAD"
        const val ACTION_REMOVE_QUEUED = "com.raoprime.downloadivo.REMOVE_QUEUED"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_THUMBNAIL = "extra_thumbnail"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_END_TIME = "extra_end_time"

        private val _currentTask = MutableStateFlow(DownloadTask())
        val currentTask = _currentTask.asStateFlow()

        private val _queuedTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
        val queuedTasks = _queuedTasks.asStateFlow()

        fun startDownload(
            context: Context,
            url: String,
            title: String,
            format: String,
            thumbnail: String = "",
            startTime: String? = null,
            endTime: String? = null
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT, format)
                putExtra(EXTRA_THUMBNAIL, thumbnail)
                putExtra(EXTRA_START_TIME, startTime)
                putExtra(EXTRA_END_TIME, endTime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun removeQueuedTask(context: Context, taskId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_REMOVE_QUEUED
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }
    }

    private val serviceJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var downloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val pendingQueue = ArrayDeque<QueuedDownload>()
    private var isDownloading = false
    private var currentThumbnailBitmap: Bitmap? = null

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
                val thumbnail = intent.getStringExtra(EXTRA_THUMBNAIL) ?: ""
                val startTime = intent.getStringExtra(EXTRA_START_TIME)
                val endTime = intent.getStringExtra(EXTRA_END_TIME)
                if (url.isNotBlank()) {
                    enqueueDownload(url, title, format, thumbnail, startTime, endTime)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                cancelCurrentDownload()
            }
            ACTION_REMOVE_QUEUED -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: ""
                if (taskId.isNotBlank()) {
                    removeQueued(taskId)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun enqueueDownload(
        url: String,
        title: String,
        format: String,
        thumbnail: String = "",
        startTime: String? = null,
        endTime: String? = null
    ) {
        val taskId = System.currentTimeMillis().toString()
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9._ -]"), "").take(80).ifBlank { "download" }
        val isAudio = format.lowercase() == "mp3" || format.lowercase() == "m4a"

        val item = QueuedDownload(
            id = taskId,
            url = url,
            title = cleanTitle,
            format = format,
            thumbnail = thumbnail,
            startTime = startTime,
            endTime = endTime
        )

        if (isDownloading) {
            pendingQueue.addLast(item)
            updateQueuedState()
            val queuedTask = DownloadTask(
                id = taskId,
                title = cleanTitle,
                url = url,
                format = format,
                thumbnail = thumbnail,
                status = DownloadStatus.QUEUED,
                isAudio = isAudio,
                startTime = startTime,
                endTime = endTime
            )
            com.raoprime.downloadivo.data.DownloadRepository.addTask(queuedTask)
            Toast.makeText(applicationContext, "Added to download queue (#${pendingQueue.size})", Toast.LENGTH_SHORT).show()
        } else {
            beginDownload(item)
        }
    }

    private fun updateQueuedState() {
        _queuedTasks.value = pendingQueue.map { q ->
            DownloadTask(
                id = q.id,
                title = q.title,
                url = q.url,
                format = q.format,
                thumbnail = q.thumbnail,
                status = DownloadStatus.QUEUED,
                isAudio = q.format.lowercase() == "mp3" || q.format.lowercase() == "m4a",
                startTime = q.startTime,
                endTime = q.endTime
            )
        }
    }

    private fun beginDownload(item: QueuedDownload) {
        isDownloading = true
        downloadJob?.cancel()

        wakeLock?.acquire(15 * 60 * 1000L /* 15 minutes */)

        val cleanTitle = item.title
        val isAudio = item.format.lowercase() == "mp3" || item.format.lowercase() == "m4a"
        val extension = if (isAudio) (if (item.format.lowercase() == "m4a") "m4a" else "mp3") else "mp4"
        val clipSuffix = if (!item.startTime.isNullOrBlank() && !item.endTime.isNullOrBlank()) {
            "_Clip_${item.startTime.replace(':', '.')}_to_${item.endTime.replace(':', '.')}"
        } else {
            ""
        }
        val fileName = "$cleanTitle$clipSuffix.$extension"

        val initialTask = DownloadTask(
            id = item.id,
            title = "$cleanTitle$clipSuffix",
            url = item.url,
            format = item.format,
            thumbnail = item.thumbnail,
            status = DownloadStatus.DOWNLOADING,
            isAudio = isAudio,
            startTime = item.startTime,
            endTime = item.endTime
        )
        _currentTask.value = initialTask
        com.raoprime.downloadivo.data.DownloadRepository.addTask(initialTask)

        startForegroundNotification(cleanTitle, 0, "Starting download...", "")

        // Asynchronously load thumbnail for rich notification shade
        currentThumbnailBitmap = null
        if (item.thumbnail.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val loader = Coil.imageLoader(applicationContext)
                    val req = ImageRequest.Builder(applicationContext)
                        .data(item.thumbnail)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(req)
                    if (result is SuccessResult) {
                        currentThumbnailBitmap = result.drawable.toBitmap()
                        updateForegroundNotification(cleanTitle, _currentTask.value.progress, _currentTask.value.speed, _currentTask.value.eta)
                    }
                } catch (_: Exception) {}
            }
        }

        downloadJob = scope.launch(Dispatchers.IO) {
            var mediaUri: Uri? = null
            var outStream: OutputStream? = null

            try {
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
                    targetUrl = item.url,
                    format = item.format,
                    startTime = item.startTime,
                    endTime = item.endTime,
                    outputStream = outStream,
                    onProgress = { percent, speed, eta, current, total ->
                        val updated = _currentTask.value.copy(
                            progress = percent,
                            speed = speed,
                            eta = eta,
                            bytesDownloaded = current,
                            totalBytes = total,
                            status = DownloadStatus.DOWNLOADING
                        )
                        _currentTask.value = updated
                        com.raoprime.downloadivo.data.DownloadRepository.updateTask(updated)
                        updateForegroundNotification(cleanTitle, percent, speed, eta)
                    }
                )

                if (result.isSuccess) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(mediaUri, contentValues, null, null)
                    }

                    val completed = _currentTask.value.copy(
                        progress = 100,
                        status = DownloadStatus.SUCCESS,
                        filePath = mediaUri.toString()
                    )
                    _currentTask.value = completed
                    com.raoprime.downloadivo.data.DownloadRepository.updateTask(completed)
                    showCompletionNotification(cleanTitle, true, mediaUri, isAudio)
                } else {
                    throw result.exceptionOrNull() ?: Exception("Download aborted")
                }

            } catch (e: Exception) {
                mediaUri?.let { uri ->
                    try { applicationContext.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }

                val failed = _currentTask.value.copy(
                    status = DownloadStatus.ERROR,
                    error = e.localizedMessage ?: "Download failed"
                )
                _currentTask.value = failed
                com.raoprime.downloadivo.data.DownloadRepository.updateTask(failed)
                showCompletionNotification(cleanTitle, false, null, isAudio)
            } finally {
                try { outStream?.close() } catch (_: Exception) {}
                isDownloading = false
                processNextInQueue()
            }
        }
    }

    private fun processNextInQueue() {
        if (pendingQueue.isNotEmpty()) {
            val next = pendingQueue.removeFirst()
            updateQueuedState()
            beginDownload(next)
        } else {
            _queuedTasks.value = emptyList()
            if (wakeLock?.isHeld == true) wakeLock?.release()
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    private fun cancelCurrentDownload() {
        downloadJob?.cancel()
        val cancelled = _currentTask.value.copy(
            status = DownloadStatus.CANCELLED,
            progress = 0
        )
        _currentTask.value = cancelled
        com.raoprime.downloadivo.data.DownloadRepository.updateTask(cancelled)
        isDownloading = false
        processNextInQueue()
    }

    private fun removeQueued(taskId: String) {
        pendingQueue.removeAll { it.id == taskId }
        updateQueuedState()
        com.raoprime.downloadivo.data.DownloadRepository.removeTask(taskId)
    }

    private fun startForegroundNotification(title: String, progress: Int, speed: String, eta: String = "") {
        val notification = buildProgressNotification(title, progress, speed, eta)
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

    private fun updateForegroundNotification(title: String, progress: Int, speed: String, eta: String = "") {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(1001, buildProgressNotification(title, progress, speed, eta))
    }

    private fun buildProgressNotification(title: String, progress: Int, speed: String, eta: String = ""): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val queueCount = pendingQueue.size
        val queueSubtext = if (queueCount > 0) " +$queueCount in queue" else ""

        val downloadedBytes = _currentTask.value.bytesDownloaded
        val totalBytes = _currentTask.value.totalBytes

        val statusText = buildString {
            if (downloadedBytes > 0) {
                append(formatByteSize(downloadedBytes))
                if (totalBytes > 0) {
                    append(" / ${formatByteSize(totalBytes)}")
                }
                if (progress >= 0) append(" ($progress%)")
            } else {
                if (progress >= 0) append("$progress%") else append("Connecting")
            }
            if (speed.isNotBlank()) append(" • $speed")
            if (eta.isNotBlank()) append(" • $eta")
            append(queueSubtext)
        }

        val builder = NotificationCompat.Builder(this, DownloadivoApp.CHANNEL_ID)
            .setContentTitle("⚡ Downloading: $title")
            .setContentText(statusText)
            .setSubText(if (speed.isNotBlank()) speed else "Downloadivo Turbo")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, if (progress >= 0) progress else 0, progress < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        currentThumbnailBitmap?.let {
            builder.setLargeIcon(it)
        }

        return builder.build()
    }

    private fun showCompletionNotification(title: String, isSuccess: Boolean, mediaUri: Uri?, isAudio: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (!isSuccess || mediaUri == null) {
            val retryIntent = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val failedNotif = NotificationCompat.Builder(this, DownloadivoApp.CHANNEL_COMPLETED_ID)
                .setContentTitle("Download Failed")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setContentIntent(retryIntent)
                .build()
            manager.notify(System.currentTimeMillis().toInt(), failedNotif)
            return
        }

        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(mediaUri, if (isAudio) "audio/*" else "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val playPendingIntent = PendingIntent.getActivity(
            this,
            2,
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (isAudio) "audio/*" else "video/*"
            putExtra(Intent.EXTRA_STREAM, mediaUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val shareChooser = Intent.createChooser(shareIntent, "Share Media")
        val sharePendingIntent = PendingIntent.getActivity(
            this,
            3,
            shareChooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val folderName = if (isAudio) "Music/Downloadivo" else "Movies/Downloadivo"

        val builder = NotificationCompat.Builder(this, DownloadivoApp.CHANNEL_COMPLETED_ID)
            .setContentTitle("✓ Download Complete")
            .setContentText(title)
            .setSubText("Saved to $folderName")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(playPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent)
            .addAction(android.R.drawable.ic_menu_share, "Share", sharePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (currentThumbnailBitmap != null) {
            builder.setLargeIcon(currentThumbnailBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(currentThumbnailBitmap)
                    .setSummaryText("$title • Saved to $folderName")
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$title\nSaved to $folderName. Tap to play or share."))
        }

        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun formatByteSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format("%.2f GB", mb / 1024.0)
        } else if (mb >= 1.0) {
            String.format("%.1f MB", mb)
        } else {
            val kb = bytes / 1024.0
            String.format("%.0f KB", kb)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
}
