@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.raoprime.downloadivo.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.raoprime.downloadivo.R
import com.raoprime.downloadivo.data.DownloadRepository
import com.raoprime.downloadivo.model.DownloadStatus
import com.raoprime.downloadivo.model.DownloadTask
import com.raoprime.downloadivo.model.FormatOption
import com.raoprime.downloadivo.model.MediaInfo
import com.raoprime.downloadivo.model.AppUpdateInfo
import com.raoprime.downloadivo.network.DownloadEngine
import com.raoprime.downloadivo.network.UpdateManager
import com.raoprime.downloadivo.network.UpdateState
import com.raoprime.downloadivo.service.DownloadService
import com.raoprime.downloadivo.ui.dialog.UpdateDialog
import com.raoprime.downloadivo.ui.player.BuiltInPlayerSheet
import com.raoprime.downloadivo.ui.player.YouTubeFloatingMiniplayer
import com.raoprime.downloadivo.ui.theme.*
import com.raoprime.downloadivo.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    HOME("Downloader"),
    QUEUE("Queue"),
    LIBRARY("Library"),
    SETTINGS("Settings")
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun DownloadivoScreen(
    sharedUrl: String? = null,
    sharedFormat: String? = null,
    clipboardUrl: String? = null,
    targetTabName: String? = null,
    initialPlaybackUri: String? = null,
    initialPlaybackTitle: String = "",
    initialPlaybackIsAudio: Boolean = false,
    initialPlaybackDuration: String = "",
    onClearInitialPlayback: () -> Unit = {},
    onClearTargetTab: () -> Unit = {},
    onClearClipboard: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    inPipMode: Boolean = false,
    onShowIntro: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentTab by remember { mutableStateOf(AppTab.HOME) }

    LaunchedEffect(targetTabName) {
        if (!targetTabName.isNullOrBlank()) {
            when (targetTabName.lowercase()) {
                "queue" -> currentTab = AppTab.QUEUE
                "library" -> currentTab = AppTab.LIBRARY
                "settings" -> currentTab = AppTab.SETTINGS
                "home" -> currentTab = AppTab.HOME
            }
            onClearTargetTab()
        }
    }

    var inputUrl by remember { mutableStateOf("") }
    var resolvedInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var selectedFormat by remember { mutableStateOf("best") }

    LaunchedEffect(sharedFormat) {
        if (!sharedFormat.isNullOrBlank()) {
            selectedFormat = sharedFormat
        }
    }
    var isResolving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCustomizeExpanded by remember { mutableStateOf(false) }

    // In-App Update System State
    var activeUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Automatic silent check for in-app updates on startup
    LaunchedEffect(Unit) {
        delay(1200) // Brief grace period for smooth startup
        val update = UpdateManager.checkForUpdates(BuildConfig.VERSION_CODE)
        if (update != null) {
            activeUpdateInfo = update
            showUpdateDialog = true
        }
    }

    // Built-in Player State (YouTube-Grade Player with Multi-Language Audio & Floating Miniplayer)
    var activePlaybackUri by remember { mutableStateOf<String?>(null) }
    var activePlaybackTitle by remember { mutableStateOf("") }
    var activePlaybackAuthor by remember { mutableStateOf("") }
    var activePlaybackThumbnail by remember { mutableStateOf("") }
    var activePlaybackIsAudio by remember { mutableStateOf(false) }
    var activePlaybackOriginalUrl by remember { mutableStateOf("") }
    var activePlaybackCurrentQuality by remember { mutableStateOf("best") }
    var activePlaybackQualities by remember { mutableStateOf<List<String>>(emptyList()) }
    var activePlaybackDuration by remember { mutableStateOf("") }
    var activePlaybackAudioTracks by remember { mutableStateOf<List<String>>(listOf("Original Audio", "Hindi (हिन्दी)", "English")) }
    var activePlaybackCurrentAudioTrack by remember { mutableStateOf("Original Audio") }
    var isPlayerMinimized by remember { mutableStateOf(false) }
    var isPlayerFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(initialPlaybackUri) {
        if (!initialPlaybackUri.isNullOrBlank()) {
            activePlaybackUri = initialPlaybackUri
            activePlaybackTitle = initialPlaybackTitle
            activePlaybackIsAudio = initialPlaybackIsAudio
            activePlaybackDuration = initialPlaybackDuration
            isPlayerMinimized = false
            onClearInitialPlayback()
        }
    }

    // Persistent Single ExoPlayer Instance across Miniplayer, Full Player & PiP
    val historyList by DownloadRepository.history.collectAsState()
    val exoPlayer = remember(context) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(35000)

        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory).setDataSourceFactory(defaultDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,  // minBufferMs (1.0s)
                30000, // maxBufferMs (30s)
                300,   // bufferForPlaybackMs (300ms - ultra-responsive start for short videos/reels)
                800    // bufferForPlaybackAfterRebufferMs (800ms)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            .build()
    }

    fun playMedia(
        uriStr: String,
        title: String,
        isAudio: Boolean,
        duration: String,
        thumbnail: String = "",
        author: String = "",
        originalUrl: String = "",
        qualities: List<String> = emptyList(),
        initialQuality: String = ""
    ) {
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            val uri = if (uriStr.startsWith("http://") || uriStr.startsWith("https://") || uriStr.startsWith("content://")) {
                Uri.parse(uriStr)
            } else {
                Uri.fromFile(File(uriStr))
            }
            val item = if (isAudio) {
                MediaItem.Builder().setUri(uri).setMimeType(MimeTypes.AUDIO_MPEG).build()
            } else {
                MediaItem.Builder().setUri(uri).build()
            }
            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (_: Exception) {}

        activePlaybackUri = uriStr
        activePlaybackOriginalUrl = originalUrl
        activePlaybackTitle = title
        activePlaybackThumbnail = thumbnail
        activePlaybackAuthor = author
        activePlaybackIsAudio = isAudio
        activePlaybackDuration = duration
        activePlaybackQualities = qualities
        activePlaybackCurrentQuality = initialQuality
        isPlayerMinimized = false
        isPlayerFullscreen = false
    }

    val playNextFromHistory: () -> Unit = {
        val list = historyList.filter { !it.filePath.isNullOrBlank() }
        if (list.isNotEmpty()) {
            val currentIndex = list.indexOfFirst {
                it.filePath == activePlaybackUri ||
                it.filePath == activePlaybackUri?.removePrefix("file://") ||
                (activePlaybackUri != null && it.filePath != null && activePlaybackUri!!.contains(it.filePath!!)) ||
                it.url == activePlaybackOriginalUrl
            }
            val nextIndex = if (currentIndex != -1 && currentIndex + 1 < list.size) currentIndex + 1 else if (currentIndex == -1) 0 else -1
            if (nextIndex in list.indices) {
                val nextTask = list[nextIndex]
                nextTask.filePath?.let { uriStr ->
                    playMedia(
                        uriStr = uriStr,
                        title = nextTask.title,
                        isAudio = nextTask.isAudio,
                        duration = nextTask.duration,
                        thumbnail = nextTask.thumbnail,
                        author = "",
                        originalUrl = nextTask.url
                    )
                }
            }
        }
    }

    val playPreviousFromHistory: () -> Unit = {
        val list = historyList.filter { !it.filePath.isNullOrBlank() }
        if (list.isNotEmpty()) {
            val currentIndex = list.indexOfFirst {
                it.filePath == activePlaybackUri ||
                it.filePath == activePlaybackUri?.removePrefix("file://") ||
                (activePlaybackUri != null && it.filePath != null && activePlaybackUri!!.contains(it.filePath!!)) ||
                it.url == activePlaybackOriginalUrl
            }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else if (currentIndex == -1) 0 else -1
            if (prevIndex in list.indices) {
                val prevTask = list[prevIndex]
                prevTask.filePath?.let { uriStr ->
                    playMedia(
                        uriStr = uriStr,
                        title = prevTask.title,
                        isAudio = prevTask.isAudio,
                        duration = prevTask.duration,
                        thumbnail = prevTask.thumbnail,
                        author = "",
                        originalUrl = prevTask.url
                    )
                }
            }
        }
    }

    var isExoPlaying by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer, historyList, activePlaybackUri) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isExoPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                isExoPlaying = exoPlayer.isPlaying
                if (state == Player.STATE_ENDED) {
                    playNextFromHistory()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    // Keep MainActivity pipEnabled in sync whenever active video playback is running
    LaunchedEffect(activePlaybackUri, activePlaybackIsAudio) {
        val act = context as? com.raoprime.downloadivo.MainActivity
        act?.pipEnabled = (activePlaybackUri != null && !activePlaybackIsAudio)
    }

    // Pure Video PiP Mode: When in PiP, display ONLY the video surface edge-to-edge
    if (inPipMode && activePlaybackUri != null) {
        BuiltInPlayerSheet(
            exoPlayer = exoPlayer,
            mediaUri = activePlaybackUri!!,
            title = activePlaybackTitle,
            author = activePlaybackAuthor,
            thumbnail = activePlaybackThumbnail,
            isAudio = activePlaybackIsAudio,
            duration = activePlaybackDuration,
            availableQualities = activePlaybackQualities,
            currentQuality = activePlaybackCurrentQuality,
            availableAudioTracks = activePlaybackAudioTracks,
            currentAudioTrack = activePlaybackCurrentAudioTrack,
            inPipMode = true,
            isFullscreen = true,
            onMinimize = { isPlayerMinimized = true },
            onDismiss = {
                try {
                    isPlayerFullscreen = false
                    isPlayerMinimized = false
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                } catch (_: Exception) {}
                activePlaybackUri = null
            }
        )
        return
    }

    // Back handler: minimize full player first, dismiss miniplayer if already minimized
    BackHandler(enabled = activePlaybackUri != null) {
        try {
            if (isPlayerFullscreen) {
                isPlayerFullscreen = false
            } else if (!isPlayerMinimized) {
                isPlayerMinimized = true
            } else {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                activePlaybackUri = null
            }
        } catch (_: Exception) {}
    }

    val activeTaskCount by DownloadService.activeTaskCount.collectAsState()
    val isAnyDownloading by DownloadService.isAnyDownloading.collectAsState()

    var showCompletionPill by remember { mutableStateOf(false) }
    var lastCompletedTitle by remember { mutableStateOf("") }
    var lastCompletedFilePath by remember { mutableStateOf<String?>(null) }
    var lastCompletedIsAudio by remember { mutableStateOf(false) }
    var lastCompletedDuration by remember { mutableStateOf("") }
    var lastCompletedThumbnail by remember { mutableStateOf("") }
    var lastCompletedAuthor by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        DownloadService.currentTask.collect { task ->
            if (task.status == DownloadStatus.SUCCESS && task.title.isNotBlank()) {
                lastCompletedTitle = task.title
                lastCompletedFilePath = task.filePath
                lastCompletedIsAudio = task.isAudio
                lastCompletedDuration = task.duration
                lastCompletedThumbnail = task.thumbnail
                showCompletionPill = true
                delay(5000)
                showCompletionPill = false
            }
        }
    }

    // Auto-fill when link is shared via Intent
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            inputUrl = sharedUrl
            isCustomizeExpanded = true
            isResolving = true
            errorMessage = null
            coroutineScope.launch {
                val res = DownloadEngine.fetchMediaInfo(sharedUrl)
                isResolving = false
                res.onSuccess {
                    resolvedInfo = it
                }.onFailure {
                    errorMessage = it.message ?: "Failed to resolve link"
                }
            }
        }
    }

    fun triggerDownload(
        url: String,
        title: String,
        format: String,
        thumbnail: String = "",
        duration: String = "",
        expectedBytes: Long = 0L,
        startTime: String? = null,
        endTime: String? = null
    ) {
        if (!isNetworkAvailable(context)) {
            Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }
        HapticUtil.heavyClick(context)
        DownloadService.startDownload(
            context = context,
            url = url,
            title = title,
            format = format,
            thumbnail = thumbnail,
            duration = duration,
            expectedBytes = expectedBytes,
            startTime = startTime,
            endTime = endTime
        )
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = "Downloading: $title",
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasDark,
        contentColor = TextCrisp,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SurfaceElevated,
                    contentColor = TextCrisp,
                    actionColor = TextCrisp,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            if ((activePlaybackUri == null || isPlayerMinimized) && ((currentTab != AppTab.HOME && currentTab != AppTab.LIBRARY) || (currentTab == AppTab.HOME && (resolvedInfo != null || isResolving)))) {
                TopAppBar(
                    title = {
                        when (currentTab) {
                            AppTab.HOME -> {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo_white),
                                    contentDescription = "Downloadivo",
                                    modifier = Modifier
                                        .height(28.dp)
                                        .padding(vertical = 2.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            AppTab.QUEUE -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.app_icon_white),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .padding(end = 8.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Text(
                                        text = "Queue",
                                        style = Typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCrisp
                                    )
                                }
                            }
                            AppTab.SETTINGS -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.app_icon_white),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .padding(end = 8.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Text(
                                        text = "Settings",
                                        style = Typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCrisp
                                    )
                                }
                            }
                            else -> {}
                        }
                    },
                    actions = {
                        if (currentTab == AppTab.HOME && (resolvedInfo != null || isResolving)) {
                            IconButton(
                                onClick = {
                                    inputUrl = ""
                                    resolvedInfo = null
                                    errorMessage = null
                                    isCustomizeExpanded = false
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CanvasDark
                    )
                )
            }
        },
        bottomBar = {
            if (activePlaybackUri == null || isPlayerMinimized) {
                Surface(
                    color = CanvasDark,
                    tonalElevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF222222)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(50.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            val iconColor = if (isSelected) TextCrisp else Color(0xFF8E8E8E)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (currentTab != tab) {
                                            HapticUtil.lightClick(context)
                                            currentTab = tab
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = when (tab) {
                                            AppTab.HOME -> Icons.Default.Download
                                            AppTab.QUEUE -> Icons.Default.FormatListBulleted
                                            AppTab.LIBRARY -> Icons.Default.VideoLibrary
                                            AppTab.SETTINGS -> Icons.Default.Tune
                                        },
                                        contentDescription = tab.name,
                                        tint = iconColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    if (tab == AppTab.QUEUE) {
                                        val activeCount = activeTaskCount
                                        if (activeCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = 6.dp, y = (-4).dp)
                                                    .size(15.dp)
                                                    .background(TextCrisp, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$activeCount",
                                                    color = CanvasDark,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 150)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 120))
                },
                label = "tab_navigation_simple_fade"
            ) { targetTab ->
                when (targetTab) {
                    AppTab.HOME -> HomeDownloaderTab(
                        inputUrl = inputUrl,
                        onUrlChange = { inputUrl = it; errorMessage = null },
                        clipboardUrl = clipboardUrl,
                        onClearClipboard = onClearClipboard,
                        resolvedInfo = resolvedInfo,
                        selectedFormat = selectedFormat,
                        onFormatSelect = { selectedFormat = it },
                        isResolving = isResolving,
                        errorMessage = errorMessage,
                        onStreamPlay = { streamUrl, originalUrl, title, isAudio, qualities, initialQuality, duration ->
                            playMedia(
                                uriStr = streamUrl,
                                title = title,
                                isAudio = isAudio,
                                duration = duration,
                                thumbnail = resolvedInfo?.thumbnail ?: "",
                                author = resolvedInfo?.author ?: "",
                                originalUrl = originalUrl,
                                qualities = qualities,
                                initialQuality = initialQuality
                            )
                        },
                        onInspect = { targetUrl ->
                            val urlToInspect = (targetUrl ?: inputUrl).trim()
                            if (urlToInspect.isNotBlank()) {
                                isCustomizeExpanded = true
                                isResolving = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val res = DownloadEngine.fetchMediaInfo(urlToInspect)
                                    isResolving = false
                                    res.onSuccess {
                                        resolvedInfo = it
                                    }.onFailure {
                                        errorMessage = it.message ?: "Failed to resolve link"
                                    }
                                }
                            }
                        },
                        onInstantDownload = { url, format ->
                            val defaultTitle = when {
                                url.contains("youtube") || url.contains("youtu.be") -> "YouTube Video"
                                url.contains("instagram.com") -> "Instagram Reel"
                                url.contains("tiktok.com") -> "TikTok Video"
                                url.contains("twitter.com") || url.contains("x.com") -> "X Video"
                                url.contains("spotify.com") -> "Spotify Audio"
                                else -> "Media Download"
                            }
                            val matchedBytes = resolvedInfo?.availableFormats?.find { it.id == format }?.sizeBytes ?: 0L
                            triggerDownload(
                                url = url,
                                title = resolvedInfo?.title ?: defaultTitle,
                                format = format,
                                thumbnail = resolvedInfo?.thumbnail ?: "",
                                duration = resolvedInfo?.duration ?: "",
                                expectedBytes = matchedBytes
                            )
                        },
                        onDownloadCustom = { info, format, startTime, endTime ->
                            val matchedBytes = info.availableFormats.find { it.id == format }?.sizeBytes ?: 0L
                            triggerDownload(
                                url = info.url,
                                title = info.title,
                                format = format,
                                thumbnail = info.thumbnail,
                                duration = info.duration,
                                expectedBytes = matchedBytes,
                                startTime = startTime,
                                endTime = endTime
                            )
                        },
                        onClear = {
                            inputUrl = ""
                            resolvedInfo = null
                            errorMessage = null
                            isCustomizeExpanded = false
                        }
                    )

                    AppTab.QUEUE -> {
                        val currentTask by DownloadService.currentTask.collectAsState()
                        val queuedTasks by DownloadService.queuedTasks.collectAsState()
                        QueueTab(
                            currentTask = currentTask,
                            queuedTasks = queuedTasks,
                            onCancelCurrent = {
                                val cancelIntent = Intent(context, DownloadService::class.java).apply {
                                    action = DownloadService.ACTION_CANCEL_DOWNLOAD
                                }
                                context.startService(cancelIntent)
                            },
                            onCancelAll = {
                                DownloadService.cancelAll(context)
                            },
                            onRemoveQueued = { taskId ->
                                DownloadService.removeQueuedTask(context, taskId)
                            },
                            onRetry = { task ->
                                triggerDownload(
                                    url = task.url,
                                    title = task.title,
                                    format = task.format,
                                    thumbnail = task.thumbnail,
                                    duration = task.duration,
                                    expectedBytes = task.totalBytes,
                                    startTime = task.startTime,
                                    endTime = task.endTime
                                )
                            },
                            onDismissError = {
                                val cancelIntent = Intent(context, DownloadService::class.java).apply {
                                    action = DownloadService.ACTION_CANCEL_DOWNLOAD
                                }
                                context.startService(cancelIntent)
                            },
                            onNavigateHome = { currentTab = AppTab.HOME }
                        )
                    }

                    AppTab.LIBRARY -> LibraryTab(
                        history = historyList,
                        hasMiniPlayer = (activePlaybackUri != null && isPlayerMinimized),
                        onPlay = { task ->
                            HapticUtil.mediumClick(context)
                            task.filePath?.let { uriStr ->
                                playMedia(
                                    uriStr = uriStr,
                                    title = task.title,
                                    isAudio = task.isAudio,
                                    duration = task.duration,
                                    thumbnail = task.thumbnail,
                                    author = "",
                                    originalUrl = task.url
                                )
                            }
                        },
                        onShare = { task ->
                            HapticUtil.lightClick(context)
                            task.filePath?.let { uriStr ->
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = if (task.isAudio) "audio/*" else "video/*"
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Could not share file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onOpenExternal = { task ->
                            HapticUtil.lightClick(context)
                            task.filePath?.let { uriStr ->
                                try {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(uriStr), if (task.isAudio) "audio/*" else "video/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(viewIntent, "Play with"))
                                } catch (_: Exception) {
                                    Toast.makeText(context, "No external media player installed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete = { task ->
                            HapticUtil.lightClick(context)
                            task.filePath?.let { uriStr ->
                                try {
                                    val fileUri = Uri.parse(uriStr)
                                    context.contentResolver.delete(fileUri, null, null)
                                } catch (_: Exception) {}
                            }
                            DownloadRepository.removeTask(task.id)
                        },
                        onNavigateHome = { currentTab = AppTab.HOME }
                    )

                    AppTab.SETTINGS -> SettingsTab(
                        onShowIntro = onShowIntro,
                        onShowUpdateDialog = { update ->
                            activeUpdateInfo = update
                            showUpdateDialog = true
                        }
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // FLOATING MINI-PLAYER & ACTIVE PROGRESS BAR
            // (Only visible on Home/Library/Settings - never duplicates on Queue tab)
            // ═══════════════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                // Active Download Floating Bar (Professional Studio Pill with Thumbnail & Live Speed)
                ActiveDownloadFloatingBar(
                    isVisible = isAnyDownloading && currentTab != AppTab.QUEUE,
                    onNavigateToQueue = {
                        HapticUtil.lightClick(context)
                        currentTab = AppTab.QUEUE
                    },
                    onCancelDownload = {
                        HapticUtil.lightClick(context)
                        val cancelIntent = Intent(context, DownloadService::class.java).apply {
                            action = DownloadService.ACTION_CANCEL_DOWNLOAD
                        }
                        context.startService(cancelIntent)
                    }
                )

                // Completion Pill (Auto-dismisses in 5s or tap X to close, only on non-queue tabs)
                CompletionFloatingPill(
                    isVisible = showCompletionPill && currentTab != AppTab.QUEUE,
                    title = lastCompletedTitle,
                    filePath = lastCompletedFilePath,
                    onPlay = {
                        HapticUtil.mediumClick(context)
                        lastCompletedFilePath?.let { filePath ->
                            playMedia(
                                uriStr = filePath,
                                title = lastCompletedTitle,
                                isAudio = lastCompletedIsAudio,
                                duration = lastCompletedDuration,
                                thumbnail = lastCompletedThumbnail,
                                author = lastCompletedAuthor
                            )
                        }
                    },
                    onDismiss = {
                        HapticUtil.lightClick(context)
                        showCompletionPill = false
                    }
                )

                // YouTube Floating Miniplayer (docked above bottom navigation bar when player is minimized!)
                androidx.compose.animation.AnimatedVisibility(
                    visible = activePlaybackUri != null && isPlayerMinimized,
                    enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) { it } + fadeIn(tween(160)),
                    exit = slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) { it } + fadeOut(tween(140))
                ) {
                    YouTubeFloatingMiniplayer(
                        title = activePlaybackTitle,
                        author = activePlaybackAuthor,
                        thumbnail = activePlaybackThumbnail,
                        isAudio = activePlaybackIsAudio,
                        isPlaying = isExoPlaying,
                        onTogglePlay = {
                            if (isExoPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        onExpand = { isPlayerMinimized = false },
                        onClose = {
                            try {
                                exoPlayer.stop()
                                exoPlayer.clearMediaItems()
                            } catch (_: Exception) {}
                            activePlaybackUri = null
                        }
                    )
                }
            }
        }

        // Fullscreen / Maximized YouTube-Grade Native Player
        androidx.compose.animation.AnimatedVisibility(
            visible = activePlaybackUri != null && !isPlayerMinimized,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) { it } + fadeOut(tween(160))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        isPlayerMinimized = true
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = if (isPlayerFullscreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                    }
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {}
                ) {
                    BuiltInPlayerSheet(
                        exoPlayer = exoPlayer,
                        mediaUri = activePlaybackUri!!,
                        title = activePlaybackTitle,
                        author = activePlaybackAuthor,
                        thumbnail = activePlaybackThumbnail,
                        isAudio = activePlaybackIsAudio,
                        duration = activePlaybackDuration,
                        availableQualities = activePlaybackQualities,
                        currentQuality = activePlaybackCurrentQuality,
                        availableAudioTracks = activePlaybackAudioTracks,
                        currentAudioTrack = activePlaybackCurrentAudioTrack,
                        inPipMode = false,
                        isFullscreen = isPlayerFullscreen,
                        onFullscreenChange = { isPlayerFullscreen = it },
                        onQualitySelected = { newQuality ->
                            activePlaybackCurrentQuality = newQuality
                            if (activePlaybackOriginalUrl.isNotBlank()) {
                                activePlaybackUri = DownloadEngine.getStreamUrl(
                                    activePlaybackOriginalUrl,
                                    newQuality,
                                    activePlaybackCurrentAudioTrack
                                )
                            }
                        },
                        onAudioTrackSelected = { newAudio ->
                            activePlaybackCurrentAudioTrack = newAudio
                            if (activePlaybackOriginalUrl.isNotBlank()) {
                                activePlaybackUri = DownloadEngine.getStreamUrl(
                                    activePlaybackOriginalUrl,
                                    activePlaybackCurrentQuality,
                                    newAudio
                                )
                            }
                        },
                        onEnterPip = onEnterPip,
                        onPlayNext = playNextFromHistory,
                        onPlayPrevious = playPreviousFromHistory,
                        onMinimize = { isPlayerMinimized = true },
                        onDismiss = {
                            try {
                                if (isPlayerFullscreen) {
                                    isPlayerFullscreen = false
                                }
                                exoPlayer.stop()
                                exoPlayer.clearMediaItems()
                            } catch (_: Exception) {}
                            activePlaybackUri = null
                        }
                    )
                }
            }
        }

        // In-App Update Dialog Modal
        if (showUpdateDialog && activeUpdateInfo != null) {
            UpdateDialog(
                updateInfo = activeUpdateInfo!!,
                onDismiss = { showUpdateDialog = false }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// FLOATING ISOLATED LEAF OVERLAYS (ISOLATED RECOMPOSITION SCOPES)
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ActiveDownloadFloatingBar(
    isVisible: Boolean,
    onNavigateToQueue: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) { it } + fadeOut(tween(140))
    ) {
        val currentTask by DownloadService.currentTask.collectAsState()
        if (currentTask.status == DownloadStatus.DOWNLOADING) {
            val animatedProgress by animateFloatAsState(
                targetValue = if (currentTask.progress >= 0) currentTask.progress / 100f else 0f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "pill_progress"
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                shadowElevation = 16.dp,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { onNavigateToQueue() }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Miniature Thumbnail with live indicator
                        Box(
                            modifier = Modifier
                                .size(46.dp, 30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentTask.thumbnail.isNotBlank()) {
                                AsyncImage(
                                    model = currentTask.thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    if (currentTask.isAudio) Icons.Default.MusicNote else Icons.Default.Download,
                                    contentDescription = null,
                                    tint = TextCrisp,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SurfaceHighlight,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = if (currentTask.isAudio) "MP3" else (if (currentTask.format.contains("1080")) "1080p" else (if (currentTask.format.contains("720")) "720p" else currentTask.format.uppercase())),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCrisp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = currentTask.title.ifBlank { "Downloading media..." },
                                    style = Typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextCrisp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (currentTask.progress >= 0) "${currentTask.progress}%" else formatByteSize(currentTask.bytesDownloaded),
                                        style = Typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCrisp
                                    )
                                    if (currentTask.speed.isNotBlank()) {
                                        Text(
                                            text = " • ${currentTask.speed}",
                                            style = Typography.bodySmall,
                                            color = TextCrisp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (currentTask.totalBytes > 0) {
                                        Text(
                                            text = " (${formatByteSize(currentTask.bytesDownloaded)} / ${formatByteSize(currentTask.totalBytes)})",
                                            style = Typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (currentTask.eta.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentTask.eta,
                                        style = Typography.labelSmall,
                                        color = TextTertiary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onCancelDownload,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextTertiary, modifier = Modifier.size(16.dp))
                        }
                    }

                    // High Precision Sleek Linear Progress Bar
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = TextCrisp,
                        trackColor = SurfaceHighlight
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionFloatingPill(
    isVisible: Boolean,
    title: String,
    filePath: String?,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) { it } + fadeIn(tween(160)),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) { it } + fadeOut(tween(140))
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
            shadowElevation = 14.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = TextCrisp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download Complete",
                        style = Typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextCrisp
                    )
                    Text(
                        text = title,
                        style = Typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (!filePath.isNullOrBlank()) {
                    Button(
                        onClick = onPlay,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextCrisp,
                            contentColor = CanvasDark
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CanvasDark, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", style = Typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// GEMINI LIQUID AMBIENT GLOW (SUBTLE LOW-OPACITY FLUID BACKGROUND)
// ═══════════════════════════════════════════════════════════════════════
@Composable
fun GeminiLiquidAmbientGlow(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_liquid_ambient")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val breathPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathPulse"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val rad1 = Math.toRadians(phase1.toDouble())
        val rad2 = Math.toRadians(phase2.toDouble())

        // 1. Celestial Deep Blue / Electric Cyan Liquid Blob
        val c1X = (w * 0.5f) + (w * 0.22f * Math.cos(rad1).toFloat())
        val c1Y = (h * 0.36f) + (h * 0.12f * Math.sin(rad1).toFloat())
        val r1 = (w * 0.65f) * breathPulse

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF2563EB).copy(alpha = 0.12f), // Gemini Electric Blue
                    Color(0xFF00E5FF).copy(alpha = 0.05f), // Fluid Cyan
                    Color.Transparent
                ),
                center = androidx.compose.ui.geometry.Offset(c1X, c1Y),
                radius = r1
            ),
            radius = r1,
            center = androidx.compose.ui.geometry.Offset(c1X, c1Y)
        )

        // 2. Cosmic Violet / Soft Orchid Liquid Blob
        val c2X = (w * 0.5f) + (w * 0.24f * Math.sin(rad2).toFloat())
        val c2Y = (h * 0.42f) + (h * 0.14f * Math.cos(rad2).toFloat())
        val r2 = (w * 0.70f) * (2.04f - breathPulse)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF7C3AED).copy(alpha = 0.10f), // Deep Violet
                    Color(0xFFEC4899).copy(alpha = 0.04f), // Soft Orchid
                    Color.Transparent
                ),
                center = androidx.compose.ui.geometry.Offset(c2X, c2Y),
                radius = r2
            ),
            radius = r2,
            center = androidx.compose.ui.geometry.Offset(c2X, c2Y)
        )

        // 3. Ambient Central Fluid Shimmer
        val c3X = w * 0.5f
        val c3Y = h * 0.38f
        val r3 = w * 0.55f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = androidx.compose.ui.geometry.Offset(c3X, c3Y),
                radius = r3
            ),
            radius = r3,
            center = androidx.compose.ui.geometry.Offset(c3X, c3Y)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 1: STUDIO HOME DOWNLOADER TAB (NO CLUTTER, PURE LUXURY)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun HomeDownloaderTab(
    inputUrl: String,
    onUrlChange: (String) -> Unit,
    clipboardUrl: String?,
    onClearClipboard: () -> Unit,
    resolvedInfo: MediaInfo?,
    selectedFormat: String,
    onFormatSelect: (String) -> Unit,
    isResolving: Boolean,
    errorMessage: String?,
    onInspect: (targetUrl: String?) -> Unit,
    onStreamPlay: (streamUrl: String, originalUrl: String, title: String, isAudio: Boolean, qualities: List<String>, initialQuality: String, duration: String) -> Unit,
    onInstantDownload: (url: String, format: String) -> Unit,
    onDownloadCustom: (info: MediaInfo, format: String, startTime: String?, endTime: String?) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var mediaMode by remember { mutableStateOf("video") }
    var isTrimming by remember { mutableStateOf(false) }
    var trimStart by remember { mutableStateOf("00:00") }
    var trimEnd by remember { mutableStateOf("01:00") }

    val detectedPlatform = remember(inputUrl) {
        val lower = inputUrl.lowercase()
        when {
            lower.contains("youtube") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("instagram.com") -> "Instagram"
            lower.contains("tiktok.com") -> "TikTok"
            lower.contains("twitter.com") || lower.contains("x.com") -> "X"
            lower.contains("spotify.com") -> "Spotify"
            lower.contains("reddit.com") -> "Reddit"
            lower.contains("facebook.com") || lower.contains("fb.watch") -> "Facebook"
            else -> null
        }
    }

    // Auto-inspect when a valid URL is entered or pasted into inputUrl
    LaunchedEffect(inputUrl) {
        val trimmed = inputUrl.trim()
        if ((trimmed.startsWith("http://") || trimmed.startsWith("https://")) && trimmed.contains(".") && trimmed.length > 10) {
            if (resolvedInfo == null && !isResolving) {
                kotlinx.coroutines.delay(650)
                if (inputUrl.trim() == trimmed && resolvedInfo == null && !isResolving) {
                    onInspect(trimmed)
                }
            }
        }
    }

    if (resolvedInfo == null && !isResolving) {
        val restingScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(restingScrollState),
            contentAlignment = Alignment.Center
        ) {
            GeminiLiquidAmbientGlow(modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ═══════════════════════════════════════════════════════════════
                // 1. BRAND EMBLEM & INPUT FIELD UNIFIED CONTROL GROUP
                // ═══════════════════════════════════════════════════════════════
                Image(
                    painter = painterResource(id = R.drawable.app_logo_white),
                    contentDescription = "Downloadivo",
                    modifier = Modifier
                        .height(36.dp)
                        .fillMaxWidth(0.62f),
                    contentScale = ContentScale.Fit
                )

                // 24–32 dp visible space between artwork and field (zero transparent padding)
                Spacer(modifier = Modifier.height(28.dp))

                // Contextual Clipboard Pill (floats above input if detected)
                if (!clipboardUrl.isNullOrBlank() && clipboardUrl != inputUrl) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = TextCrisp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Copied link detected",
                                    style = Typography.titleSmall,
                                    color = TextCrisp
                                )
                                Text(
                                    text = clipboardUrl,
                                    style = Typography.bodySmall,
                                    color = TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    HapticUtil.mediumClick(context)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onUrlChange(clipboardUrl)
                                    onClearClipboard()
                                    onInspect(clipboardUrl)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TextCrisp,
                                    contentColor = CanvasDark
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Paste & Inspect", style = Typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = onClearClipboard,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextTertiary)
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════════
                // REFINED LINK FIELD & PASTE BUTTON (SINGLE CONTROL GROUP)
                // ═══════════════════════════════════════════════════════════════
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Leading Link Icon or Platform Tag
                        if (detectedPlatform != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceHighlight,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = detectedPlatform,
                                    style = Typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = TextCrisp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        // Dedicated Editable Link Field (weight = 1f ensures zero overlap with button)
                        BasicTextField(
                            value = inputUrl,
                            onValueChange = onUrlChange,
                            modifier = Modifier.weight(1f),
                            textStyle = Typography.bodyLarge.copy(color = TextCrisp, fontSize = 15.sp),
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(TextCrisp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    if (inputUrl.startsWith("http")) onInspect(inputUrl)
                                }
                            ),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (inputUrl.isEmpty()) {
                                        Text(
                                            text = "Video or audio link",
                                            style = Typography.bodyLarge.copy(fontSize = 15.sp),
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.semantics {
                                                contentDescription = "Video or audio link"
                                            }
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Separate Width Action Slot (Paste Button or Clear / Submit)
                        if (inputUrl.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        onClear()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear link",
                                        tint = TextTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Surface(
                                    onClick = {
                                        HapticUtil.mediumClick(context)
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        if (inputUrl.startsWith("http")) onInspect(inputUrl)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = TextCrisp,
                                    modifier = Modifier
                                        .height(40.dp)
                                        .width(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Submit link",
                                            tint = CanvasDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Quieter Inner Button Border & Legible Vertically Aligned "Paste"
                            Surface(
                                onClick = {
                                    if (isResolving) return@Surface
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    val clean = DownloadEngine.sanitizeUrl(clip)
                                    if (clean.startsWith("http")) {
                                        HapticUtil.mediumClick(context)
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        onUrlChange(clean)
                                        onInspect(clean)
                                    } else {
                                        HapticUtil.lightClick(context)
                                        Toast.makeText(context, "No link in clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26262C)),
                                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                ) {
                                    if (isResolving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp,
                                            color = TextCrisp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = null,
                                            tint = TextCrisp,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isResolving) "Checking..." else "Paste",
                                        style = Typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        ),
                                        color = TextCrisp
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Action Buttons directly beneath center input when a link is pasted or typed
                AnimatedVisibility(
                    visible = inputUrl.isNotBlank() && (inputUrl.startsWith("http://") || inputUrl.startsWith("https://")),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    HapticUtil.mediumClick(context)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onInspect(inputUrl)
                                },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TextCrisp,
                                    contentColor = CanvasDark
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = CanvasDark, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Download Video",
                                    style = Typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CanvasDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = {
                                    HapticUtil.mediumClick(context)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    onInstantDownload(inputUrl, "mp3")
                                },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceElevated,
                                    contentColor = TextCrisp
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Audio MP3",
                                    style = Typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCrisp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            GeminiLiquidAmbientGlow(modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // ═══════════════════════════════════════════════════════════════
            // COMPACT TOP INPUT CAPSULE (ALWAYS VISIBLE & PINNED AT TOP)
            // ═══════════════════════════════════════════════════════════════

            // Compact input capsule when reviewing or loading
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = inputUrl,
                        onValueChange = onUrlChange,
                        modifier = Modifier.weight(1f),
                        textStyle = Typography.bodyMedium.copy(color = TextCrisp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (inputUrl.startsWith("http")) onInspect(inputUrl)
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (inputUrl.isEmpty()) {
                                Text(
                                    "Paste video or audio link...",
                                    style = Typography.bodyMedium,
                                    color = TextQuaternary
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (inputUrl.isNotBlank()) {
                        IconButton(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onClear()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextTertiary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = {
                                HapticUtil.mediumClick(context)
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (inputUrl.startsWith("http")) onInspect(inputUrl)
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextCrisp,
                                contentColor = CanvasDark
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Inspect", tint = CanvasDark, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Master Batch Queue Mode Card (Triggered when user pastes multiple links)
        val detectedUrls = remember(inputUrl) { DownloadEngine.extractAllUrls(inputUrl) }
        if (detectedUrls.size > 1 && resolvedInfo == null && !isResolving) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = TextCrisp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Batch Queue Mode",
                                style = Typography.titleSmall,
                                color = TextCrisp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceHighlight
                        ) {
                            Text(
                                text = "${detectedUrls.size} LINKS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCrisp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Queue all ${detectedUrls.size} items simultaneously into turbo background engine.",
                        style = Typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                HapticUtil.heavyClick(context)
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                detectedUrls.forEach { u ->
                                    onInstantDownload(u, "best")
                                }
                                onClear()
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextCrisp,
                                contentColor = CanvasDark
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = CanvasDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All Videos (${detectedUrls.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CanvasDark)
                        }

                        Button(
                            onClick = {
                                HapticUtil.heavyClick(context)
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                detectedUrls.forEach { u ->
                                    onInstantDownload(u, "mp3")
                                }
                                onClear()
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceDark,
                                contentColor = TextCrisp
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All MP3s (${detectedUrls.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextCrisp)
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // ERROR BANNER WITH RETRY ACTION
        // ═══════════════════════════════════════════════════════════════
        AnimatedVisibility(visible = errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StatusErrorBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = StatusError)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = Typography.bodyMedium,
                        color = TextCrisp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onInspect(null) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = TextCrisp)
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // RESOLVING STATE (SLEEK MINIMALIST LOADER)
        // ═══════════════════════════════════════════════════════════════
        AnimatedVisibility(visible = isResolving) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = TextCrisp,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Analyzing media streams...", style = Typography.bodyMedium, color = TextCrisp, fontWeight = FontWeight.Medium)
                    Text("Extracting 4K, 1080p formats & exact sizes", style = Typography.bodySmall, color = TextTertiary)
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // CROWN JEWEL: 16:9 CINEMATIC MEDIA STUDIO CARD
        // ═══════════════════════════════════════════════════════════════
        resolvedInfo?.let { info ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 16:9 Widescreen Cinematic Thumbnail Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(CanvasDark)
                    ) {
                        if (info.thumbnail.isNotBlank()) {
                            AsyncImage(
                                model = info.thumbnail,
                                contentDescription = "Video Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        // Top-Left Platform Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = info.extractor.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCrisp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        // Top-Right Dismiss "X" Button
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.8f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(32.dp)
                                .clickable { onClear() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close preview",
                                    tint = TextCrisp,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Center Play / Stream Circle Button
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .border(1.dp, BorderSubtle, CircleShape)
                                .clickable {
                                    HapticUtil.mediumClick(context)
                                    val streamUrl = DownloadEngine.getStreamUrl(info.url, selectedFormat)
                                    val qualities = info.availableFormats.filter { !it.isAudio }.map { it.id }
                                    onStreamPlay(streamUrl, info.url, info.title, mediaMode == "audio", qualities, selectedFormat, info.duration)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Stream Now",
                                tint = TextCrisp,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Bottom-Right Duration Badge
                        if (info.duration.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = info.duration,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Content & Action Hierarchy
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Title & Channel Header
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = info.title.ifBlank { "Media Stream" },
                                style = Typography.titleMedium,
                                color = TextCrisp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (info.author.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = TextTertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = info.author,
                                        style = Typography.bodyMedium,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = BorderHairline)

                        // Mode Segmented Bar: Video vs Audio
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CanvasDark)
                                .border(1.dp, BorderHairline, RoundedCornerShape(12.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = {
                                    HapticUtil.lightClick(context)
                                    mediaMode = "video"
                                    onFormatSelect("best")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(9.dp),
                                color = if (mediaMode == "video") TextCrisp else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = if (mediaMode == "video") CanvasDark else TextSecondary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Video",
                                        style = Typography.labelMedium,
                                        color = if (mediaMode == "video") CanvasDark else TextSecondary,
                                        fontWeight = if (mediaMode == "video") FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    HapticUtil.lightClick(context)
                                    mediaMode = "audio"
                                    onFormatSelect("mp3")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(9.dp),
                                color = if (mediaMode == "audio") TextCrisp else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (mediaMode == "audio") CanvasDark else TextSecondary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Audio",
                                        style = Typography.labelMedium,
                                        color = if (mediaMode == "audio") CanvasDark else TextSecondary,
                                        fontWeight = if (mediaMode == "audio") FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Responsive 2-Column Format Matrix
                        val formatsToShow = if (mediaMode == "video") {
                            info.availableFormats.filter { !it.isAudio }
                        } else {
                            info.availableFormats.filter { it.isAudio }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            formatsToShow.chunked(2).forEach { rowFormats ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowFormats.forEach { fmt ->
                                        val isSelected = selectedFormat == fmt.id
                                        Surface(
                                            onClick = {
                                                HapticUtil.lightClick(context)
                                                onFormatSelect(fmt.id)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = 54.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) TextCrisp else SurfaceElevated,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) TextCrisp else BorderHairline
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    val cleanLabel = fmt.label
                                                        .replace(" Video", "")
                                                        .replace(" Quality", "")
                                                        .replace(" Audio", "")
                                                    Text(
                                                        text = cleanLabel,
                                                        style = Typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) CanvasDark else TextCrisp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = fmt.getEffectiveSize(info.duration),
                                                        style = Typography.bodySmall,
                                                        color = if (isSelected) CanvasDark.copy(alpha = 0.8f) else TextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = CanvasDark,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // Filler if odd number
                                    if (rowFormats.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Precision Clip Trimmer
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CanvasDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCut,
                                            contentDescription = null,
                                            tint = TextCrisp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Clip Trimmer",
                                            style = Typography.titleSmall,
                                            color = TextCrisp
                                        )
                                    }
                                    Switch(
                                        checked = isTrimming,
                                        onCheckedChange = {
                                            HapticUtil.lightClick(context)
                                            isTrimming = it
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = CanvasDark,
                                            checkedTrackColor = TextCrisp
                                        )
                                    )
                                }
                                if (isTrimming) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = trimStart,
                                            onValueChange = { trimStart = it },
                                            placeholder = { Text("Start: 00:00", fontSize = 11.sp, color = TextQuaternary) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = TextCrisp,
                                                unfocusedBorderColor = BorderHairline,
                                                focusedTextColor = TextCrisp,
                                                unfocusedTextColor = TextCrisp
                                            )
                                        )
                                        OutlinedTextField(
                                            value = trimEnd,
                                            onValueChange = { trimEnd = it },
                                            placeholder = { Text("End: 01:00", fontSize = 11.sp, color = TextQuaternary) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = TextCrisp,
                                                unfocusedBorderColor = BorderHairline,
                                                focusedTextColor = TextCrisp,
                                                unfocusedTextColor = TextCrisp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Dual Action: Stream Ad-Free vs Download Offline
                        val selectedFmt = info.availableFormats.find { it.id == selectedFormat }
                        val selectedLabel = selectedFmt?.label?.replace(" Video", "")?.replace(" Audio", "") ?: selectedFormat.uppercase()
                        val effectiveSize = selectedFmt?.getEffectiveSize(info.duration) ?: ""
                        val sizeText = if (effectiveSize.isNotBlank()) " • $effectiveSize" else ""

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Instant Stream Button (Play in Full HD/4K Ad-Free without downloading)
                            Button(
                                onClick = {
                                    HapticUtil.mediumClick(context)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    val streamUrl = DownloadEngine.getStreamUrl(info.url, selectedFormat)
                                    val qualities = info.availableFormats.filter { !it.isAudio }.map { it.id }
                                    onStreamPlay(streamUrl, info.url, info.title, mediaMode == "audio", qualities, selectedFormat, info.duration)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceElevated,
                                    contentColor = TextCrisp
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(19.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Stream Ad-Free",
                                    style = Typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCrisp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Master Download Button
                            Button(
                                onClick = {
                                    HapticUtil.heavyClick(context)
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    val start = if (isTrimming) trimStart.ifBlank { null } else null
                                    val end = if (isTrimming) trimEnd.ifBlank { null } else null
                                    onDownloadCustom(info, selectedFormat, start, end)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TextCrisp,
                                    contentColor = CanvasDark
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = CanvasDark, modifier = Modifier.size(19.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isTrimming) "Save Clip" else "Download",
                                    style = Typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CanvasDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = if (isTrimming) "Trim mode active: $trimStart - $trimEnd" else "Selected: $selectedLabel$sizeText",
                            style = Typography.bodySmall,
                            color = TextTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Up Next / Related Videos Single Feed
                        if (info.relatedFeed.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "UP NEXT & RELATED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSubtle,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "${info.relatedFeed.size} videos",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                info.relatedFeed.forEach { feedItem ->
                                    Surface(
                                        onClick = {
                                            HapticUtil.lightClick(context)
                                            onUrlChange(feedItem.url)
                                            onInspect(feedItem.url)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = SurfaceCardDark,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Thumbnail
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 110.dp, height = 62.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF141418))
                                            ) {
                                                if (feedItem.thumbnail.isNotBlank()) {
                                                    AsyncImage(
                                                        model = feedItem.thumbnail,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                // Duration tag
                                                if (feedItem.duration.isNotBlank()) {
                                                    Surface(
                                                        color = Color.Black.copy(alpha = 0.75f),
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(4.dp)
                                                    ) {
                                                        Text(
                                                            text = feedItem.duration,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextCrisp,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                // Mini play overlay icon
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .align(Alignment.Center)
                                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = TextCrisp,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = feedItem.title,
                                                    style = Typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = feedItem.uploader,
                                                    style = Typography.bodySmall,
                                                    color = TextMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(130.dp))
        }
    }
}
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 2: POLISHED MONOCHROME DOWNLOAD QUEUE
// ═══════════════════════════════════════════════════════════════════════

private fun sanitizeQueueErrorMessage(raw: String?): String {
    if (raw.isNullOrBlank()) return "Download interrupted"
    val lower = raw.lowercase()
    return when {
        "timeout" in lower || "timed out" in lower -> "Connection timed out"
        "network" in lower || "unable to resolve host" in lower || "no address associated" in lower -> "Network connection lost"
        "space" in lower || "enospc" in lower -> "Insufficient storage space"
        "403" in lower || "forbidden" in lower -> "Access forbidden by server"
        "404" in lower || "not found" in lower -> "Media file not found"
        "mediastore" in lower || "destination" in lower -> "Could not save to storage"
        "aborted" in lower || "cancelled" in lower -> "Download cancelled"
        raw.length > 55 -> raw.take(52) + "..."
        else -> raw
    }
}

@Composable
private fun QueueTab(
    currentTask: DownloadTask,
    queuedTasks: List<DownloadTask>,
    onCancelCurrent: () -> Unit,
    onCancelAll: () -> Unit = {},
    onRemoveQueued: (String) -> Unit,
    onRetry: (DownloadTask) -> Unit = {},
    onDismissError: () -> Unit = {},
    onNavigateHome: () -> Unit
) {
    val context = LocalContext.current
    var isInitialSyncComplete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Prevents flashing "Queue is empty" during initial StateFlow synchronization
        kotlinx.coroutines.delay(80)
        isInitialSyncComplete = true
    }

    // Active or actionable tasks: downloading, resolving, or failed current task + queued tasks
    val isCurrentActionable = currentTask.id.isNotBlank() && (
        currentTask.status == DownloadStatus.DOWNLOADING ||
        currentTask.status == DownloadStatus.RESOLVING ||
        currentTask.status == DownloadStatus.ERROR
    )

    val activeJobs = remember(currentTask, queuedTasks, isCurrentActionable) {
        val list = mutableListOf<DownloadTask>()
        if (isCurrentActionable) {
            list.add(currentTask)
        }
        list.addAll(queuedTasks)
        list
    }

    // Rapid action debounce to avoid multiple triggers on fast taps
    var pendingActionTaskId by remember { mutableStateOf<String?>(null) }
    var isNewDownloadTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(currentTask.id, queuedTasks.size) {
        pendingActionTaskId = null
    }

    if (activeJobs.isEmpty()) {
        if (!isInitialSyncComplete) {
            // Neutral empty canvas during initial frame load
            Box(modifier = Modifier.fillMaxSize())
        } else {
            // ── COMPACT MONOCHROME EMPTY STATE (CENTERED IN USABLE SPACE) ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. List glyph inside circular charcoal surface
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = null,
                            tint = TextCrisp,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 2. Title "Queue is empty"
                    Text(
                        text = "Queue is empty",
                        style = Typography.titleMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = TextCrisp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 3. Supporting line "Active and waiting downloads appear here."
                    Text(
                        text = "Active and waiting downloads appear here.",
                        style = Typography.bodyMedium.copy(fontSize = 14.sp),
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // 4. White "New download" button with black plus and label
                    Button(
                        onClick = {
                            if (!isNewDownloadTriggered) {
                                isNewDownloadTriggered = true
                                HapticUtil.mediumClick(context)
                                onNavigateHome()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextCrisp,
                            contentColor = CanvasDark
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = CanvasDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New download",
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CanvasDark
                        )
                    }
                }
            }
        }
    } else {
        // ── POPULATED MONOCHROME QUEUE LIST ──
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Subtle Queue Header
            item(key = "queue_header_section") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp, start = 2.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeJobs.size} ${if (activeJobs.size == 1) "download" else "downloads"}",
                        style = Typography.bodySmall.copy(fontSize = 13.sp),
                        color = TextSecondary
                    )

                    if (activeJobs.size > 1 || currentTask.status == DownloadStatus.DOWNLOADING) {
                        TextButton(
                            onClick = {
                                HapticUtil.mediumClick(context)
                                onCancelAll()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Cancel all",
                                style = Typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextTertiary
                            )
                        }
                    }
                }
            }

            // Populated Jobs with Stable IDs
            itemsIndexed(
                items = activeJobs,
                key = { _, task -> task.id.ifBlank { "task_${task.url.hashCode()}" } }
            ) { index, task ->
                val queuePosition = if (isCurrentActionable) index else (index + 1)

                QueueJobRow(
                    task = task,
                    queuePosition = queuePosition,
                    onCancel = {
                        if (pendingActionTaskId != task.id) {
                            pendingActionTaskId = task.id
                            HapticUtil.lightClick(context)
                            if (task.id == currentTask.id) {
                                onCancelCurrent()
                            } else {
                                onRemoveQueued(task.id)
                            }
                        }
                    },
                    onRetry = {
                        if (pendingActionTaskId != task.id) {
                            pendingActionTaskId = task.id
                            HapticUtil.mediumClick(context)
                            onRetry(task)
                        }
                    },
                    onDismiss = {
                        if (pendingActionTaskId != task.id) {
                            pendingActionTaskId = task.id
                            HapticUtil.lightClick(context)
                            onDismissError()
                        }
                    }
                )
            }

            // Bottom Spacing for Bottom Bar clearance
            item(key = "queue_bottom_spacer") {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }
    }
}

@Composable
private fun QueueJobRow(
    task: DownloadTask,
    queuePosition: Int,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Artwork Thumbnail (16:9 ratio, 76dp x 44dp)
            Box(
                modifier = Modifier
                    .size(76.dp, 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (task.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = task.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (task.isAudio) Icons.Default.MusicNote else Icons.Default.Download,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Format or duration pill badge overlay
                val badgeText = if (task.isAudio) "MP3" else if (task.format.contains("1080")) "1080p" else if (task.format.contains("720")) "720p" else if (task.duration.isNotBlank()) task.duration else ""
                if (badgeText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextCrisp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Info Column (weight = 1f ensures fixed bounds without shifting adjacent controls)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Media Title (strongest emphasis, max 2 lines)
                Text(
                    text = task.title.ifBlank { "Media Download" },
                    style = Typography.titleSmall.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = TextCrisp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Real state in plain language
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (task.progress >= 0 && task.totalBytes > 0) {
                                    "${task.progress}% • ${formatByteSize(task.bytesDownloaded)} / ${formatByteSize(task.totalBytes)}"
                                } else if (task.bytesDownloaded > 0) {
                                    "${formatByteSize(task.bytesDownloaded)} transferred"
                                } else {
                                    "Downloading..."
                                },
                                style = Typography.bodySmall.copy(fontSize = 12.sp),
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (task.speed.isNotBlank()) {
                                Text(
                                    text = " • ${task.speed}",
                                    style = Typography.bodySmall.copy(fontSize = 12.sp),
                                    color = TextTertiary,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Progress indicator (steady 4dp height)
                        val animatedProgress by animateFloatAsState(
                            targetValue = if (task.progress >= 0) task.progress / 100f else 0f,
                            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                            label = "queue_progress"
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = TextCrisp,
                            trackColor = SurfaceHighlight
                        )
                    }

                    DownloadStatus.RESOLVING -> {
                        Text(
                            text = "Preparing download...",
                            style = Typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = TextCrisp,
                            trackColor = SurfaceHighlight
                        )
                    }

                    DownloadStatus.ERROR -> {
                        Text(
                            text = sanitizeQueueErrorMessage(task.error),
                            style = Typography.bodySmall.copy(fontSize = 12.sp),
                            color = StatusError,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DownloadStatus.QUEUED -> {
                        val subText = buildString {
                            append("Waiting")
                            if (queuePosition > 0) {
                                append(" • #$queuePosition in queue")
                            }
                            if (task.totalBytes > 0) {
                                append(" • ~${formatByteSize(task.totalBytes)}")
                            }
                        }
                        Text(
                            text = subText,
                            style = Typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    else -> {
                        Text(
                            text = "Waiting",
                            style = Typography.bodySmall.copy(fontSize = 12.sp),
                            color = TextTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Action Slot
            when (task.status) {
                DownloadStatus.ERROR -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            onClick = {
                                HapticUtil.mediumClick(context)
                                onRetry()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                            modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry download ${task.title}",
                                    tint = TextCrisp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Retry",
                                    style = Typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextCrisp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                HapticUtil.lightClick(context)
                                onDismiss()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error for ${task.title}",
                                tint = TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                DownloadStatus.DOWNLOADING, DownloadStatus.RESOLVING -> {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel download ${task.title}",
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DownloadStatus.QUEUED -> {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove ${task.title} from queue",
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                else -> {}
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════
// TAB 3: YOUTUBE-GRADE STUDIO OFFLINE VAULT & LIBRARY
// ═══════════════════════════════════════════════════════════════════════
private enum class LibrarySort(val label: String, val icon: ImageVector) {
    NEWEST("Newest First", Icons.Default.Schedule),
    OLDEST("Oldest First", Icons.Default.History),
    SIZE("Largest Size", Icons.Default.Storage),
    NAME("Name (A to Z)", Icons.Default.SortByAlpha)
}

private fun formatQualityBadge(task: DownloadTask): String {
    if (task.isAudio) {
        val f = task.format.lowercase()
        return when {
            f.contains("320") || f == "mp3" -> "MP3 · 320k"
            f.contains("m4a") -> "AAC · Lossless"
            f.contains("wav") -> "WAV · Hi-Res"
            else -> "MP3"
        }
    }
    val f = task.format.lowercase()
    val t = task.title.lowercase()
    return when {
        f.contains("4k") || f.contains("2160") || t.contains("4k") || t.contains("2160p") -> "4K UHD"
        f.contains("1440") || f.contains("2k") || t.contains("1440p") -> "2K QHD"
        f.contains("1080") || f == "best" || t.contains("1080p") -> "1080p FHD"
        f.contains("720") || t.contains("720p") -> "720p HD"
        f.contains("480") || t.contains("480p") -> "480p"
        f.contains("360") || t.contains("360p") -> "360p"
        else -> "MP4"
    }
}

private fun formatTimestampRelative(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDay = 24 * 60 * 60 * 1000L
    return when {
        diff < 60 * 1000L -> "Just now"
        diff < 60 * 60 * 1000L -> "${diff / (60 * 1000L)}m ago"
        diff < oneDay -> "Today"
        diff < 2 * oneDay -> "Yesterday"
        else -> {
            val sdf = SimpleDateFormat("dd MMM", Locale.US)
            sdf.format(Date(timestamp))
        }
    }
}

private fun formatSimpleBadge(task: DownloadTask): String {
    if (task.isAudio) {
        val f = task.format.lowercase()
        return when {
            f.contains("m4a") -> "M4A"
            f.contains("wav") -> "WAV"
            f.contains("flac") -> "FLAC"
            f.contains("opus") -> "OPUS"
            else -> "MP3"
        }
    }
    val f = task.format.lowercase()
    val t = task.title.lowercase()
    return when {
        f.contains("4k") || f.contains("2160") || t.contains("4k") || t.contains("2160p") -> "4K"
        f.contains("1440") || f.contains("2k") || t.contains("1440p") -> "2K"
        f.contains("1080") || t.contains("1080p") -> "1080p"
        f.contains("720") || t.contains("720p") -> "720p"
        f.contains("480") || t.contains("480p") -> "480p"
        f.contains("360") || t.contains("360p") -> "360p"
        else -> "MP4"
    }
}

// ── APPROVED GRAPHITE SEGMENTED SELECTOR ──
@Composable
private fun GraphiteSegmentedSelector(
    selectedFilter: String,
    allCount: Int,
    videoCount: Int,
    audioCount: Int,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val segments = listOf(
        Triple("All", "All", allCount),
        Triple("Videos", "Videos", videoCount),
        Triple("Audio", "Audio", audioCount)
    )
    val selectedIndex = when (selectedFilter) {
        "Videos" -> 1
        "Audio" -> 2
        else -> 0
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF1E1E22))
            .border(1.dp, Color(0xFF28282E), RoundedCornerShape(13.dp))
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / segments.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "selector_indicator"
        )

        // ── SATIN-SILVER SLIDING SELECTION SURFACE ──
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEDEDF2), // Fine top highlight
                            Color(0xFFD4D4DC), // Satin silver body
                            Color(0xFFBCBCC6)  // Base silver tone
                        )
                    )
                )
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp)
                )
        )

        // ── SEGMENT LABELS & INACTIVE DIVIDERS ──
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEachIndexed { index, (key, label, count) ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (selectedFilter != key) {
                                HapticUtil.lightClick(context)
                                onFilterSelected(key)
                            }
                        }
                        .semantics {
                            role = Role.Tab
                            this.selected = isSelected
                            contentDescription = "$label, $count items, ${if (isSelected) "selected" else "not selected"}"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = label,
                            style = Typography.titleSmall.copy(
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = if (isSelected) Color(0xFF111114) else Color(0xFFA2A2AA)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "$count",
                            style = Typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) Color(0xFF1E1E24) else Color(0xFF787882)
                        )
                    }

                    // Subtle hairline divider between adjacent inactive segments
                    if (index < segments.size - 1 && index != selectedIndex && index + 1 != selectedIndex) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(1.dp)
                                .height(16.dp)
                                .background(Color(0xFF33333A))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    history: List<DownloadTask>,
    hasMiniPlayer: Boolean = false,
    onPlay: (DownloadTask) -> Unit,
    onShare: (DownloadTask) -> Unit,
    onOpenExternal: (DownloadTask) -> Unit,
    onDelete: (DownloadTask) -> Unit,
    onNavigateHome: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterType by rememberSaveable { mutableStateOf("All") }
    var sortOrder by rememberSaveable { mutableStateOf(LibrarySort.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<DownloadTask?>(null) }
    var selectedTaskForDetails by remember { mutableStateOf<DownloadTask?>(null) }

    // Strict Library Invariant: ONLY fully completed, on-disk media appears in Library
    val completedHistory = remember(history) {
        history.filter { it.status == DownloadStatus.SUCCESS && !it.filePath.isNullOrBlank() }
    }

    val totalBytes = remember(completedHistory) { completedHistory.sumOf { it.totalBytes } }
    val videoCount = remember(completedHistory) { completedHistory.count { !it.isAudio } }
    val audioCount = remember(completedHistory) { completedHistory.count { it.isAudio } }

    val filteredHistory = remember(completedHistory, searchQuery, filterType, sortOrder) {
        val filtered = completedHistory.filter { task ->
            val matchesSearch = searchQuery.isBlank() ||
                task.title.contains(searchQuery, ignoreCase = true) ||
                task.format.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterType) {
                "Videos" -> !task.isAudio
                "Audio" -> task.isAudio
                else -> true
            }
            matchesSearch && matchesFilter
        }
        when (sortOrder) {
            LibrarySort.NEWEST -> filtered.sortedByDescending { it.timestamp }
            LibrarySort.OLDEST -> filtered.sortedBy { it.timestamp }
            LibrarySort.SIZE -> filtered.sortedByDescending { it.totalBytes }
            LibrarySort.NAME -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    // Clear History Safety Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear download history?",
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextCrisp
                )
            },
            text = {
                Text(
                    text = "This removes your download records from this list. Your actual downloaded video and audio files remain 100% safe in your device storage.",
                    style = Typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        HapticUtil.mediumClick(context)
                        showClearConfirmDialog = false
                        DownloadRepository.clearHistory()
                    }
                ) {
                    Text("Clear history", color = StatusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Individual File Delete Safety Confirmation Dialog
    if (taskToDelete != null) {
        val task = taskToDelete!!
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = {
                Text(
                    text = "Delete file from device?",
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextCrisp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${task.title}\" from your device storage? This cannot be undone.",
                    style = Typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        HapticUtil.mediumClick(context)
                        val t = task
                        taskToDelete = null
                        onDelete(t)
                    }
                ) {
                    Text("Delete", color = StatusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Media File Details Dialog
    if (selectedTaskForDetails != null) {
        val task = selectedTaskForDetails!!
        AlertDialog(
            onDismissRequest = { selectedTaskForDetails = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (task.isAudio) Icons.Default.GraphicEq else Icons.Default.SmartDisplay,
                        contentDescription = null,
                        tint = if (task.isAudio) Color(0xFFFFB74D) else TextCrisp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "File Details",
                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextCrisp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = task.title,
                        style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextCrisp
                    )

                    HorizontalDivider(color = BorderHairline)

                    // Specs Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Type / Format", style = Typography.bodySmall, color = TextSecondary)
                        Text(
                            formatQualityBadge(task),
                            style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (task.isAudio) Color(0xFFFFB74D) else TextCrisp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("File Size", style = Typography.bodySmall, color = TextSecondary)
                        Text(
                            "${formatByteSize(task.totalBytes)} (${task.totalBytes} B)",
                            style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = TextCrisp
                        )
                    }

                    if (task.duration.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Duration", style = Typography.bodySmall, color = TextSecondary)
                            Text(task.duration, style = Typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = TextCrisp)
                        }
                    }

                    if (task.timestamp > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Downloaded", style = Typography.bodySmall, color = TextSecondary)
                            val fullDate = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US).format(Date(task.timestamp))
                            Text(fullDate, style = Typography.bodySmall, color = TextSecondary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Task ID", style = Typography.bodySmall, color = TextSecondary)
                        Text(task.id.take(12), style = Typography.bodySmall, color = TextTertiary)
                    }

                    HorizontalDivider(color = BorderHairline)

                    // File Path
                    Column {
                        Text("Storage Location", style = Typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(3.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = task.filePath ?: "Local storage",
                                style = Typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextTertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toPlay = task
                        selectedTaskForDetails = null
                        HapticUtil.lightClick(context)
                        onPlay(toPlay)
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", color = TextCrisp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTaskForDetails = null }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasDark),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = if (hasMiniPlayer) 140.dp else 80.dp
        )
    ) {
        // ── 1. HEADER, SEARCH & APPROVED SEGMENTED SELECTOR ──
        item(key = "library_top_controls") {
            // Header Row (Library title + Sort + 3-dots Menu)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    style = Typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextCrisp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sort Dropdown Button
                    Box {
                        IconButton(
                            onClick = {
                                HapticUtil.lightClick(context)
                                showSortMenu = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = TextCrisp,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(SurfaceElevated)
                        ) {
                            LibrarySort.values().forEach { sort ->
                                val isSelected = sort == sortOrder
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = sort.label,
                                            color = if (isSelected) TextCrisp else TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = sort.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) TextCrisp else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    onClick = {
                                        HapticUtil.lightClick(context)
                                        sortOrder = sort
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Secondary Menu (3-dots overflow) with Clear History
                    Box {
                        IconButton(
                            onClick = {
                                HapticUtil.lightClick(context)
                                showTopOverflowMenu = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions",
                                tint = TextCrisp,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showTopOverflowMenu,
                            onDismissRequest = { showTopOverflowMenu = false },
                            modifier = Modifier.background(SurfaceElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear History", color = StatusError) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = StatusError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showTopOverflowMenu = false
                                    HapticUtil.mediumClick(context)
                                    showClearConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // ── 2. QUIET SUMMARY UNDERNEATH ──
            Text(
                text = "${completedHistory.size} items · ${formatByteSize(totalBytes)}",
                style = Typography.bodySmall.copy(fontSize = 12.5.sp),
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ── 3. SEARCH FIELD ("Search downloads") ──
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF131316),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E1E22)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF6E6E76),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 11.dp),
                        textStyle = Typography.bodyMedium.copy(color = TextCrisp),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search downloads",
                                    style = Typography.bodyMedium,
                                    color = Color(0xFF5E5E66)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                HapticUtil.lightClick(context)
                                searchQuery = ""
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color(0xFF888890), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 4. APPROVED GRAPHITE SEGMENTED SELECTOR ──
            GraphiteSegmentedSelector(
                selectedFilter = filterType,
                allCount = completedHistory.size,
                videoCount = videoCount,
                audioCount = audioCount,
                onFilterSelected = { filterType = it }
            )

            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── 5. GROUPED MEDIA LIST PANEL OR EMPTY STATE ──
        if (filteredHistory.isEmpty()) {
            item(key = "library_empty_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF141418),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E36)),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(SurfaceElevated)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (completedHistory.isEmpty()) Icons.Default.VideoLibrary else Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = TextCrisp,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (completedHistory.isEmpty()) "Library is empty" else "No matching downloads",
                                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.5.sp),
                                color = TextCrisp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (completedHistory.isEmpty())
                                    "Downloaded videos and audio will appear here for offline playback anytime."
                                else
                                    "No downloads match \"$searchQuery\". Try checking the spelling or resetting the filters.",
                                style = Typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                color = Color(0xFFD4D4D8),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(18.dp))
                            if (completedHistory.isEmpty()) {
                                Button(
                                    onClick = {
                                        HapticUtil.mediumClick(context)
                                        onNavigateHome()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TextCrisp,
                                        contentColor = CanvasDark
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        tint = CanvasDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Start a download",
                                        color = CanvasDark,
                                        style = Typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        HapticUtil.lightClick(context)
                                        searchQuery = ""
                                        filterType = "All"
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = SurfaceElevated,
                                        contentColor = TextCrisp
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Reset filters",
                                        color = TextCrisp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "grouped_media_panel") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF141416),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222226)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        filteredHistory.forEachIndexed { index, task ->
                            key(task.id) {
                                var showItemMenu by remember { mutableStateOf(false) }

                                val imageModel = remember(task.thumbnail, task.filePath, task.isAudio) {
                                    when {
                                        !task.isAudio && !task.filePath.isNullOrBlank() -> File(task.filePath)
                                        task.thumbnail.isNotBlank() -> task.thumbnail
                                        !task.filePath.isNullOrBlank() -> File(task.filePath)
                                        else -> null
                                    }
                                }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        HapticUtil.lightClick(context)
                                        onPlay(task)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ── AREA 1: THUMBNAIL (86.dp x 49.dp, 16:9, softly rounded) ──
                                Box(
                                    modifier = Modifier
                                        .width(86.dp)
                                        .height(49.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E22))
                                        .border(0.5.dp, Color(0xFF28282E), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (imageModel != null) {
                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.linearGradient(
                                                        if (task.isAudio)
                                                            listOf(Color(0xFF2E2214), Color(0xFF141210))
                                                        else
                                                            listOf(Color(0xFF1E2028), Color(0xFF0C0C0F))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (task.isAudio) Icons.Default.GraphicEq else Icons.Default.SmartDisplay,
                                                contentDescription = null,
                                                tint = if (task.isAudio) Color(0xFFFFB74D) else TextTertiary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // ── AREA 2: FILE INFORMATION (Title + Plain Quiet Metadata) ──
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    // Title: up to 2 lines, clean spacing
                                    Text(
                                        text = task.title.replace(Regex("\\s+"), " ").trim(),
                                        style = Typography.titleSmall.copy(
                                            fontSize = 13.5.sp,
                                            lineHeight = 18.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = TextCrisp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Plain metadata: "MP3 · 06:43 · 15.4 MB" (No colored badge box!)
                                    val formatStr = formatSimpleBadge(task)
                                    val metaParts = buildList {
                                        if (formatStr.isNotBlank()) add(formatStr)
                                        if (task.duration.isNotBlank()) add(task.duration)
                                        if (task.totalBytes > 0L) add(formatByteSize(task.totalBytes))
                                    }
                                    if (metaParts.isNotEmpty()) {
                                        Text(
                                            text = metaParts.joinToString(" · "),
                                            style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // ── AREA 3: SEPARATE OVERFLOW BUTTON (3-dots ⋮) ──
                                Box {
                                    IconButton(
                                        onClick = {
                                            HapticUtil.lightClick(context)
                                            showItemMenu = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "Options",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showItemMenu,
                                        onDismissRequest = { showItemMenu = false },
                                        modifier = Modifier.background(SurfaceElevated)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Play Media", color = TextCrisp, fontWeight = FontWeight.SemiBold) },
                                            leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null, tint = TextCrisp) },
                                            onClick = {
                                                showItemMenu = false
                                                HapticUtil.lightClick(context)
                                                onPlay(task)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Play Externally", color = TextCrisp) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = TextCrisp) },
                                            onClick = {
                                                showItemMenu = false
                                                HapticUtil.lightClick(context)
                                                onOpenExternal(task)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share File", color = TextCrisp) },
                                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = TextCrisp) },
                                            onClick = {
                                                showItemMenu = false
                                                HapticUtil.lightClick(context)
                                                onShare(task)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("File Details", color = TextCrisp) },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = TextCrisp) },
                                            onClick = {
                                                showItemMenu = false
                                                HapticUtil.lightClick(context)
                                                selectedTaskForDetails = task
                                            }
                                        )
                                        HorizontalDivider(color = BorderHairline)
                                        DropdownMenuItem(
                                            text = { Text("Delete from Device", color = StatusError) },
                                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = StatusError) },
                                            onClick = {
                                                showItemMenu = false
                                                HapticUtil.mediumClick(context)
                                                taskToDelete = task
                                            }
                                        )
                                    }
                                }
                            }

                            // Subtle Inset Divider between rows (not after final row!)
                            if (index < filteredHistory.lastIndex) {
                                HorizontalDivider(
                                    color = Color(0xFF222227),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

// TAB 4: REDESIGNED SETTINGS TAB (CLEAN SECTIONS, ZERO DUPLICATE TITLE)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun SettingsTab(
    onShowIntro: (() -> Unit)? = null,
    onShowUpdateDialog: (AppUpdateInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val prefs = remember { context.getSharedPreferences("downloadivo_prefs", Context.MODE_PRIVATE) }

    // Preference: Cloud Turbo
    var isCloudTurbo by rememberSaveable {
        mutableStateOf(prefs.getBoolean("cloud_turbo_enabled", true))
    }

    // Default base URL
    val defaultUrl = "https://downloadivo.vercel.app"

    // Server Endpoint State
    var backendUrlInput by rememberSaveable {
        mutableStateOf(prefs.getString("custom_backend_url", DownloadEngine.activeBaseUrl) ?: DownloadEngine.activeBaseUrl)
    }
    var isAdvancedExpanded by rememberSaveable { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var testJob by remember { mutableStateOf<Job?>(null) }

    // Dialog state
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    // Update state
    val updateState by UpdateManager.updateState.collectAsState()
    var isCheckingUpdate by remember { mutableStateOf(false) }

    // Save Locations
    val videosLocation = remember {
        try {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Downloadivo").absolutePath
        } catch (_: Exception) {
            "Movies/Downloadivo"
        }
    }
    val audioLocation = remember {
        try {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Downloadivo").absolutePath
        } catch (_: Exception) {
            "Music/Downloadivo"
        }
    }

    // Clear History Safety Confirmation Dialog
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = {
                Text(
                    text = "Clear download history?",
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextCrisp
                )
            },
            text = {
                Text(
                    text = "Downloaded files stay on this device. Only the list of past download records in Downloadivo will be cleared.",
                    style = Typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        HapticUtil.mediumClick(context)
                        showClearHistoryConfirm = false
                        DownloadRepository.clearHistory()
                        Toast.makeText(context, "Download history cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear history", color = StatusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel", color = TextCrisp)
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. DOWNLOADS SECTION ──
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "DOWNLOADS",
                    style = Typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = TextTertiary
                )

                // Cloud Turbo Toggle Row (Tapping row or switch toggles exactly once)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.lightClick(context)
                            val next = !isCloudTurbo
                            isCloudTurbo = next
                            prefs.edit().putBoolean("cloud_turbo_enabled", next).apply()
                            DownloadEngine.isCloudTurboEnabled = next
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Cloud Turbo",
                            style = Typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            ),
                            color = TextCrisp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Accelerates downloads by streaming through Downloadivo's high-speed cloud edge tunnel.",
                            style = Typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = isCloudTurbo,
                        onCheckedChange = { next ->
                            HapticUtil.lightClick(context)
                            isCloudTurbo = next
                            prefs.edit().putBoolean("cloud_turbo_enabled", next).apply()
                            DownloadEngine.isCloudTurboEnabled = next
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CanvasDark,
                            checkedTrackColor = TextCrisp,
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = SurfaceElevated
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = "Cloud Turbo acceleration"
                        }
                    )
                }

                HorizontalDivider(color = BorderHairline)

                // Save Locations: separate rows for Videos and Audio
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Storage locations",
                        style = Typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = TextCrisp
                    )

                    // Videos Location Row
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Videos",
                            style = Typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderHairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = videosLocation,
                                style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = TextCrisp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

                    // Audio Location Row
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Audio",
                            style = Typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderHairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = audioLocation,
                                style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = TextCrisp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 2. LIBRARY HISTORY SECTION ──
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "LIBRARY HISTORY",
                    style = Typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = TextTertiary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Clear download history",
                            style = Typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            ),
                            color = TextCrisp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Clears past download records. Downloaded files stay on this device.",
                            style = Typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = TextSecondary
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            HapticUtil.mediumClick(context)
                            showClearHistoryConfirm = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SurfaceElevated,
                            contentColor = StatusError
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = StatusError
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Clear",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // ── 3. ADVANCED SERVER SETTINGS SECTION ──
        val isDefaultActive = remember(backendUrlInput, defaultUrl) {
            val clean = backendUrlInput.trim().trimEnd('/')
            clean == defaultUrl.trimEnd('/') || clean == "https://mazda-prints-equity-albuquerque.trycloudflare.com"
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row (Click to Expand / Collapse)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticUtil.lightClick(context)
                            isAdvancedExpanded = !isAdvancedExpanded
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "ADVANCED",
                            style = Typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Custom server endpoint",
                            style = Typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            ),
                            color = TextCrisp
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = if (isDefaultActive) "Default server active" else "Custom: ${backendUrlInput.trim()}",
                            style = Typography.bodySmall.copy(fontSize = 12.sp),
                            color = if (isDefaultActive) TextSecondary else Color(0xFFFFB74D),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isAdvancedExpanded) "Collapse" else "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Expanded Section Body
                if (isAdvancedExpanded) {
                    HorizontalDivider(color = BorderHairline)

                    Text(
                        text = "Server address",
                        style = Typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = backendUrlInput,
                        onValueChange = { newVal ->
                            backendUrlInput = newVal
                            testJob?.cancel()
                            testStatus = null
                            isTesting = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://your-server.com", color = TextTertiary) },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TextCrisp,
                            unfocusedBorderColor = BorderHairline,
                            focusedTextColor = TextCrisp,
                            unfocusedTextColor = TextCrisp,
                            cursorColor = TextCrisp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        )
                    )

                    Text(
                        text = "Default: https://downloadivo.vercel.app",
                        style = Typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextTertiary
                    )

                    // Dedicated Test Row with clear status
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Test entered address button
                            FilledTonalButton(
                                onClick = {
                                    HapticUtil.lightClick(context)
                                    isTesting = true
                                    testStatus = "Testing entered address..."
                                    testJob?.cancel()
                                    val candidate = backendUrlInput
                                    testJob = coroutineScope.launch {
                                        val res = DownloadEngine.testEndpoint(candidate)
                                        isTesting = false
                                        if (res.isSuccess) {
                                            testStatus = "✓ ${res.getOrNull()}"
                                        } else {
                                            testStatus = "✗ ${res.exceptionOrNull()?.message ?: "Connection failed"}"
                                        }
                                    }
                                },
                                enabled = !isTesting && backendUrlInput.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = SurfaceElevated,
                                    contentColor = TextCrisp
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        color = TextCrisp,
                                        strokeWidth = 1.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (isTesting) "Testing..." else "Test connection",
                                    style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            // Save button
                            Button(
                                onClick = {
                                    HapticUtil.mediumClick(context)
                                    val clean = backendUrlInput.trim().trimEnd('/')
                                    DownloadEngine.activeBaseUrl = clean
                                    prefs.edit().putString("custom_backend_url", clean).apply()
                                    Toast.makeText(context, "Server address saved", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TextCrisp,
                                    contentColor = CanvasDark
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Text("Save", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }

                            // Reset button
                            OutlinedButton(
                                onClick = {
                                    HapticUtil.lightClick(context)
                                    backendUrlInput = defaultUrl
                                    DownloadEngine.activeBaseUrl = defaultUrl
                                    prefs.edit().remove("custom_backend_url").apply()
                                    testStatus = null
                                    Toast.makeText(context, "Reset to default server", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text("Reset", style = Typography.labelSmall)
                            }
                        }

                        // Feedback message row below action buttons
                        if (testStatus != null) {
                            Text(
                                text = testStatus!!,
                                style = Typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = when {
                                    testStatus!!.startsWith("✓") -> Color(0xFF81C784)
                                    testStatus!!.startsWith("✗") -> StatusError
                                    else -> TextSecondary
                                }
                            )
                        }
                    }
                }
            }
        }

        // ── 4. ABOUT & UPDATE STATUS SECTION ──
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ABOUT",
                    style = Typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = TextTertiary
                )

                // App Identity with dedicated space
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_white),
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, CardBorderDark, RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Downloadivo Mobile",
                            style = Typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = TextCrisp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE}",
                            style = Typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = TextSecondary
                        )
                    }
                }

                // Quiet Inline Update Status (Placed below identity to prevent horizontal breakage)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    when (val st = updateState) {
                        is UpdateState.Checking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = TextSecondary,
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Checking for updates...",
                                style = Typography.bodySmall.copy(fontSize = 12.sp),
                                color = TextSecondary
                            )
                        }
                        is UpdateState.UpToDate -> {
                            Text(
                                text = "✓ App is up to date",
                                style = Typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF81C784)
                            )
                        }
                        is UpdateState.Available -> {
                            Text(
                                text = "★ Update v${st.updateInfo.versionName} available",
                                style = Typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFFFFB74D)
                            )
                        }
                        is UpdateState.Error -> {
                            Text(
                                text = "✗ Could not check for updates (${st.message.take(28)})",
                                style = Typography.bodySmall.copy(fontSize = 12.sp),
                                color = StatusError
                            )
                        }
                        else -> {
                            Text(
                                text = "✓ App is up to date",
                                style = Typography.bodySmall.copy(fontSize = 12.sp),
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Dedicated Check for Updates Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(10.dp))
                        .clickable(enabled = !isCheckingUpdate && updateState !is UpdateState.Checking) {
                            HapticUtil.lightClick(context)
                            isCheckingUpdate = true
                            coroutineScope.launch {
                                val update = UpdateManager.checkForUpdates(BuildConfig.VERSION_CODE)
                                isCheckingUpdate = false
                                if (update != null) {
                                    onShowUpdateDialog(update)
                                } else if (UpdateManager.updateState.value is UpdateState.UpToDate) {
                                    Toast.makeText(context, "Downloadivo is up to date (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCheckingUpdate || updateState is UpdateState.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                color = TextCrisp,
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking for updates...", color = TextCrisp, style = Typography.labelMedium)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (updateState is UpdateState.Error) "Retry update check" else "Check for Updates",
                                color = TextCrisp,
                                style = Typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(76.dp))
    }
}

private fun formatByteSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
        @Suppress("DEPRECATION")
        cm.activeNetworkInfo?.isConnected == true
    }
}
