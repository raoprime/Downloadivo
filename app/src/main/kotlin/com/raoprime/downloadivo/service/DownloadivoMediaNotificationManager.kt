package com.raoprime.downloadivo.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.exoplayer.ExoPlayer
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.raoprime.downloadivo.DownloadivoApp
import com.raoprime.downloadivo.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadivoMediaNotificationManager(
    private val context: Context,
    private val exoPlayer: ExoPlayer
) {
    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_PLAY_PAUSE = "com.raoprime.downloadivo.ACTION_PLAY_PAUSE"
        const val ACTION_REWIND = "com.raoprime.downloadivo.ACTION_REWIND"
        const val ACTION_FORWARD = "com.raoprime.downloadivo.ACTION_FORWARD"
        const val ACTION_DISMISS = "com.raoprime.downloadivo.ACTION_DISMISS"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(Dispatchers.Main)
    private var isReceiverRegistered = false
    private var lastThumbnailUrl: String? = null
    private var cachedThumbnailBitmap: Bitmap? = null

    private val mediaActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
                    try {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    } catch (_: Exception) {}
                }
                ACTION_REWIND -> {
                    try {
                        val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                    } catch (_: Exception) {}
                }
                ACTION_FORWARD -> {
                    try {
                        val d = exoPlayer.duration
                        val max = if (d > 0) d else Long.MAX_VALUE
                        val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(max)
                        exoPlayer.seekTo(target)
                    } catch (_: Exception) {}
                }
                ACTION_DISMISS -> {
                    try {
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                    } catch (_: Exception) {}
                    dismiss()
                }
            }
        }
    }

    fun register() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_PLAY_PAUSE)
                addAction(ACTION_REWIND)
                addAction(ACTION_FORWARD)
                addAction(ACTION_DISMISS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(mediaActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(mediaActionReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    fun unregister() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(mediaActionReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        dismiss()
    }

    fun updateNotification(title: String, author: String, thumbnail: String, isPlaying: Boolean) {
        if (thumbnail != lastThumbnailUrl && thumbnail.isNotBlank()) {
            lastThumbnailUrl = thumbnail
            scope.launch(Dispatchers.IO) {
                try {
                    val loader = Coil.imageLoader(context)
                    val req = ImageRequest.Builder(context)
                        .data(thumbnail)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(req)
                    if (result is SuccessResult) {
                        cachedThumbnailBitmap = result.drawable.toBitmap(300, 300)
                        postNotification(title, author, isPlaying)
                    }
                } catch (_: Exception) {}
            }
        }
        postNotification(title, author, isPlaying)
    }

    private fun postNotification(title: String, author: String, isPlaying: Boolean) {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val playPauseIntent = Intent(ACTION_PLAY_PAUSE)
        val playPausePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val rewindIntent = Intent(ACTION_REWIND)
        val rewindPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            rewindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val forwardIntent = Intent(ACTION_FORWARD)
        val forwardPendingIntent = PendingIntent.getBroadcast(
            context,
            3,
            forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val dismissIntent = Intent(ACTION_DISMISS)
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            4,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(context, DownloadivoApp.CHANNEL_MEDIA_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (title.isNotBlank()) title else "Downloadivo Playing")
            .setContentText(if (author.isNotBlank()) author else "Downloadivo Media Player")
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "-10s", rewindPendingIntent)
            .addAction(playPauseIcon, playPauseLabel, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "+10s", forwardPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", dismissPendingIntent)

        cachedThumbnailBitmap?.let {
            builder.setLargeIcon(it)
        }

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {}
    }

    fun dismiss() {
        try {
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
