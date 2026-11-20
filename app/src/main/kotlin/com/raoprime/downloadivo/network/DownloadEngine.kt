package com.raoprime.downloadivo.network

import com.raoprime.downloadivo.model.FormatOption
import com.raoprime.downloadivo.model.MediaInfo
import com.raoprime.downloadivo.model.formatMediaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object DownloadEngine {

    // Default Edge Cloud Endpoint (Can be overridden in Settings)
    var activeBaseUrl: String = "https://police-urge-prefix-journal.trycloudflare.com"

    // High-Speed Direct Tunnel Endpoint
    var directStreamBaseUrl: String = "https://police-urge-prefix-journal.trycloudflare.com"

    private val apiClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun sanitizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val regex = Regex("""https?://[^\s<>"'{}|\\^`]+""")
        val match = regex.find(trimmed)?.value ?: trimmed
        return match.trimEnd('.', ',', '!', '?', ')', ']', '}', '>', ';', '"', '\'')
    }

    fun extractAllUrls(rawText: String): List<String> {
        val regex = Regex("""https?://[^\s<>"'{}|\\^`]+""")
        return regex.findAll(rawText).map { match ->
            match.value.trimEnd('.', ',', '!', '?', ')', ']', '}', '>', ';', '"', '\'')
        }.filter { it.startsWith("http://") || it.startsWith("https://") }.distinct().toList()
    }

    fun getCandidateBaseUrls(): List<String> {
        val list = mutableListOf<String>()
        if (directStreamBaseUrl.isNotBlank()) list.add(directStreamBaseUrl.trim().trimEnd('/'))
        if (activeBaseUrl.isNotBlank()) list.add(activeBaseUrl.trim().trimEnd('/'))
        list.add("https://police-urge-prefix-journal.trycloudflare.com")
        list.add("https://downloadivo.vercel.app")
        return list.filter { it.startsWith("http") }.distinct()
    }

    suspend fun fetchMediaInfo(targetUrl: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(targetUrl)
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(Exception("Please provide a valid URL"))
        }

        val candidates = getCandidateBaseUrls()
        var lastErrorMsg = "Unable to connect to download servers"

        for (candidate in candidates) {
            try {
                val encoded = URLEncoder.encode(cleanUrl, "UTF-8")
                val getReq = Request.Builder()
                    .url("$candidate/info?url=$encoded")
                    .header("User-Agent", "Downloadivo-Android/5.0")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                var responseBody: String? = null
                var responseCode = 0

                try {
                    apiClient.newCall(getReq).execute().use { resp ->
                        responseCode = resp.code
                        if (resp.isSuccessful) {
                            responseBody = resp.body?.string()
                        }
                    }
                } catch (_: Exception) {
                    // Ignore and proceed to POST fallback
                }

                // Fallback to POST if GET returned error or empty
                if (responseBody == null) {
                    val jsonBody = JSONObject().put("url", cleanUrl).toString()
                    val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                    val requestBody = jsonBody.toRequestBody(mediaType)
                    val postReq = Request.Builder()
                        .url("$candidate/info")
                        .header("User-Agent", "Downloadivo-Android/5.0")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .post(requestBody)
                        .build()

                    try {
                        apiClient.newCall(postReq).execute().use { resp ->
                            responseCode = resp.code
                            if (resp.isSuccessful) {
                                responseBody = resp.body?.string()
                            }
                        }
                    } catch (_: Exception) {
                        // Candidate failed
                    }
                }

                if (!responseBody.isNullOrBlank()) {
                    val json = JSONObject(responseBody!!)

                    // Auto-discover active high-speed streaming tunnel
                    val tunnelUrl = json.optString("tunnel_url").ifBlank { json.optString("public_url") }
                    if (tunnelUrl.isNotBlank() && (tunnelUrl.startsWith("http://") || tunnelUrl.startsWith("https://"))) {
                        directStreamBaseUrl = tunnelUrl.trim().trimEnd('/')
                    } else {
                        directStreamBaseUrl = candidate
                    }
                    activeBaseUrl = candidate

            val title = json.optString("title", "Media Stream")
            val author = json.optString("uploader", json.optString("author", "Web Creator"))
            val duration = formatMediaDuration(json.optString("duration", ""))
            val thumbnail = json.optString("thumbnail", "")
            val extractor = json.optString("extractor", "Web Stream")
            val isYoutube = json.optBoolean("is_youtube", false)
            val is4k = json.optBoolean("is_4k", false)
            val is8k = json.optBoolean("is_8k", false)

            // Parse available resolutions dynamically
            val resolutions = mutableListOf<Int>()
            val resArr = json.optJSONArray("resolutions")
            if (resArr != null) {
                for (i in 0 until resArr.length()) {
                    val item = resArr.opt(i)
                    when (item) {
                        is Int -> resolutions.add(item)
                        is String -> {
                            val num = Regex("\\d+").find(item)?.value?.toIntOrNull()
                            if (num != null) resolutions.add(num)
                        }
                    }
                }
            }

            // Parse format sizes if returned by backend
            val formatSizesJson = json.optJSONObject("format_sizes")
            val sizeMap = mutableMapOf<String, String>()
            if (formatSizesJson != null) {
                val keys = formatSizesJson.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    sizeMap[k] = formatSizesJson.optString(k, "")
                }
            }

            val formatSizesBytesJson = json.optJSONObject("format_sizes_bytes")
            val bytesMap = mutableMapOf<String, Long>()
            if (formatSizesBytesJson != null) {
                val keys = formatSizesBytesJson.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    bytesMap[k] = formatSizesBytesJson.optLong(k, 0L)
                }
            }

            // Build dynamic Seal-grade format matrix with file sizes
            val formats = mutableListOf<FormatOption>()
            val maxRes = if (resolutions.isNotEmpty()) (resolutions.maxOrNull() ?: 1080) else 1080
            val bestSize = (sizeMap["best"] ?: sizeMap["4k"] ?: sizeMap["2160"] ?: sizeMap["1080p"] ?: sizeMap["1080"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(1080, false, "best", duration)
            }
            val bestBytes = bytesMap["best"] ?: bytesMap["4k"] ?: bytesMap["2160"] ?: bytesMap["1080p"] ?: bytesMap["1080"] ?: 0L
            formats.add(FormatOption("best", "Best Video (Max Quality)", "mp4", false, bestSize, 0, bestSize, bestBytes))

            if (is4k || resolutions.contains(2160) || maxRes >= 2160 || is8k) {
                val sz = (sizeMap["4k"] ?: sizeMap["2160"] ?: sizeMap["2160p"] ?: "").ifBlank {
                    com.raoprime.downloadivo.model.estimateFormatSize(2160, false, "4k", duration)
                }
                val b = bytesMap["4k"] ?: bytesMap["2160"] ?: bytesMap["2160p"] ?: 0L
                formats.add(FormatOption("4k", "4K UHD (2160p)", "mp4", false, sz, 2160, sz, b))
            }
            if (resolutions.contains(1440) || maxRes >= 1440) {
                val sz = (sizeMap["1440p"] ?: sizeMap["1440"] ?: sizeMap["2k"] ?: "").ifBlank {
                    com.raoprime.downloadivo.model.estimateFormatSize(1440, false, "1440p", duration)
                }
                val b = bytesMap["1440p"] ?: bytesMap["1440"] ?: bytesMap["2k"] ?: 0L
                formats.add(FormatOption("1440p", "2K QHD (1440p)", "mp4", false, sz, 1440, sz, b))
            }
            if (maxRes >= 1080 || resolutions.contains(1080) || resolutions.isEmpty()) {
                val sz = (sizeMap["1080p"] ?: sizeMap["1080"] ?: "").ifBlank {
                    com.raoprime.downloadivo.model.estimateFormatSize(1080, false, "1080p", duration)
                }
                val b = bytesMap["1080p"] ?: bytesMap["1080"] ?: 0L
                formats.add(FormatOption("1080p", "1080p Full HD", "mp4", false, sz, 1080, sz, b))
            }
            if (maxRes >= 720 || resolutions.contains(720) || resolutions.isEmpty()) {
                val sz = (sizeMap["720p"] ?: sizeMap["720"] ?: "").ifBlank {
                    com.raoprime.downloadivo.model.estimateFormatSize(720, false, "720p", duration)
                }
                val b = bytesMap["720p"] ?: bytesMap["720"] ?: 0L
                formats.add(FormatOption("720p", "720p HD", "mp4", false, sz, 720, sz, b))
            }
            // Standard and Data Saver formats are always available for bandwidth efficiency
            val sz480 = (sizeMap["480p"] ?: sizeMap["480"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(480, false, "480p", duration)
            }
            val b480 = bytesMap["480p"] ?: bytesMap["480"] ?: 0L
            formats.add(FormatOption("480p", "480p Standard", "mp4", false, sz480, 480, sz480, b480))

            val sz360 = (sizeMap["360p"] ?: sizeMap["360"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(360, false, "360p", duration)
            }
            val b360 = bytesMap["360p"] ?: bytesMap["360"] ?: 0L
            formats.add(FormatOption("360p", "360p Data Saver", "mp4", false, sz360, 360, sz360, b360))

            // High Fidelity Audio Options with file sizes
            val mp3Size = (sizeMap["mp3"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(0, true, "mp3", duration)
            }
            val mp3Bytes = bytesMap["mp3"] ?: 0L
            val m4aSize = (sizeMap["m4a"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(0, true, "m4a", duration)
            }
            val m4aBytes = bytesMap["m4a"] ?: 0L
            formats.add(FormatOption("mp3", "320kbps MP3 Audio", "mp3", true, mp3Size, 0, mp3Size, mp3Bytes))
            formats.add(FormatOption("m4a", "Lossless AAC Stream", "m4a", true, m4aSize, 0, m4aSize, m4aBytes))

            val relatedFeed = mutableListOf<com.raoprime.downloadivo.model.FeedItem>()
            val relArr = json.optJSONArray("related_feed")
            if (relArr != null) {
                for (i in 0 until relArr.length()) {
                    val obj = relArr.optJSONObject(i)
                    if (obj != null) {
                        relatedFeed.add(
                            com.raoprime.downloadivo.model.FeedItem(
                                title = obj.optString("title", ""),
                                url = obj.optString("url", ""),
                                thumbnail = obj.optString("thumbnail", ""),
                                uploader = obj.optString("uploader", ""),
                                duration = formatMediaDuration(obj.optString("duration", ""))
                            )
                        )
                    }
                }
            }

            val audioTracksList = mutableListOf<String>()
            val audioTracksJson = json.optJSONArray("audio_tracks")
            if (audioTracksJson != null) {
                for (i in 0 until audioTracksJson.length()) {
                    val aObj = audioTracksJson.optJSONObject(i)
                    val label = aObj?.optString("label") ?: aObj?.optString("language") ?: ""
                    if (label.isNotBlank()) audioTracksList.add(label)
                }
            }

            val info = MediaInfo(
                title = title,
                author = author,
                duration = duration,
                thumbnail = thumbnail,
                url = cleanUrl,
                extractor = extractor,
                isYoutube = isYoutube,
                is4k = is4k,
                is8k = is8k,
                resolutions = resolutions,
                availableFormats = formats,
                audioTracks = if (audioTracksList.isNotEmpty()) audioTracksList else listOf("Original Audio"),
                relatedFeed = relatedFeed
            )

                    return@withContext Result.success(info)
                } else {
                    lastErrorMsg = "Server $candidate returned HTTP $responseCode"
                }
            } catch (e: Exception) {
                lastErrorMsg = e.message ?: "Connection failure with $candidate"
            }
        }

        return@withContext Result.failure(Exception(lastErrorMsg))
    }

    fun getStreamUrl(targetUrl: String, format: String = "best", audioLang: String? = null): String {
        val cleanUrl = sanitizeUrl(targetUrl)
        val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
        val encodedFmt = URLEncoder.encode(format.trim(), "UTF-8")
        val endpoint = if (format.trim().lowercase() in listOf("mp3", "m4a", "audio")) "stream.mp3" else "stream.mp4"
        val audioQuery = if (!audioLang.isNullOrBlank()) "&audio_lang=" + URLEncoder.encode(audioLang.trim(), "UTF-8") else ""
        val baseUrl = if (directStreamBaseUrl.isNotBlank()) directStreamBaseUrl else activeBaseUrl
        return "$baseUrl/$endpoint?url=$encodedUrl&format=$encodedFmt$audioQuery"
    }

    fun parseDurationToMillis(durationStr: String?): Long {
        if (durationStr.isNullOrBlank()) return 0L
        val parts = durationStr.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            1 -> parts[0] * 1000L
            else -> 0L
        }
    }

    suspend fun downloadStream(
        targetUrl: String,
        format: String,
        startTime: String? = null,
        endTime: String? = null,
        expectedTotalBytes: Long = 0L,
        outputStream: OutputStream,
        onProgress: (percent: Int, speedStr: String, etaStr: String, currentBytes: Long, totalBytes: Long) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        val bases = (listOfNotNull(
            directStreamBaseUrl.takeIf { it.isNotBlank() },
            activeBaseUrl
        ) + getCandidateBaseUrls()).filter { it.startsWith("http") }.distinct()

        var totalBytesRead = 0L
        var totalLength = if (expectedTotalBytes > 0L) expectedTotalBytes else -1L
        var lastError: Exception? = null
        val maxAttempts = 4
        var attempt = 0

        val cleanUrl = sanitizeUrl(targetUrl)
        val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
        val encodedFmt = URLEncoder.encode(format.trim(), "UTF-8")

        while (attempt < maxAttempts) {
            attempt++
            val currentBase = bases[(attempt - 1) % bases.size]
            var streamUrl = "$currentBase/stream?url=$encodedUrl&format=$encodedFmt"

            if (!startTime.isNullOrBlank() && !endTime.isNullOrBlank()) {
                val encStart = URLEncoder.encode(startTime.trim(), "UTF-8")
                val encEnd = URLEncoder.encode(endTime.trim(), "UTF-8")
                streamUrl += "&start_time=$encStart&end_time=$encEnd"
            }

            try {
                val reqBuilder = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Downloadivo-Android/4.0")
                    .header("Accept-Encoding", "identity")

                if (totalBytesRead > 0L) {
                    reqBuilder.header("Range", "bytes=$totalBytesRead-")
                }

                val response = streamClient.newCall(reqBuilder.build()).execute()

                if (!response.isSuccessful && response.code != 206) {
                    val code = response.code
                    response.close()
                    throw Exception("Server returned HTTP error code: $code")
                }

                val body = response.body ?: throw Exception("No response body received")
                val isPartial = response.code == 206 || response.header("Content-Range") != null

                if (totalLength <= 0L) {
                    val headerLength = response.header("X-Expected-Total-Bytes")?.toLongOrNull()
                        ?: response.header("X-Total-Content-Length")?.toLongOrNull()
                        ?: response.header("Content-Length")?.toLongOrNull()
                        ?: body.contentLength()
                    if (headerLength != null && headerLength > 0L) {
                        totalLength = if (isPartial) totalBytesRead + headerLength else headerLength
                    }
                }

                val inputStream: InputStream = body.byteStream()
                val buffer = ByteArray(256 * 1024)
                var bytesRead: Int

                var lastTime = System.currentTimeMillis()
                var bytesSinceLastTime = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    bytesSinceLastTime += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastTime

                    if (timeDiff >= 400) {
                        val speedBytesPerSec = (bytesSinceLastTime * 1000.0) / timeDiff
                        val speedStr = formatSpeed(speedBytesPerSec)

                        val percent = if (totalLength > 0) {
                            ((totalBytesRead * 100) / totalLength).toInt().coerceIn(0, 99)
                        } else {
                            -1
                        }

                        val etaStr = if (totalLength > totalBytesRead && speedBytesPerSec > 1024) {
                            val remainingBytes = totalLength - totalBytesRead
                            val secondsLeft = (remainingBytes / speedBytesPerSec).toLong()
                            formatEta(secondsLeft)
                        } else {
                            ""
                        }

                        onProgress(percent, speedStr, etaStr, totalBytesRead, totalLength)

                        lastTime = now
                        bytesSinceLastTime = 0L
                    }
                }

                outputStream.flush()
                response.close()

                if (totalLength > 0L && totalBytesRead >= totalLength) {
                    onProgress(100, "Done", "0s", totalBytesRead, totalBytesRead)
                    return@withContext Result.success(totalBytesRead)
                } else if (totalLength <= 0L && totalBytesRead > 0L) {
                    onProgress(100, "Done", "0s", totalBytesRead, totalBytesRead)
                    return@withContext Result.success(totalBytesRead)
                }
            } catch (e: Exception) {
                lastError = e
                if (totalLength > 0L && totalBytesRead >= totalLength) {
                    outputStream.flush()
                    onProgress(100, "Done", "0s", totalBytesRead, totalBytesRead)
                    return@withContext Result.success(totalBytesRead)
                }
                kotlinx.coroutines.delay(1000L)
            }
        }

        if (totalBytesRead > 0L) {
            outputStream.flush()
            onProgress(100, "Done", "0s", totalBytesRead, totalBytesRead)
            Result.success(totalBytesRead)
        } else {
            Result.failure(lastError ?: Exception("Download aborted or zero bytes received"))
        }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }

    private fun formatEta(seconds: Long): String {
        return when {
            seconds >= 3600 -> String.format("%dh %02dm", seconds / 3600, (seconds % 3600) / 60)
            seconds >= 60 -> String.format("%02dm %02ds", seconds / 60, seconds % 60)
            else -> "${seconds}s remaining"
        }
    }
}
