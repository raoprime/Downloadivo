package com.raoprime.downloadivo.network

import com.raoprime.downloadivo.BuildConfig
import com.raoprime.downloadivo.model.FormatOption
import com.raoprime.downloadivo.model.MediaInfo
import com.raoprime.downloadivo.model.AppUpdateInfo
import com.raoprime.downloadivo.model.formatMediaDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class StreamTransferMetrics(
    val initialSavedBytes: Long,
    val offsetRequested: Long,
    val bytesTransferredThisSession: Long,
    val totalFileBytes: Long,
    val isResumeSupported: Boolean,
    val wasCleanRestart: Boolean
)

object DownloadEngine {

    // Default Edge Cloud Endpoint (Can be overridden in Settings)
    var activeBaseUrl: String = "https://logging-show-mortality-parameters.trycloudflare.com"

    // High-Speed Direct Tunnel Endpoint
    var directStreamBaseUrl: String = "https://logging-show-mortality-parameters.trycloudflare.com"

    // Cloud Turbo Acceleration Preference (Persisted in SharedPreferences)
    var isCloudTurboEnabled: Boolean = true

    suspend fun testEndpoint(targetUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val clean = targetUrl.trim().trimEnd('/')
            if (clean.isBlank() || (!clean.startsWith("http://") && !clean.startsWith("https://"))) {
                return@withContext Result.failure(Exception("URL must start with http:// or https://"))
            }
            try {
                val healthReq = Request.Builder()
                    .url("$clean/health")
                    .header("User-Agent", "Downloadivo-Android")
                    .get()
                    .build()
                apiClient.newCall(healthReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext Result.success("Connected (${resp.code} OK)")
                    }
                }
            } catch (_: Exception) {}

