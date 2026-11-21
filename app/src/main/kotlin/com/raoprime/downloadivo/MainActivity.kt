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
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.raoprime.downloadivo.ui.DownloadivoScreen
import com.raoprime.downloadivo.ui.onboarding.OnboardingScreen
import com.raoprime.downloadivo.ui.theme.DownloadivoTheme

class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf<String?>(null)
    private var sharedFormat by mutableStateOf<String?>(null)
    private var clipboardUrl by mutableStateOf<String?>(null)
    private var targetTabName by mutableStateOf<String?>(null)
    private var playbackUri by mutableStateOf<String?>(null)
    private var playbackTitle by mutableStateOf("")
    private var playbackIsAudio by mutableStateOf(false)
    private var playbackDuration by mutableStateOf("")

    // Set true by BuiltInPlayerSheet while a video is playing, so Home press triggers PiP
    private val _pipEnabled = mutableStateOf(false)
    var pipEnabled: Boolean
        get() = _pipEnabled.value
        set(value) {
            _pipEnabled.value = value
            updatePipParams(value)
        }
    var inPipMode by mutableStateOf(false)

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        @Suppress("DEPRECATION")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        inPipMode = isInPictureInPictureMode
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipMode = isInPictureInPictureMode
    }

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
        targetTabName = intent.getStringExtra("select_tab")

        // Configure Coil to support local video thumbnail frame extraction
        val imageLoader = ImageLoader.Builder(applicationContext)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        val prefs = getSharedPreferences("downloadivo_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("has_seen_onboarding", true).apply()

        setContent {
            DownloadivoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DownloadivoScreen(
                        sharedUrl = sharedUrl,
                        sharedFormat = sharedFormat,
                        clipboardUrl = clipboardUrl,
                        targetTabName = targetTabName,
                        initialPlaybackUri = playbackUri,
                        initialPlaybackTitle = playbackTitle,
                        initialPlaybackIsAudio = playbackIsAudio,
                        initialPlaybackDuration = playbackDuration,
                        onClearInitialPlayback = { playbackUri = null },
                        onClearTargetTab = { targetTabName = null },
                        onClearClipboard = { clipboardUrl = null },
                        onEnterPip = { enterPipMode() },
                        inPipMode = inPipMode,
                        onShowIntro = { }
                    )
                }
            }
        }
    }

    fun updatePipParams(enabled: Boolean = pipEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(enabled)
                    builder.setSeamlessResizeEnabled(true)
                }
                setPictureInPictureParams(builder.build())
            } catch (_: Exception) {}
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true)
                    builder.setSeamlessResizeEnabled(true)
                }
                enterPictureInPictureMode(builder.build())
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
        val tab = intent.getStringExtra("select_tab")
        if (!tab.isNullOrBlank()) {
            targetTabName = tab
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return

        val directUrl = intent.getStringExtra("shared_url")
        if (!directUrl.isNullOrBlank()) {
            sharedUrl = extractUrl(directUrl)
            sharedFormat = intent.getStringExtra("selected_format")
            return
        }

        val pUri = intent.getStringExtra("playback_uri")
        if (!pUri.isNullOrBlank()) {
            playbackUri = pUri
            playbackTitle = intent.getStringExtra("playback_title") ?: ""
            playbackIsAudio = intent.getBooleanExtra("playback_is_audio", false)
            playbackDuration = intent.getStringExtra("playback_duration") ?: ""
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
                val dataUri = intent.data
                val mime = intent.type ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/") ||
                    (dataUri != null && (dataUri.scheme == "content" || dataUri.scheme == "file"))) {
                    dataUri?.toString()?.let { uriStr ->
                        playbackUri = uriStr
                        playbackIsAudio = mime.startsWith("audio/")
                        playbackTitle = dataUri.lastPathSegment ?: "Media File"
                        playbackDuration = ""
                    }
                } else {
                    val data = intent.dataString ?: ""
                    val extracted = extractUrl(data)
                    if (extracted.isNotBlank()) {
                        sharedUrl = extracted
                    }
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
