package com.raoprime.downloadivo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DownloadivoApp : Application() {

    companion object {
        const val CHANNEL_ID = "downloadivo_channel"
        const val CHANNEL_NAME = "Live Download Progress"

        const val CHANNEL_COMPLETED_ID = "downloadivo_completed_channel"
        const val CHANNEL_COMPLETED_NAME = "Completed Downloads"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        com.raoprime.downloadivo.data.DownloadRepository.init(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Progress channel (silent/low priority so it doesn't chime repeatedly on each chunk)
            val progressChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time line-rate download speed, progress and ETA"
                setShowBadge(false)
            }

            // Completion channel (high priority with sound/vibration when file is done)
            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED_ID,
                CHANNEL_COMPLETED_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts immediately with 1-tap playback and share when download finishes"
                enableVibration(true)
                setShowBadge(true)
            }

            manager?.createNotificationChannel(progressChannel)
            manager?.createNotificationChannel(completedChannel)
        }
    }
}