            // Fallback: test root /
            val rootReq = Request.Builder()
                .url(clean)
                .header("User-Agent", "Downloadivo-Android")
                .get()
                .build()
            apiClient.newCall(rootReq).execute().use { resp ->
                if (resp.isSuccessful || resp.code < 500) {
                    Result.success("Connected (${resp.code} OK)")
                } else {
                    Result.failure(Exception("Server returned HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Connection failed / Unreachable"))
        }
    }

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
        list.add("https://logging-show-mortality-parameters.trycloudflare.com")
        list.add("https://downloadivo.vercel.app")
        return list.filter { it.startsWith("http") }.distinct()
    }

    suspend fun syncLatestTunnelUrl(): Unit = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://raw.githubusercontent.com/raoprime/Downloadivo/main/tunnel_url.txt")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            val resp = apiClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val url = resp.body?.string()?.trim()
                resp.close()
                if (!url.isNullOrBlank() && url.startsWith("https://") && url.contains("trycloudflare.com")) {
                    try {
                        val healthReq = Request.Builder().url("$url/health").get().build()
                        val healthResp = apiClient.newCall(healthReq).execute()
                        if (healthResp.isSuccessful) {
                            activeBaseUrl = url
                            directStreamBaseUrl = url
                        }
                        healthResp.close()
                    } catch (_: Exception) {
                        // Stale tunnel rejected; keep fallback
                    }
                }
            } else {
                resp.close()
            }
        } catch (_: Exception) {}
    }

    fun isSupportedMediaUrl(rawUrl: String): Boolean {
        val clean = sanitizeUrl(rawUrl).lowercase()
        if (clean.isBlank() || (!clean.startsWith("http://") && !clean.startsWith("https://"))) {
            return false
        }
        val supportedDomains = listOf(
            "youtube.com", "youtu.be",
            "instagram.com",
            "tiktok.com",
            "facebook.com", "fb.watch", "fb.com",
            "twitter.com", "x.com",
            "spotify.com", "spotify.link",
            "reddit.com", "redd.it",
            "pinterest.com", "pin.it",
            "threads.net",
            "soundcloud.com",
            "vimeo.com",
            "dailymotion.com",
            "twitch.tv"
        )
        val hasSupportedDomain = supportedDomains.any { clean.contains(it) }
        val hasDirectMediaExtension = listOf(".mp4", ".mp3", ".m4a", ".webm", ".mkv", ".wav", ".aac", ".flac", ".ogg").any { clean.contains(it) }
        return hasSupportedDomain || hasDirectMediaExtension
    }

    suspend fun fetchMediaInfo(targetUrl: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(targetUrl)
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(Exception("Please provide a valid URL"))
        }
        if (!isSupportedMediaUrl(cleanUrl)) {
            return@withContext Result.failure(Exception("Unsupported media link. Please paste a valid link from YouTube, Instagram, Facebook, TikTok, Twitter/X, Spotify, Reddit, Pinterest, etc."))
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

            // Build comprehensive format matrix with exact or estimated file sizes
            val formats = mutableListOf<FormatOption>()
            val maxRes = if (resolutions.isNotEmpty()) (resolutions.maxOrNull() ?: 1080) else 1080
            val bestSize = (sizeMap["best"] ?: sizeMap[maxRes.toString()] ?: sizeMap["${maxRes}p"] ?: sizeMap["1080p"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(maxRes, false, "best", duration)
            }
            val bestBytes = bytesMap["best"] ?: bytesMap[maxRes.toString()] ?: bytesMap["${maxRes}p"] ?: 0L
            formats.add(FormatOption("best", "Best Video (${maxRes}p Max)", "mp4", false, bestSize, maxRes, bestSize, bestBytes))

            // 4K UHD (2160p) - Only if video actually supports 4K!
            if (maxRes >= 2160 || is4k) {
                val sz4k = (sizeMap["4k"] ?: sizeMap["2160"] ?: sizeMap["2160p"] ?: "").ifBlank {
                    com.raoprime.downloadivo.model.estimateFormatSize(2160, false, "4k", duration)
                }
                val b4k = bytesMap["4k"] ?: bytesMap["2160"] ?: bytesMap["2160p"] ?: 0L
                formats.add(FormatOption("4k", "4K UHD (2160p)", "mp4", false, sz4k, 2160, sz4k, b4k))
            }

            // 2K QHD (1440p) - Only if video actually supports 1440p!
            if (maxRes >= 1440) {
                val sz1440 = (sizeMap["1440p"] ?: sizeMap["1440"] ?: sizeMap["2k"] ?: "").ifBlank {
                    com.raoprime.downloadivo.model.estimateFormatSize(1440, false, "1440p", duration)
                }
                val b1440 = bytesMap["1440p"] ?: bytesMap["1440"] ?: bytesMap["2k"] ?: 0L
                formats.add(FormatOption("1440p", "2K QHD (1440p)", "mp4", false, sz1440, 1440, sz1440, b1440))
            }

            // 1080p Full HD - Only if video actually supports 1080p!
            if (maxRes >= 1080) {
                val sz1080 = (sizeMap["1080p"] ?: sizeMap["1080"] ?: "").ifBlank {
                    com.raoprime.downloadivo.model.estimateFormatSize(1080, false, "1080p", duration)
                }
                val b1080 = bytesMap["1080p"] ?: bytesMap["1080"] ?: 0L
                formats.add(FormatOption("1080p", "1080p Full HD", "mp4", false, sz1080, 1080, sz1080, b1080))
            }

            // 720p HD - Only if video actually supports 720p!
            if (maxRes >= 720) {
                val sz720 = (sizeMap["720p"] ?: sizeMap["720"] ?: "").ifBlank {
                    com.raoprime.downloadivo.model.estimateFormatSize(720, false, "720p", duration)
                }
                val b720 = bytesMap["720p"] ?: bytesMap["720"] ?: 0L
                formats.add(FormatOption("720p", "720p HD", "mp4", false, sz720, 720, sz720, b720))
            }

            // 480p Standard
            if (maxRes >= 480) {
                val sz480 = (sizeMap["480p"] ?: sizeMap["480"] ?: "").ifBlank {
                    com.raoprime.downloadivo.model.estimateFormatSize(480, false, "480p", duration)
                }
                val b480 = bytesMap["480p"] ?: bytesMap["480"] ?: 0L
                formats.add(FormatOption("480p", "480p Standard", "mp4", false, sz480, 480, sz480, b480))
            }

            // 360p Medium
            val sz360 = (sizeMap["360p"] ?: sizeMap["360"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(360, false, "360p", duration)
            }
            val b360 = bytesMap["360p"] ?: bytesMap["360"] ?: 0L
            formats.add(FormatOption("360p", "360p Medium", "mp4", false, sz360, 360, sz360, b360))

            // 240p Low
            val sz240 = (sizeMap["240p"] ?: sizeMap["240"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(240, false, "240p", duration)
            }
            val b240 = bytesMap["240p"] ?: bytesMap["240"] ?: 0L
            formats.add(FormatOption("240p", "240p Low", "mp4", false, sz240, 240, sz240, b240))

            // 144p Data Saver
            val sz144 = (sizeMap["144p"] ?: sizeMap["144"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(144, false, "144p", duration)
            }
            val b144 = bytesMap["144p"] ?: bytesMap["144"] ?: 0L
            formats.add(FormatOption("144p", "144p Data Saver", "mp4", false, sz144, 144, sz144, b144))

            // ── Comprehensive Audio Formats ──
            // 320kbps MP3 (Ultra Quality)
            val sz320 = (sizeMap["320k"] ?: sizeMap["mp3"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(0, true, "320k", duration)
            }
            val b320 = bytesMap["320k"] ?: bytesMap["mp3"] ?: 0L
            formats.add(FormatOption("mp3", "320kbps MP3 (Ultra Quality)", "mp3", true, sz320, 0, sz320, b320))

            // 256kbps MP3 (High Quality)
            val sz256 = (sizeMap["256k"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(0, true, "256k", duration)
            }
            val b256 = bytesMap["256k"] ?: 0L
            formats.add(FormatOption("256k", "256kbps MP3 (High Quality)", "mp3", true, sz256, 0, sz256, b256))

            // 192kbps MP3 (Medium Quality)
            val sz192 = (sizeMap["192k"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(0, true, "192k", duration)
            }
            val b192 = bytesMap["192k"] ?: 0L
            formats.add(FormatOption("192k", "192kbps MP3 (Medium Quality)", "mp3", true, sz192, 0, sz192, b192))

            // 128kbps MP3 (Standard Quality)
            val sz128 = (sizeMap["128k"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(0, true, "128k", duration)
            }
            val b128 = bytesMap["128k"] ?: 0L
            formats.add(FormatOption("128k", "128kbps MP3 (Standard Quality)", "mp3", true, sz128, 0, sz128, b128))

            // Lossless AAC (M4A)
            val m4aSize = (sizeMap["m4a"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(0, true, "m4a", duration)
            }
            val m4aBytes = bytesMap["m4a"] ?: 0L
            formats.add(FormatOption("m4a", "Lossless AAC (M4A)", "m4a", true, m4aSize, 0, m4aSize, m4aBytes))

            // Studio WAV (Lossless Audio)
            val wavSize = (sizeMap["wav"] ?: "").ifBlank {
                com.raoprime.downloadivo.model.estimateFormatSize(0, true, "wav", duration)
            }
            val wavBytes = bytesMap["wav"] ?: 0L
            formats.add(FormatOption("wav", "Studio WAV (Lossless Audio)", "wav", true, wavSize, 0, wavSize, wavBytes))

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

    fun getStreamUrl(targetUrl: String, format: String = "best", audioLang: String? = null, online: Boolean = true): String {
        val cleanUrl = sanitizeUrl(targetUrl)
        val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
        val encodedFmt = URLEncoder.encode(format.trim(), "UTF-8")
        val endpoint = if (format.trim().lowercase() in listOf("mp3", "m4a", "audio", "wav", "320k", "256k", "192k", "128k")) "stream.mp3" else "stream.mp4"
        val audioQuery = if (!audioLang.isNullOrBlank()) "&audio_lang=" + URLEncoder.encode(audioLang.trim(), "UTF-8") else ""
        val onlineQuery = if (online) "&online=true" else ""
        val baseUrl = if (directStreamBaseUrl.isNotBlank()) directStreamBaseUrl else activeBaseUrl
        return "$baseUrl/$endpoint?url=$encodedUrl&format=$encodedFmt$audioQuery$onlineQuery"
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

    fun parseContentRangeStart(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val clean = contentRange.trim().removePrefix("bytes").trim()
        val dashIdx = clean.indexOf('-')
        if (dashIdx != -1) {
            return clean.substring(0, dashIdx).trim().toLongOrNull()
        }
        return null
    }

    fun parseContentRangeTotal(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        val slashIdx = contentRange.indexOf('/')
        if (slashIdx != -1) {
            val totalPart = contentRange.substring(slashIdx + 1).trim()
            if (totalPart != "*") {
                return totalPart.toLongOrNull()
            }
        }
        return null
    }

    suspend fun downloadStreamToFile(
        targetUrl: String,
        format: String,
        destinationFile: File,
        startTime: String? = null,
        endTime: String? = null,
        expectedTotalBytes: Long = 0L,
        onProgress: (percent: Int, speedStr: String, etaStr: String, currentBytes: Long, totalBytes: Long) -> Unit,
        isCancelled: () -> Boolean = { false },
        isPaused: () -> Boolean = { false },
        onTransferMetrics: ((StreamTransferMetrics) -> Unit)? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            syncLatestTunnelUrl()
        } catch (_: Exception) {}

        destinationFile.parentFile?.mkdirs()

        val cleanUrl = sanitizeUrl(targetUrl)
        val isDirectHttp = cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")
        val isDirectEndpoint = isDirectHttp && (
            cleanUrl.contains("/test/") ||
            cleanUrl.endsWith(".apk") ||
            cleanUrl.contains(".mp4") ||
            cleanUrl.contains(".mp3") ||
            cleanUrl.contains("/downloadivo.apk") ||
            cleanUrl.contains("/stream")
        )

        val bases = (listOfNotNull(
            directStreamBaseUrl.takeIf { it.isNotBlank() },
            activeBaseUrl
        ) + getCandidateBaseUrls()).filter { it.startsWith("http") }.distinct()

        val encodedUrl = URLEncoder.encode(cleanUrl, "UTF-8")
        val encodedFmt = URLEncoder.encode(format.trim(), "UTF-8")

        var lastError: Exception? = null
        val maxAttempts = 3
        var attempt = 0

        while (attempt < maxAttempts) {
            attempt++
            if (isCancelled()) {
                return@withContext Result.failure(CancellationException("Download cancelled by user"))
            }
            if (isPaused()) {
                return@withContext Result.failure(IOException("Download paused by user"))
            }

            val streamUrl = if (isDirectEndpoint) {
                cleanUrl
            } else {
                val currentBase = bases[(attempt - 1) % bases.size]
                var u = "$currentBase/stream?url=$encodedUrl&format=$encodedFmt"
                if (!startTime.isNullOrBlank() && !endTime.isNullOrBlank()) {
                    val encStart = URLEncoder.encode(startTime.trim(), "UTF-8")
                    val encEnd = URLEncoder.encode(endTime.trim(), "UTF-8")
                    u += "&start_time=$encStart&end_time=$encEnd"
                }
                u
            }

            val initialSavedBytes = if (destinationFile.exists()) destinationFile.length() else 0L
            var offsetToRequest = initialSavedBytes

            val reqBuilder = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Downloadivo-Android/${BuildConfig.VERSION_NAME}")
                .header("Accept-Encoding", "identity")

            if (offsetToRequest > 0L) {
                reqBuilder.header("Range", "bytes=$offsetToRequest-")
            }

            var raf: RandomAccessFile? = null
            var response: okhttp3.Response? = null

            try {
                response = streamClient.newCall(reqBuilder.build()).execute()

                // Check 416 Range Not Satisfiable (e.g. file size exceeded or changed)
                if (response.code == 416 && offsetToRequest > 0L) {
                    response.close()
                    destinationFile.delete()
                    destinationFile.createNewFile()
                    offsetToRequest = 0L
                    continue
                }

                if (!response.isSuccessful && response.code != 206) {
                    val code = response.code
                    response.close()
                    throw IOException("Server returned HTTP error code: $code")
                }

                val body = response.body ?: throw IOException("No response body received")
                val is206 = response.code == 206
                val contentRange = response.header("Content-Range")
                val parsedStart = contentRange?.let { parseContentRangeStart(it) }

                var isResumeSupported = false
                var wasCleanRestart = false

                if (offsetToRequest > 0L) {
                    if (is206 && (parsedStart == null || parsedStart == offsetToRequest)) {
                        // Server honored Range request from the exact requested offset
                        isResumeSupported = true
                        wasCleanRestart = false
                        raf = RandomAccessFile(destinationFile, "rw")
                        raf.seek(offsetToRequest)
                    } else {
                        // Server sent 200 OK or range mismatch -> Clean restart path
                        // Never append a full response to a partial file
                        isResumeSupported = false
                        wasCleanRestart = true
                        destinationFile.delete()
                        destinationFile.createNewFile()
                        offsetToRequest = 0L
                        raf = RandomAccessFile(destinationFile, "rw")
                        raf.setLength(0L)
                        raf.seek(0L)
                    }
                } else {
                    // Fresh initial download
                    isResumeSupported = true
                    wasCleanRestart = false
                    if (!destinationFile.exists()) destinationFile.createNewFile()
                    raf = RandomAccessFile(destinationFile, "rw")
                    raf.setLength(0L)
                    raf.seek(0L)
                }

                // Determine total expected length
                val parsedTotal = contentRange?.let { parseContentRangeTotal(it) }
                    ?: response.header("X-Expected-Total-Bytes")?.toLongOrNull()
                    ?: response.header("X-Total-Content-Length")?.toLongOrNull()
                    ?: if (is206) (offsetToRequest + body.contentLength()) else body.contentLength()
                val totalLength = if (parsedTotal > 0L) parsedTotal else (if (expectedTotalBytes > 0L) expectedTotalBytes else -1L)

                val inputStream = body.byteStream()
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var bytesThisSession = 0L
                var lastTime = System.currentTimeMillis()
                var bytesSinceLastTime = 0L

                val currentFileLength = destinationFile.length()
                val isInitialTotalReliable = totalLength > 0L && currentFileLength <= totalLength
                val initialPercent = if (isInitialTotalReliable) {
                    ((currentFileLength * 100) / totalLength).toInt().coerceIn(0, 99)
                } else {
                    -1
                }
                val initialReportedTotal = if (isInitialTotalReliable) totalLength else -1L
                onProgress(initialPercent, "", "", currentFileLength, initialReportedTotal)

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled()) {
                        raf.close()
                        response.close()
                        throw CancellationException("Download cancelled by user")
                    }
                    if (isPaused()) {
                        raf.close()
                        response.close()
                        throw IOException("Download paused by user")
                    }

                    raf.write(buffer, 0, bytesRead)
                    bytesThisSession += bytesRead
                    bytesSinceLastTime += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastTime
                    if (timeDiff >= 400) {
                        val currentLen = destinationFile.length()
                        val speedBytesPerSec = (bytesSinceLastTime * 1000.0) / timeDiff
                        val speedStr = formatSpeed(speedBytesPerSec)
                        // If total length is unknown, exceeded, or unreliable, show transferred amount without determinate percentage or ETA
                        val isTotalReliable = totalLength > 0L && currentLen <= totalLength
                        val percent = if (isTotalReliable) {
                            ((currentLen * 100) / totalLength).toInt().coerceIn(1, 99)
                        } else {
                            -1 // Indeterminate progress when total is unknown or overrun
                        }
                        val etaStr = if (isTotalReliable && speedBytesPerSec > 1024) {
                            val remainingBytes = totalLength - currentLen
                            val secondsLeft = (remainingBytes / speedBytesPerSec).toLong()
                            formatEta(secondsLeft)
                        } else {
                            ""
                        }
                        val reportedTotal = if (isTotalReliable) totalLength else -1L
                        onProgress(percent, speedStr, etaStr, currentLen, reportedTotal)
                        lastTime = now
                        bytesSinceLastTime = 0L
                    }
                }

                raf.fd.sync()
                raf.close()
                raf = null
                response.close()
                response = null

                val finalLength = destinationFile.length()
                if (totalLength > 0L && finalLength < totalLength) {
                    throw IOException("Premature EOF: received $finalLength of $totalLength bytes")
                }

                if (finalLength > 0L) {
                    onProgress(100, "Done", "0s", finalLength, finalLength)
                    onTransferMetrics?.invoke(
                        StreamTransferMetrics(
                            initialSavedBytes = initialSavedBytes,
                            offsetRequested = offsetToRequest,
                            bytesTransferredThisSession = bytesThisSession,
                            totalFileBytes = finalLength,
                            isResumeSupported = isResumeSupported,
                            wasCleanRestart = wasCleanRestart
                        )
                    )
                    return@withContext Result.success(finalLength)
                }
            } catch (e: Exception) {
                lastError = e
                try { raf?.close() } catch (_: Exception) {}
                try { response?.close() } catch (_: Exception) {}

                if (e is CancellationException) {
                    throw e
                }
                if (e.message?.contains("paused", ignoreCase = true) == true) {
                    throw e
                }

                if (attempt < maxAttempts) {
                    kotlinx.coroutines.delay(1000L)
                }
            }
        }

        Result.failure(lastError ?: IOException("Download failed after $maxAttempts attempts"))
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
        val tempFile = File.createTempFile("dld_stream_", ".tmp")
        try {
            val res = downloadStreamToFile(
                targetUrl = targetUrl,
                format = format,
                destinationFile = tempFile,
                startTime = startTime,
                endTime = endTime,
                expectedTotalBytes = expectedTotalBytes,
                onProgress = onProgress
            )
            if (res.isSuccess) {
                tempFile.inputStream().use { input ->
                    input.copyTo(outputStream)
                }
                outputStream.flush()
                Result.success(tempFile.length())
            } else {
                res
            }
        } finally {
            try { tempFile.delete() } catch (_: Exception) {}
        }
    }

    suspend fun checkAppUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            syncLatestTunnelUrl()
        } catch (_: Exception) {}
        val candidates = getCandidateBaseUrls()
        for (candidate in candidates) {
            try {
                val req = Request.Builder()
                    .url("$candidate/version")
                    .header("User-Agent", "Downloadivo-Android/6.0.0")
                    .get()
                    .build()
                val resp = apiClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    resp.close()
                    if (!body.isNullOrBlank()) {
                        val json = org.json.JSONObject(body)
                        return@withContext AppUpdateInfo(
                            versionCode = json.optInt("version_code", 0),
                            versionName = json.optString("version_name", "6.0.0"),
                            apkUrl = json.optString("apk_url", ""),
                            changelog = json.optString("changelog", "Performance improvements & bug fixes"),
                            forceUpdate = json.optBoolean("force_update", false)
                        )
                    }
                } else {
                    resp.close()
                }
            } catch (_: Exception) {}
        }
        null
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
