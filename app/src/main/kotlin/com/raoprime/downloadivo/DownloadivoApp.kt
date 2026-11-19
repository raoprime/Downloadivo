package com.raoprime.downloadivo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DownloadivoApp : Application() {

    companion object {
        const val CHANNEL_ID = "downloadivo_channel"
        const val CHANNEL_NAME = "Media Downloads"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        com.raoprime.downloadivo.data.DownloadRepository.init(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress and line-rate speed"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
