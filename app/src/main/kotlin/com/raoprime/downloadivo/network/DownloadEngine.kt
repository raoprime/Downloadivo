package com.raoprime.downloadivo.network

import com.raoprime.downloadivo.model.FormatOption
import com.raoprime.downloadivo.model.MediaInfo
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
    var activeBaseUrl: String = "https://downloadivo.vercel.app"

    private val client = OkHttpClient.Builder()
        .connectTimeout(35, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
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

    suspend fun fetchMediaInfo(targetUrl: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(targetUrl)
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(Exception("Please provide a valid URL"))
        }

        try {
            val encoded = URLEncoder.encode(cleanUrl, "UTF-8")
            val getReq = Request.Builder()
                .url("$activeBaseUrl/info?url=$encoded")
                .header("User-Agent", "Downloadivo-Android/2.0")
                .header("Accept", "application/json")
                .get()
                .build()

            var responseBody: String? = null
            var responseCode = 0

            try {
                client.newCall(getReq).execute().use { resp ->
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
                    .url("$activeBaseUrl/info")
                    .header("User-Agent", "Downloadivo-Android/2.0")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(postReq).execute().use { resp ->
                    responseCode = resp.code
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("HTTP $responseCode: Could not analyze media. Check URL and connectivity.")
                        )
                    }
                    responseBody = resp.body?.string()
                }
            }

            if (responseBody.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Empty response from media server"))
            }

            val json = JSONObject(responseBody!!)
            val title = json.optString("title", "Media Stream")
            val author = json.optString("uploader", json.optString("author", "Web Creator"))
            val duration = json.optString("duration", "")
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

            // Build dynamic Seal-grade format matrix with file sizes
            val formats = mutableListOf<FormatOption>()
            val bestSize = sizeMap["best"] ?: sizeMap["4k"] ?: sizeMap["2160"] ?: sizeMap["1080p"] ?: sizeMap["1080"] ?: ""
            formats.add(FormatOption("best", "Best Video (Max Quality)", "mp4", false, "Original Quality", 0, bestSize))

            if (is4k || resolutions.contains(2160) || is8k) {
                val sz = sizeMap["4k"] ?: sizeMap["2160"] ?: sizeMap["2160p"] ?: ""
                formats.add(FormatOption("4k", "4K UHD (2160p)", "mp4", false, "Ultra HD", 2160, sz))
            }
            if (resolutions.contains(1440)) {
                val sz = sizeMap["1440p"] ?: sizeMap["1440"] ?: sizeMap["2k"] ?: ""
                formats.add(FormatOption("1440p", "2K QHD (1440p)", "mp4", false, "Quad HD", 1440, sz))
            }
            if (resolutions.contains(1080) || (!resolutions.isEmpty() && resolutions.maxOrNull() ?: 0 >= 1080)) {
                val sz = sizeMap["1080p"] ?: sizeMap["1080"] ?: ""
                formats.add(FormatOption("1080p", "1080p Full HD", "mp4", false, "1920x1080", 1080, sz))
            }
            if (resolutions.contains(720) || resolutions.isEmpty()) {
                val sz = sizeMap["720p"] ?: sizeMap["720"] ?: ""
                formats.add(FormatOption("720p", "720p HD", "mp4", false, "1280x720", 720, sz))
            }
            if (resolutions.contains(480) || resolutions.isEmpty()) {
                val sz = sizeMap["480p"] ?: sizeMap["480"] ?: ""
                formats.add(FormatOption("480p", "480p Standard", "mp4", false, "854x480", 480, sz))
            }
            if (resolutions.contains(360) || resolutions.isEmpty()) {
                val sz = sizeMap["360p"] ?: sizeMap["360"] ?: ""
                formats.add(FormatOption("360p", "360p Data Saver", "mp4", false, "640x360", 360, sz))
            }

            // High Fidelity Audio Options with file sizes
            val mp3Size = sizeMap["mp3"] ?: ""
            val m4aSize = sizeMap["m4a"] ?: ""
            formats.add(FormatOption("mp3", "320kbps MP3 Audio", "mp3", true, "Studio Fidelity", 0, mp3Size))
            formats.add(FormatOption("m4a", "Lossless AAC Stream", "m4a", true, "Original Sound", 0, m4aSize))

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
                                duration = obj.optString("duration", "")
                            )
                        )
                    }
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
                relatedFeed = relatedFeed
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getStreamUrl(targetUrl: String, format: String = "best"): String {
        val cleanUrl = sanitizeUrl(targetUrl)
        val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
        val encodedFmt = URLEncoder.encode(format.trim(), "UTF-8")
        return "$activeBaseUrl/stream?url=$encodedUrl&format=$encodedFmt"
    }

    suspend fun downloadStream(
        targetUrl: String,
        format: String,
        startTime: String? = null,
        endTime: String? = null,
        outputStream: OutputStream,
        onProgress: (percent: Int, speedStr: String, etaStr: String, currentBytes: Long, totalBytes: Long) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = sanitizeUrl(targetUrl)
            val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
            val encodedFmt = URLEncoder.encode(format.trim(), "UTF-8")
            var streamUrl = "$activeBaseUrl/stream?url=$encodedUrl&format=$encodedFmt"

            if (!startTime.isNullOrBlank() && !endTime.isNullOrBlank()) {
                val encStart = URLEncoder.encode(startTime.trim(), "UTF-8")
                val encEnd = URLEncoder.encode(endTime.trim(), "UTF-8")
                streamUrl += "&start_time=$encStart&end_time=$encEnd"
            }

            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Downloadivo-Android/2.0")
                .header("Accept-Encoding", "identity")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Server returned HTTP error code: ${response.code}")
                    )
                }

                val body = response.body ?: throw Exception("No response data received from server")
                val totalLength = body.contentLength()
                val inputStream: InputStream = body.byteStream()

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

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
                            ((totalBytesRead * 100) / totalLength).toInt().coerceIn(0, 100)
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
                onProgress(100, "Done", "0s", totalBytesRead, totalBytesRead)
                Result.success(totalBytesRead)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
