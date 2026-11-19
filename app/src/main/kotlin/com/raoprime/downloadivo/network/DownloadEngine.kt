package com.raoprime.downloadivo.network

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

    // Downloadivo Edge Cloud Proxy
    const val BASE_URL = "https://downloadivo.vercel.app"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchMediaInfo(targetUrl: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().put("url", targetUrl).toString()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = jsonBody.toRequestBody(mediaType)

            // Try POST first, fallback to GET if needed
            val request = Request.Builder()
                .url("$BASE_URL/info")
                .header("User-Agent", "Downloadivo-Android/1.0")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Fallback to GET
                    val encoded = URLEncoder.encode(targetUrl, "UTF-8")
                    val getReq = Request.Builder()
                        .url("$BASE_URL/info?url=$encoded")
                        .header("User-Agent", "Downloadivo-Android/1.0")
                        .get()
                        .build()
                    val getResp = client.newCall(getReq).execute()
                    if (!getResp.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${getResp.code}: Failed to resolve media info"))
                    }
                    val body = getResp.body?.string() ?: throw Exception("Empty server response")
                    val json = JSONObject(body)
                    return@withContext Result.success(
                        MediaInfo(
                            title = json.optString("title", "Media Download"),
                            author = json.optString("uploader", json.optString("author", "Web Stream")),
                            duration = json.optString("duration", ""),
                            thumbnail = json.optString("thumbnail", ""),
                            url = targetUrl
                        )
                    )
                }

                val body = response.body?.string() ?: throw Exception("Empty server response")
                val json = JSONObject(body)

                val info = MediaInfo(
                    title = json.optString("title", "Media Download"),
                    author = json.optString("uploader", json.optString("author", "Web Stream")),
                    duration = json.optString("duration", ""),
                    thumbnail = json.optString("thumbnail", ""),
                    url = targetUrl
                )
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadStream(
        targetUrl: String,
        format: String,
        outputStream: OutputStream,
        onProgress: (percent: Int, speedStr: String, currentBytes: Long, totalBytes: Long) -> Unit
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = URLEncoder.encode(targetUrl, "UTF-8")
            val encodedFmt = URLEncoder.encode(format, "UTF-8")
            val request = Request.Builder()
                .url("$BASE_URL/stream?url=$encodedUrl&format=$encodedFmt")
                .header("User-Agent", "Downloadivo-Android/1.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Server returned error code: ${response.code}"))
                }

                val body = response.body ?: throw Exception("No response body available")
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

                    if (timeDiff >= 500) {
                        val speedBytesPerSec = (bytesSinceLastTime * 1000.0) / timeDiff
                        val speedStr = formatSpeed(speedBytesPerSec)
                        val percent = if (totalLength > 0) {
                            ((totalBytesRead * 100) / totalLength).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        onProgress(percent, speedStr, totalBytesRead, totalLength)

                        lastTime = now
                        bytesSinceLastTime = 0L
                    }
                }

                outputStream.flush()
                onProgress(100, "Done", totalBytesRead, totalBytesRead)
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
}
