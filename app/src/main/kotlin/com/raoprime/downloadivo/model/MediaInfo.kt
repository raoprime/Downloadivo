package com.raoprime.downloadivo.model

import kotlinx.serialization.Serializable

@Serializable
data class FormatOption(
    val id: String,
    val label: String,
    val ext: String = "mp4",
    val isAudio: Boolean = false,
    val note: String = "",
    val resolution: Int = 0,
    val sizeStr: String = "",
    val sizeBytes: Long = 0L
) {
    fun getEffectiveSize(duration: String = ""): String {
        if (sizeStr.isNotBlank() && sizeStr != "0 KB" && sizeStr != "0 B" && sizeStr != "0.0 MB") {
            return sizeStr
        }
        if (note.isNotBlank() && (note.contains("MB", ignoreCase = true) || note.contains("GB", ignoreCase = true) || note.contains("KB", ignoreCase = true))) {
            return note
        }
        return estimateFormatSize(resolution, isAudio, id, duration)
    }
}

@Serializable
data class FeedItem(
    val title: String = "",
    val url: String = "",
    val thumbnail: String = "",
    val uploader: String = "",
    val duration: String = ""
)

@Serializable
data class AppUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val changelog: String = "",
    val forceUpdate: Boolean = false
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
        FormatOption("best", "Best Video (Max Quality)", "mp4", false, "~85 MB", 0, "~85 MB"),
        FormatOption("1080p", "1080p Full HD", "mp4", false, "~75 MB", 1080, "~75 MB"),
        FormatOption("720p", "720p HD", "mp4", false, "~38 MB", 720, "~38 MB"),
        FormatOption("480p", "480p Standard", "mp4", false, "~18 MB", 480, "~18 MB"),
        FormatOption("360p", "360p Data Saver", "mp4", false, "~9.5 MB", 360, "~9.5 MB"),
        FormatOption("mp3", "320kbps MP3 Audio", "mp3", true, "~8.2 MB", 0, "~8.2 MB"),
        FormatOption("m4a", "Lossless AAC Stream", "m4a", true, "~3.5 MB", 0, "~3.5 MB")
    ),
    val audioTracks: List<String> = emptyList(),
    val relatedFeed: List<FeedItem> = emptyList()
)

enum class DownloadStatus {
    IDLE,
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    PROCESSING,
    SAVING,
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
    val duration: String = "",
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
) {
    val displayTitle: String
        get() {
            if (title.isNotBlank()) return title
            if (!filePath.isNullOrBlank()) {
                val name = java.io.File(filePath).nameWithoutExtension
                if (name.isNotBlank()) return name
            }
            return "Media Download"
        }
}

fun formatMediaDuration(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val trimmed = raw.trim()
    if (Regex("^\\d+:\\d{2}(:\\d{2})?$").matches(trimmed)) {
        return trimmed
    }
    val sec = trimmed.toDoubleOrNull()?.toLong()
    if (sec != null && sec > 0) {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }
    }
    val iso = Regex("^PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
    if (iso != null) {
        val h = iso.groupValues[1].toLongOrNull() ?: 0L
        val m = iso.groupValues[2].toLongOrNull() ?: 0L
        val s = iso.groupValues[3].toLongOrNull() ?: 0L
        return if (h > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", m, s)
        }
    }
    return trimmed
}

fun parseDurationToSeconds(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    val trimmed = raw.trim()
    val parts = trimmed.split(":")
    if (parts.size == 2) {
        val m = parts[0].toLongOrNull() ?: 0L
        val s = parts[1].toLongOrNull() ?: 0L
        return m * 60 + s
    } else if (parts.size == 3) {
        val h = parts[0].toLongOrNull() ?: 0L
        val m = parts[1].toLongOrNull() ?: 0L
        val s = parts[2].toLongOrNull() ?: 0L
        return h * 3600 + m * 60 + s
    }
    return trimmed.toDoubleOrNull()?.toLong() ?: 0L
}

fun formatByteSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

fun estimateFormatSize(resolution: Int, isAudio: Boolean, id: String, durationStr: String): String {
    val sec = parseDurationToSeconds(durationStr)
    val kbps = when {
        isAudio -> when {
            id.contains("320") || id == "mp3" -> 320
            id.contains("256") -> 256
            id.contains("192") -> 192
            id.contains("128") -> 128
            id == "wav" -> 1411
            id == "m4a" -> 160
            else -> 192
        }
        id == "4k" || resolution >= 2160 -> 18000
        id == "1440p" || id == "2k" || resolution >= 1440 -> 9000
        id == "1080p" || resolution >= 1080 || id == "best" -> 4200
        id == "720p" || resolution >= 720 -> 2200
        id == "480p" || resolution >= 480 -> 1000
        id == "360p" || resolution >= 360 -> 500
        id == "240p" || resolution >= 240 -> 300
        id == "144p" || resolution >= 144 -> 150
        else -> 3500
    }
    if (sec > 0) {
        val bytes = (kbps.toLong() * 1000L * sec) / 8L
        return formatByteSize(bytes)
    }
    return when {
        isAudio -> when {
            id.contains("320") || id == "mp3" -> "~8.5 MB"
            id.contains("256") -> "~6.8 MB"
            id.contains("192") -> "~5.2 MB"
            id.contains("128") -> "~3.4 MB"
            id == "wav" -> "~38 MB"
            id == "m4a" -> "~4.2 MB"
            else -> "~5.0 MB"
        }
        id == "4k" || resolution >= 2160 -> "~350 MB"
        id == "1440p" || id == "2k" || resolution >= 1440 -> "~180 MB"
        id == "1080p" || resolution >= 1080 || id == "best" -> "~75 MB"
        id == "720p" || resolution >= 720 -> "~38 MB"
        id == "480p" || resolution >= 480 -> "~18 MB"
        id == "360p" || resolution >= 360 -> "~9.5 MB"
        id == "240p" || resolution >= 240 -> "~5.5 MB"
        id == "144p" || resolution >= 144 -> "~2.8 MB"
        else -> "~45 MB"
    }
}
