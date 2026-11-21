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
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.semantics.contentDescription
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
    clipboardUrl: String? = null,
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
    var inputUrl by remember { mutableStateOf("") }
    var resolvedInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var selectedFormat by remember { mutableStateOf("best") }
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

    // Persistent Single ExoPlayer Instance across Miniplayer, Full Player & PiP
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
                2500,  // minBufferMs (2.5s)
                35000, // maxBufferMs (35s)
                1500,  // bufferForPlaybackMs (1.5s - guarantees video keyframe is ready)
                2000   // bufferForPlaybackAfterRebufferMs (2.0s)
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

    var isExoPlaying by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isExoPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                isExoPlaying = exoPlayer.isPlaying
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
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

    val currentTask by DownloadService.currentTask.collectAsState()
    val queuedTasks by DownloadService.queuedTasks.collectAsState()
    val historyList by DownloadRepository.history.collectAsState()

    var showCompletionPill by remember { mutableStateOf(false) }
    LaunchedEffect(currentTask.status) {
        if (currentTask.status == DownloadStatus.SUCCESS) {
            showCompletionPill = true
            delay(5000)
            showCompletionPill = false
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
            if (currentTab != AppTab.HOME || resolvedInfo != null || isResolving) {
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
                            AppTab.LIBRARY -> {
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
                                        text = "Library",
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
                                        currentTab = tab
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
                                        val activeCount = (if (currentTask.status == DownloadStatus.DOWNLOADING) 1 else 0) + queuedTasks.size
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
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) { width -> direction * (width * 0.28f).toInt() } + fadeIn(tween(200)))
                    .togetherWith(
                        slideOutHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { width -> -direction * (width * 0.28f).toInt() } + fadeOut(tween(180))
                    )
                },
                label = "tab_navigation_sliding"
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
                            activePlaybackUri = streamUrl
                            activePlaybackOriginalUrl = originalUrl
                            activePlaybackTitle = title
                            activePlaybackIsAudio = isAudio
                            activePlaybackQualities = qualities
                            activePlaybackCurrentQuality = initialQuality
                            activePlaybackDuration = duration
                            activePlaybackThumbnail = resolvedInfo?.thumbnail ?: ""
                            activePlaybackAuthor = resolvedInfo?.author ?: ""
                            activePlaybackAudioTracks = if (resolvedInfo?.audioTracks?.isNotEmpty() == true) resolvedInfo!!.audioTracks else listOf("Original Audio", "Hindi (हिन्दी)", "English")
                            activePlaybackCurrentAudioTrack = activePlaybackAudioTracks.firstOrNull() ?: "Original Audio"
                            isPlayerMinimized = false
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

                    AppTab.QUEUE -> QueueTab(
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
                        onNavigateHome = { currentTab = AppTab.HOME }
                    )

                    AppTab.LIBRARY -> LibraryTab(
                        history = historyList,
                        onPlay = { task ->
                            HapticUtil.mediumClick(context)
                            task.filePath?.let { uriStr ->
                                activePlaybackUri = uriStr
                                activePlaybackTitle = task.title
                                activePlaybackIsAudio = task.isAudio
                                activePlaybackDuration = task.duration
                                activePlaybackQualities = emptyList()
                                activePlaybackCurrentQuality = ""
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
                AnimatedVisibility(
                    visible = currentTask.status == DownloadStatus.DOWNLOADING && currentTab != AppTab.QUEUE,
                    enter = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                    exit = slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut()
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = if (currentTask.progress >= 0) currentTask.progress / 100f else 0f,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        label = "pill_progress"
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                        shadowElevation = 16.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clickable {
                                HapticUtil.lightClick(context)
                                currentTab = AppTab.QUEUE
                            }
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
                                    onClick = {
                                        HapticUtil.lightClick(context)
                                        val cancelIntent = Intent(context, DownloadService::class.java).apply {
                                            action = DownloadService.ACTION_CANCEL_DOWNLOAD
                                        }
                                        context.startService(cancelIntent)
                                    },
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

                // Completion Pill (Auto-dismisses in 5s or tap X to close, only on non-queue tabs)
                AnimatedVisibility(
                    visible = currentTask.status == DownloadStatus.SUCCESS && currentTab != AppTab.QUEUE && showCompletionPill,
                    enter = slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                    exit = slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        shadowElevation = 14.dp,
                        modifier = Modifier
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
                                    text = currentTask.title,
                                    style = Typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            currentTask.filePath?.let { filePath ->
                                Button(
                                    onClick = {
                                        HapticUtil.mediumClick(context)
                                        activePlaybackUri = filePath
                                        activePlaybackTitle = currentTask.title
                                        activePlaybackIsAudio = currentTask.isAudio
                                        activePlaybackDuration = currentTask.duration
                                        activePlaybackQualities = emptyList()
                                        activePlaybackCurrentQuality = ""
                                    },
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
                                onClick = {
                                    HapticUtil.lightClick(context)
                                    showCompletionPill = false
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // YouTube Floating Miniplayer (docked above bottom navigation bar when player is minimized!)
                androidx.compose.animation.AnimatedVisibility(
                    visible = activePlaybackUri != null && isPlayerMinimized,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
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
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it } + fadeOut(tween(180))
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
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = TextCrisp,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Paste",
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

// ═══════════════════════════════════════════════════════════════════════
// TAB 2: ACTIVE QUEUE TAB (STUDIO-GRADE DOWNLOAD MANAGER)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun QueueTab(
    currentTask: DownloadTask,
    queuedTasks: List<DownloadTask>,
    onCancelCurrent: () -> Unit,
    onCancelAll: () -> Unit = {},
    onRemoveQueued: (String) -> Unit,
    onNavigateHome: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val isAnyActive = currentTask.status == DownloadStatus.DOWNLOADING || queuedTasks.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── TOP DASHBOARD STATUS SUMMARY CARD (Only when active) ──
        if (isAnyActive) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (currentTask.status == DownloadStatus.DOWNLOADING) StatusSuccess else TextTertiary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (currentTask.status == DownloadStatus.DOWNLOADING) {
                                    "Downloading Active (${currentTask.progress}%)"
                                } else if (queuedTasks.isNotEmpty()) {
                                    "${queuedTasks.size} Tasks in Queue"
                                } else {
                                    "Engine Idle • All Clear"
                                },
                                style = Typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextCrisp
                            )
                            Text(
                                text = "${queuedTasks.size} queued • Turbo Multi-Thread",
                                style = Typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }

                    Button(
                        onClick = {
                            HapticUtil.mediumClick(context)
                            onCancelAll()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusError.copy(alpha = 0.15f),
                            contentColor = StatusError
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = null, tint = StatusError, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel All", style = Typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── FAILED TASK RETRY ALERT ──
        if (currentTask.status == DownloadStatus.ERROR) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = StatusError,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Download Interrupted",
                            style = Typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusError
                        )
                        Text(
                            text = currentTask.title.ifBlank { "Media Stream" },
                            style = Typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        currentTask.error?.let {
                            Text(
                                text = it,
                                style = Typography.bodySmall,
                                color = TextTertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onNavigateHome,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", style = Typography.labelSmall)
                    }
                }
            }
        }

        // ── ACTIVE DOWNLOADING HERO CARD ──
        if (currentTask.status == DownloadStatus.DOWNLOADING) {
            val animatedTaskProgress by animateFloatAsState(
                targetValue = if (currentTask.progress >= 0) currentTask.progress / 100f else 0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "hero_progress"
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                shadowElevation = 18.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row with Thumbnail
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // 16:9 Thumbnail Image
                        Box(
                            modifier = Modifier
                                .size(88.dp, 54.dp)
                                .clip(RoundedCornerShape(10.dp))
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
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = SurfaceHighlight,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = if (currentTask.isAudio) "MP3 320k" else (if (currentTask.format.contains("1080")) "1080p FHD" else (if (currentTask.format.contains("720")) "720p HD" else currentTask.format.uppercase())),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCrisp,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                if (currentTask.duration.isNotBlank()) {
                                    Text(
                                        text = currentTask.duration,
                                        style = Typography.bodySmall,
                                        color = TextTertiary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentTask.title.ifBlank { "Downloading stream..." },
                                style = Typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextCrisp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                HapticUtil.lightClick(context)
                                onCancelCurrent()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = StatusError, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = if (currentTask.progress >= 0) "${currentTask.progress}%" else formatByteSize(currentTask.bytesDownloaded),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextCrisp
                        )

                        Column(
                            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            if (currentTask.speed.isNotBlank()) {
                                Text(
                                    text = "⚡ ${currentTask.speed}",
                                    style = Typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCrisp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = buildString {
                                    append(formatByteSize(currentTask.bytesDownloaded))
                                    if (currentTask.totalBytes > 0) {
                                        append(" / ")
                                        append(formatByteSize(currentTask.totalBytes))
                                    }
                                    if (currentTask.eta.isNotBlank()) {
                                        append(" • ")
                                        append(currentTask.eta)
                                    }
                                },
                                style = Typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Studio Smooth Progress Indicator
                    LinearProgressIndicator(
                        progress = { animatedTaskProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = TextCrisp,
                        trackColor = SurfaceHighlight
                    )
                }
            }
        }

        // ── QUEUED TASKS SECTION ──
        if (queuedTasks.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Up Next in Queue (${queuedTasks.size})",
                    style = Typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )

                queuedTasks.forEachIndexed { index, task ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SurfaceElevated,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = Typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCrisp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(50.dp, 32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black),
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
                                        if (task.isAudio) Icons.Default.MusicNote else Icons.Default.Download,
                                        contentDescription = null,
                                        tint = TextCrisp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = Typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextCrisp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append(if (task.isAudio) "MP3 320k" else "MP4 ${task.format.uppercase()}")
                                        if (task.totalBytes > 0) {
                                            append(" • ~")
                                            append(formatByteSize(task.totalBytes))
                                        }
                                        if (task.duration.isNotBlank()) {
                                            append(" • ")
                                            append(task.duration)
                                        }
                                    },
                                    style = Typography.bodySmall,
                                    color = TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = {
                                    HapticUtil.lightClick(context)
                                    onRemoveQueued(task.id)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── CLEAN POLISHED EMPTY STATE ──
        if (currentTask.status != DownloadStatus.DOWNLOADING && queuedTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FormatListBulleted,
                            contentDescription = null,
                            tint = TextCrisp,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Queue is empty",
                        style = Typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextCrisp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Downloads in progress or pending will appear here.",
                        style = Typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = {
                            HapticUtil.mediumClick(context)
                            onNavigateHome()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextCrisp,
                            contentColor = CanvasDark
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = CanvasDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New download", style = Typography.labelMedium, fontWeight = FontWeight.Bold, color = CanvasDark)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(76.dp))
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

@Composable
private fun LibraryTab(
    history: List<DownloadTask>,
    onPlay: (DownloadTask) -> Unit,
    onShare: (DownloadTask) -> Unit,
    onOpenExternal: (DownloadTask) -> Unit,
    onDelete: (DownloadTask) -> Unit,
    onNavigateHome: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }
    var sortOrder by remember { mutableStateOf(LibrarySort.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
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
            LibrarySort.NEWEST -> filtered
            LibrarySort.OLDEST -> filtered.reversed()
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
                    text = "Clear Library Index?",
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextCrisp
                )
            },
            text = {
                Text(
                    text = "This removes all entries from your offline library list. Your actual video and audio files remain 100% safe in device storage (Movies/Downloadivo and Music/Downloadivo).",
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
                    Text("Clear All", color = StatusError, fontWeight = FontWeight.Bold)
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
                        Text("Type / Quality", style = Typography.bodySmall, color = TextSecondary)
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
                            val fullDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(task.timestamp))
                            Text(fullDate, style = Typography.bodySmall, color = TextSecondary)
                        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (completedHistory.isNotEmpty()) {
            // ── OFFLINE VAULT STORAGE HERO BANNER ──
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated)
                                    .border(1.dp, BorderHairline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = TextCrisp,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Offline Vault",
                                    style = Typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.5.sp),
                                    color = TextCrisp
                                )
                                Text(
                                    text = "${completedHistory.size} items offline",
                                    style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = TextSecondary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Storage Pill Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = formatByteSize(totalBytes),
                                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                                        color = TextCrisp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Safe Clear Button
                            IconButton(
                                onClick = {
                                    HapticUtil.mediumClick(context)
                                    showClearConfirmDialog = true
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Clear library",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Ratio Bar & Breakdown
                    val videoBytes = completedHistory.filter { !it.isAudio }.sumOf { it.totalBytes }
                    val audioBytes = completedHistory.filter { it.isAudio }.sumOf { it.totalBytes }
                    val videoFraction = if (totalBytes > 0) (videoBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.08f, 0.92f) else 0.5f

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SurfaceElevated)
                    ) {
                        if (videoBytes > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(if (audioBytes > 0) videoFraction else 1f)
                                    .fillMaxHeight()
                                    .background(TextCrisp)
                            )
                        }
                        if (audioBytes > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(if (videoBytes > 0) (1f - videoFraction) else 1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFFFFB74D))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(TextCrisp, CircleShape))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Videos ($videoCount · ${formatByteSize(videoBytes)})",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFFFFB74D), CircleShape))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Audio ($audioCount · ${formatByteSize(audioBytes)})",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── MODERN PILL SEARCH BAR ──
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
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
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        textStyle = Typography.bodyMedium.copy(color = TextCrisp),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search titles or formats...",
                                    style = Typography.bodyMedium,
                                    color = TextQuaternary
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
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── FILTER CHIPS + SORT BUTTON ROW ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Filter Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    listOf(
                        Triple("All", "All (${completedHistory.size})", Icons.Default.Layers),
                        Triple("Videos", "Videos ($videoCount)", Icons.Default.SmartDisplay),
                        Triple("Audio", "Audio ($audioCount)", Icons.Default.GraphicEq)
                    ).forEach { (key, label, icon) ->
                        val isSelected = filterType == key
                        Surface(
                            onClick = {
                                HapticUtil.lightClick(context)
                                filterType = key
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) TextCrisp else SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) TextCrisp else CardBorderDark
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) CanvasDark else TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = label,
                                    style = Typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CanvasDark else TextSecondary
                                )
                            }
                        }
                    }
                }

                // Sort Dropdown Button
                Box {
                    Surface(
                        onClick = {
                            HapticUtil.lightClick(context)
                            showSortMenu = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = sortOrder.icon,
                                contentDescription = null,
                                tint = TextCrisp,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Sort options",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (filteredHistory.isEmpty()) {
            // ── EMPTY STATE (HIGH IMPACT MINIMALIST VAULT) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(26.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (completedHistory.isEmpty()) Icons.Default.VideoLibrary else Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = TextCrisp,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = if (completedHistory.isEmpty()) "Your Offline Vault is Empty" else "No Matching Media Found",
                            style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.5.sp),
                            color = TextCrisp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (completedHistory.isEmpty())
                                "Downloaded videos and audio are saved directly onto your device for high-speed offline playback anytime."
                            else
                                "No downloads matched \"$searchQuery\". Try checking the spelling or resetting the filters.",
                            style = Typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        if (completedHistory.isEmpty()) {
                            Spacer(modifier = Modifier.height(18.dp))
                            // Feature pills
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("1080p / 4K", "Lossless Audio", "100% Offline").forEach { feature ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = SurfaceElevated,
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderHairline),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = feature,
                                            style = Typography.labelSmall.copy(fontSize = 10.sp),
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    HapticUtil.mediumClick(context)
                                    onNavigateHome()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TextCrisp, contentColor = CanvasDark),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = CanvasDark, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Media Now", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedButton(
                                onClick = {
                                    HapticUtil.lightClick(context)
                                    searchQuery = ""
                                    filterType = "All"
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextCrisp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Filters", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // ── YOUTUBE-GRADE STUDIO MEDIA LIST ──
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(filteredHistory, key = { it.id }) { task ->
                    var showItemMenu by remember { mutableStateOf(false) }

                    val localFile = remember(task.filePath) { task.filePath?.let { File(it) } }
                    val hasLocalFile = remember(localFile) { localFile != null && localFile.exists() }
                    val imageModel = remember(task.thumbnail, hasLocalFile, task.filePath) {
                        when {
                            !task.isAudio && hasLocalFile -> localFile
                            task.thumbnail.isNotBlank() -> task.thumbnail
                            hasLocalFile -> localFile
                            else -> null
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                HapticUtil.lightClick(context)
                                onPlay(task)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 16:9 Cinema Thumbnail (124dp x 70dp - YouTube Standard)
                            Box(
                                modifier = Modifier
                                    .width(124.dp)
                                    .height(70.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceElevated)
                                    .border(0.5.dp, BorderHairline, RoundedCornerShape(10.dp)),
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
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                // Subtle Central Play Affordance Overlay
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // YouTube-Style Duration Badge
                                if (task.duration.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xE6000000))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = task.duration,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Metadata Column (Title 2 lines + Quality Badge + Size + Relative Date)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = task.title,
                                    style = Typography.titleSmall.copy(
                                        fontSize = 13.5.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = TextCrisp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // High-Contrast Format & Quality Badge
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (task.isAudio) Color(0xFF2A2012) else SurfaceHighlight,
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderHairline)
                                    ) {
                                        Text(
                                            text = formatQualityBadge(task),
                                            color = if (task.isAudio) Color(0xFFFFB74D) else TextCrisp,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }

                                    if (task.totalBytes > 0) {
                                        Text(
                                            text = formatByteSize(task.totalBytes),
                                            style = Typography.bodySmall,
                                            fontSize = 11.5.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    val relTime = formatTimestampRelative(task.timestamp)
                                    if (relTime.isNotBlank()) {
                                        Text(
                                            text = "•",
                                            fontSize = 11.sp,
                                            color = TextTertiary
                                        )
                                        Text(
                                            text = relTime,
                                            style = Typography.bodySmall,
                                            fontSize = 11.5.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            // Right Action: Sleek 3-Dot (⋮) Menu Button
                            Box {
                                IconButton(
                                    onClick = {
                                        HapticUtil.lightClick(context)
                                        showItemMenu = true
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
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
                                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, tint = TextCrisp) },
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
                                            onDelete(task)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(76.dp))
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
    val updateState by UpdateManager.updateState.collectAsState()
    var isCheckingUpdate by remember { mutableStateOf(false) }

    var backendUrlInput by remember { mutableStateOf(DownloadEngine.activeBaseUrl) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Cloud Turbo Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cloud Turbo", style = Typography.titleSmall, color = TextCrisp)
                        Text("Line-rate edge stream multiplexing", style = Typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CanvasDark,
                            checkedTrackColor = TextCrisp
                        )
                    )
                }

                HorizontalDivider(color = BorderHairline)

                // Backend Endpoint
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backend Endpoint", style = Typography.titleSmall, color = TextCrisp)
                    OutlinedTextField(
                        value = backendUrlInput,
                        onValueChange = {
                            backendUrlInput = it
                            DownloadEngine.activeBaseUrl = it.trim().trimEnd('/')
                            testStatus = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TextCrisp,
                            unfocusedBorderColor = BorderHairline,
                            focusedTextColor = TextCrisp,
                            unfocusedTextColor = TextCrisp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = testStatus ?: "Default: https://downloadivo.vercel.app",
                            style = Typography.bodySmall,
                            color = if (testStatus?.startsWith("✓") == true) TextCrisp else TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        FilledTonalButton(
                            onClick = {
                                isTesting = true
                                testStatus = "Testing..."
                                coroutineScope.launch {
                                    try {
                                        val testRes = DownloadEngine.fetchMediaInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                                        isTesting = false
                                        if (testRes.isSuccess) {
                                            testStatus = "✓ Connected (200 OK)"
                                        } else {
                                            testStatus = "✗ Failed"
                                        }
                                    } catch (_: Exception) {
                                        isTesting = false
                                        testStatus = "✗ Unreachable"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(if (isTesting) "..." else "Test", style = Typography.labelSmall)
                        }
                    }
                }

                HorizontalDivider(color = BorderHairline)

                // Storage location
                Column {
                    Text("Storage location", style = Typography.titleSmall, color = TextCrisp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Movies/Downloadivo & Music/Downloadivo", style = Typography.bodySmall, color = TextSecondary)
                }

                HorizontalDivider(color = BorderHairline)

                // Clear Download History
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Clear History", style = Typography.titleSmall, color = TextCrisp)
                        Text("Remove all entries from Library", style = Typography.bodySmall, color = TextSecondary)
                    }
                    FilledTonalButton(
                        onClick = { DownloadRepository.clearHistory() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", style = Typography.labelSmall)
                    }
                }

                HorizontalDivider(color = BorderHairline)

                // App version & In-App Update System
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_icon_white),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Downloadivo Mobile",
                                    style = Typography.titleMedium,
                                    color = TextCrisp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "v${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE}",
                                    style = Typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Version Status Badge
                        val isUpdateAvail = updateState is UpdateState.Available
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isUpdateAvail) TextCrisp else SurfaceElevated)
                                .border(1.dp, CardBorderDark, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isUpdateAvail) "Update Ready" else "Up to date",
                                color = if (isUpdateAvail) CanvasDark else TextCrisp,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Check for Updates Button with Spring Bounce & Tactile Haptic
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceElevated)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp))
                            .clickable(enabled = !isCheckingUpdate) {
                                HapticUtil.lightClick(context)
                                isCheckingUpdate = true
                                coroutineScope.launch {
                                    val update = UpdateManager.checkForUpdates(BuildConfig.VERSION_CODE)
                                    isCheckingUpdate = false
                                    if (update != null) {
                                        onShowUpdateDialog(update)
                                    } else {
                                        android.widget.Toast.makeText(context, "Downloadivo is up to date (v${BuildConfig.VERSION_NAME})", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = TextCrisp,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Checking for updates...", color = TextCrisp, style = Typography.labelMedium)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Check for Updates", color = TextCrisp, style = Typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
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
