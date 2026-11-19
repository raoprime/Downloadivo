package com.raoprime.downloadivo.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaInfo(
    val title: String = "",
    val author: String = "",
    val duration: String = "",
    val thumbnail: String = "",
    val url: String = ""
)

enum class DownloadStatus {
    IDLE,
    RESOLVING,
    DOWNLOADING,
    SUCCESS,
    ERROR
}

data class DownloadTask(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val format: String = "best",
    val progress: Int = 0,
    val speed: String = "",
    val eta: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val error: String? = null,
    val filePath: String? = null
)
