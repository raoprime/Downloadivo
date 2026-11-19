package com.raoprime.downloadivo

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.raoprime.downloadivo.ui.DownloadivoScreen
import com.raoprime.downloadivo.ui.theme.DownloadivoTheme

class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf<String?>(null)
    private var clipboardUrl by mutableStateOf<String?>(null)

    // Set true by BuiltInPlayerSheet while a video is playing, so Home press triggers PiP
    var pipEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ Notification Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Android <10: Request WRITE_EXTERNAL_STORAGE for MediaStore
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 102)
            }
        }

        handleShareIntent(intent)
        checkClipboardForUrl()

        setContent {
            DownloadivoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DownloadivoScreen(
                        sharedUrl = sharedUrl,
                        clipboardUrl = clipboardUrl,
                        onClearClipboard = { clipboardUrl = null },
                        onEnterPip = { enterPipMode() }
                    )
                }
            }
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto PiP when user presses Home while a video is playing
        if (pipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode()
        }
    }

    override fun onResume() {
        super.onResume()
        checkClipboardForUrl()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return

        val directUrl = intent.getStringExtra("shared_url")
        if (!directUrl.isNullOrBlank()) {
            sharedUrl = extractUrl(directUrl)
            return
        }

        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    val extracted = extractUrl(text)
                    if (extracted.isNotBlank()) {
                        sharedUrl = extracted
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.dataString ?: ""
                val extracted = extractUrl(data)
                if (extracted.isNotBlank()) {
                    sharedUrl = extracted
                }
            }
        }
    }

    private fun checkClipboardForUrl() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val text = item?.text?.toString() ?: ""
                val extracted = extractUrl(text)
                if (extracted.startsWith("http://") || extracted.startsWith("https://")) {
                    clipboardUrl = extracted
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractUrl(text: String): String {
        return com.raoprime.downloadivo.network.DownloadEngine.sanitizeUrl(text)
    }
}
