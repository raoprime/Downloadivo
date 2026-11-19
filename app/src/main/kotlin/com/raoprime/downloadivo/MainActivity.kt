package com.raoprime.downloadivo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ Notification Permission Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
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
                        onClearClipboard = { clipboardUrl = null }
                    )
                }
            }
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
