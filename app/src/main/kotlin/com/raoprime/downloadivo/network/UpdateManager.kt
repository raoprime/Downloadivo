package com.raoprime.downloadivo.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.raoprime.downloadivo.BuildConfig
import com.raoprime.downloadivo.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val updateInfo: AppUpdateInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Float, val currentBytes: Long, val totalBytes: Long) : UpdateState()
    data class ReadyToInstall(val apkFile: File, val updateInfo: AppUpdateInfo) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

object UpdateManager {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    suspend fun checkForUpdates(currentVersionCode: Int = BuildConfig.VERSION_CODE): AppUpdateInfo? = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        try {
            val updateInfo = DownloadEngine.checkAppUpdate()
            if (updateInfo != null && updateInfo.versionCode > currentVersionCode) {
                _updateState.value = UpdateState.Available(updateInfo)
                updateInfo
            } else {
                _updateState.value = UpdateState.UpToDate
                null
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "Failed to check for updates")
            null
        }
    }

    suspend fun downloadUpdate(
        context: Context,
        updateInfo: AppUpdateInfo
    ): File? = withContext(Dispatchers.IO) {
        try {
            var downloadUrl = updateInfo.apkUrl.trim()
            if (downloadUrl.isBlank() || !downloadUrl.startsWith("http")) {
                downloadUrl = "${DownloadEngine.activeBaseUrl}/downloadivo.apk"
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Downloadivo-Updater/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful) {
                _updateState.value = UpdateState.Error("HTTP error: ${response.code}")
                response.close()
                return@withContext null
            }

            val body = response.body
            if (body == null) {
                _updateState.value = UpdateState.Error("Empty download response from server")
                response.close()
                return@withContext null
            }

            val totalBytes = body.contentLength().coerceAtLeast(1L)
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()

            val apkFile = File(updateDir, "downloadivo_v${updateInfo.versionName}.apk")
            if (apkFile.exists()) apkFile.delete()

            var downloadedBytes = 0L
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(64 * 1024)
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                downloadedBytes += read
                val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                _updateState.value = UpdateState.Downloading(progress, downloadedBytes, totalBytes)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            response.close()

            _updateState.value = UpdateState.ReadyToInstall(apkFile, updateInfo)
            apkFile
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Download failed: ${e.message}")
            null
        }
    }

    fun promptInstall(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) {
                _updateState.value = UpdateState.Error("Installer file not found")
                return false
            }

            // On Android 8.0 (Oreo) and above, check if unknown app sources are allowed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Cannot start package installer: ${e.message}")
            false
        }
    }
}
