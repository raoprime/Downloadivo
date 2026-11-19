package com.raoprime.downloadivo.model

import kotlinx.serialization.Serializable

@Serializable
data class FormatOption(
    val id: String,
    val label: String,
    val ext: String = "mp4",
    val isAudio: Boolean = false,
    val note: String = "",
    val resolution: Int = 0
)

@Serializable
data class MediaInfo(
    val title: String = "",
    val author: String = "",
    val duration: String = "",
    val thumbnail: String = "",
    val url: String = "",
    val extractor: String = "Web",
    val isYoutube: Boolean = false,
    val is4k: Boolean = false,
    val is8k: Boolean = false,
    val resolutions: List<Int> = emptyList(),
    val availableFormats: List<FormatOption> = listOf(
        FormatOption("best", "Best Video (Max Quality)", "mp4", false, "Original Stream"),
        FormatOption("1080p", "1080p Full HD", "mp4", false, "1920x1080", 1080),
        FormatOption("720p", "720p HD", "mp4", false, "1280x720", 720),
        FormatOption("480p", "480p Standard", "mp4", false, "854x480", 480),
        FormatOption("360p", "360p Data Saver", "mp4", false, "640x360", 360),
        FormatOption("mp3", "320kbps MP3 Audio", "mp3", true, "Studio Quality Audio"),
        FormatOption("m4a", "Lossless AAC Stream", "m4a", true, "Original Audio")
    )
)

enum class DownloadStatus {
    IDLE,
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    SUCCESS,
    ERROR,
    CANCELLED
}

@Serializable
data class DownloadTask(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val format: String = "best",
    val thumbnail: String = "",
    val progress: Int = 0,
    val speed: String = "",
    val eta: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val error: String? = null,
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isAudio: Boolean = false,
    val startTime: String? = null,
    val endTime: String? = null
)
