package com.raoprime.downloadivo

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleShareIntent(intent)

        setContent {
            DownloadivoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DownloadivoScreen(sharedUrl = sharedUrl)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val extracted = extractUrl(text)
            if (extracted.isNotBlank()) {
                sharedUrl = extracted
            }
        }
    }

    private fun extractUrl(text: String): String {
        val regex = Regex("""https?://[^\s]+""")
        return regex.find(text)?.value ?: text.trim()
    }
}
