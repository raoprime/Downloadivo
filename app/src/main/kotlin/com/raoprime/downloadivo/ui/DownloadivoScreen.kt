package com.raoprime.downloadivo.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.raoprime.downloadivo.R
import com.raoprime.downloadivo.data.DownloadRepository
import com.raoprime.downloadivo.model.DownloadStatus
import com.raoprime.downloadivo.model.DownloadTask
import com.raoprime.downloadivo.model.FormatOption
import com.raoprime.downloadivo.model.MediaInfo
import com.raoprime.downloadivo.network.DownloadEngine
import com.raoprime.downloadivo.service.DownloadService
import com.raoprime.downloadivo.ui.player.BuiltInPlayerSheet
import com.raoprime.downloadivo.ui.theme.*
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    HOME("Downloader"),
    QUEUE("Queue"),
    LIBRARY("Library"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadivoScreen(
    sharedUrl: String? = null,
    clipboardUrl: String? = null,
    onClearClipboard: () -> Unit = {},
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

    // Built-in Player State
    var activePlaybackUri by remember { mutableStateOf<String?>(null) }
    var activePlaybackTitle by remember { mutableStateOf("") }
    var activePlaybackIsAudio by remember { mutableStateOf(false) }

    val currentTask by DownloadService.currentTask.collectAsState()
    val queuedTasks by DownloadService.queuedTasks.collectAsState()
    val historyList by DownloadRepository.history.collectAsState()

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
        startTime: String? = null,
        endTime: String? = null
    ) {
        DownloadService.startDownload(
            context = context,
            url = url,
            title = title,
            format = format,
            thumbnail = thumbnail,
            startTime = startTime,
            endTime = endTime
        )
        // Transition gracefully to Queue tab to let user see progress
        currentTab = AppTab.QUEUE
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = "Download queued for $title",
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
            CenterAlignedTopAppBar(
                title = {
                    when (currentTab) {
                        AppTab.HOME -> {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo_white),
                                contentDescription = "Downloadivo",
                                modifier = Modifier.height(24.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        AppTab.QUEUE -> {
                            Text(
                                text = "Queue",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextCrisp
                            )
                        }
                        AppTab.LIBRARY -> {
                            Text(
                                text = "Library",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextCrisp
                            )
                        }
                        AppTab.SETTINGS -> {
                            Text(
                                text = "Settings",
                                style = Typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextCrisp
                            )
                        }
                    }
                },
                actions = {
                    if (currentTab == AppTab.LIBRARY && historyList.isNotEmpty()) {
                        TextButton(
                            onClick = { DownloadRepository.clearHistory() }
                        ) {
                            Text("Clear", color = TextSecondary, style = Typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CanvasDark
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextCrisp,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.HOME,
                    onClick = { currentTab = AppTab.HOME },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Downloader") },
                    label = { Text("Download", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CanvasDark,
                        selectedTextColor = TextCrisp,
                        indicatorColor = TextCrisp,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    )
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.QUEUE,
                    onClick = { currentTab = AppTab.QUEUE },
                    icon = {
                        val activeCount = (if (currentTask.status == DownloadStatus.DOWNLOADING) 1 else 0) + queuedTasks.size
                        BadgedBox(badge = {
                            if (activeCount > 0) {
                                Badge(containerColor = TextCrisp) {
                                    Text("$activeCount", color = CanvasDark, fontWeight = FontWeight.Bold)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Sync, contentDescription = "Queue")
                        }
                    },
                    label = { Text("Queue", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CanvasDark,
                        selectedTextColor = TextCrisp,
                        indicatorColor = TextCrisp,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    )
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.LIBRARY,
                    onClick = { currentTab = AppTab.LIBRARY },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
                    label = { Text("Library", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CanvasDark,
                        selectedTextColor = TextCrisp,
                        indicatorColor = TextCrisp,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    )
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.SETTINGS,
                    onClick = { currentTab = AppTab.SETTINGS },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CanvasDark,
                        selectedTextColor = TextCrisp,
                        indicatorColor = TextCrisp,
                        unselectedIconColor = TextTertiary,
                        unselectedTextColor = TextTertiary
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (currentTab) {
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
                    onInspect = {
                        if (inputUrl.isNotBlank()) {
                            isCustomizeExpanded = true
                            isResolving = true
                            errorMessage = null
                            coroutineScope.launch {
                                val res = DownloadEngine.fetchMediaInfo(inputUrl)
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
                        triggerDownload(
                            url = url,
                            title = resolvedInfo?.title ?: defaultTitle,
                            format = format,
                            thumbnail = resolvedInfo?.thumbnail ?: ""
                        )
                    },
                    onDownloadCustom = { info, format, startTime, endTime ->
                        triggerDownload(
                            url = info.url,
                            title = info.title,
                            format = format,
                            thumbnail = info.thumbnail,
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
                    onRemoveQueued = { taskId ->
                        DownloadService.removeQueuedTask(context, taskId)
                    },
                    onNavigateHome = { currentTab = AppTab.HOME }
                )

                AppTab.LIBRARY -> LibraryTab(
                    history = historyList,
                    onPlay = { task ->
                        task.filePath?.let { uriStr ->
                            activePlaybackUri = uriStr
                            activePlaybackTitle = task.title
                            activePlaybackIsAudio = task.isAudio
                        }
                    },
                    onShare = { task ->
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
                    onDelete = { task ->
                        DownloadRepository.removeTask(task.id)
                    }
                )

                AppTab.SETTINGS -> SettingsTab()
            }

            // ═══════════════════════════════════════════════════════════════
            // FLOATING MINI-PLAYER & ACTIVE PROGRESS BAR
            // ═══════════════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                // Active Download Pill
                AnimatedVisibility(
                    visible = currentTask.status == DownloadStatus.DOWNLOADING,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { currentTab = AppTab.QUEUE }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceHighlight),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentTask.progress >= 0) {
                                    CircularProgressIndicator(
                                        progress = { currentTask.progress / 100f },
                                        modifier = Modifier.size(26.dp),
                                        color = TextCrisp,
                                        strokeWidth = 2.5.dp,
                                        trackColor = BorderHairline
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(26.dp),
                                        color = TextCrisp,
                                        strokeWidth = 2.5.dp
                                    )
                                }
                                Icon(
                                    if (currentTask.isAudio) Icons.Default.MusicNote else Icons.Default.Download,
                                    contentDescription = null,
                                    tint = TextCrisp,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentTask.title.ifBlank { "Downloading media..." },
                                    style = Typography.titleSmall,
                                    color = TextCrisp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (currentTask.progress >= 0) "${currentTask.progress}%" else formatByteSize(currentTask.bytesDownloaded),
                                        style = Typography.labelMedium,
                                        color = TextCrisp
                                    )
                                    if (currentTask.speed.isNotBlank()) {
                                        Text(
                                            text = " • ${currentTask.speed}",
                                            style = Typography.bodySmall,
                                            color = TextCrisp
                                        )
                                    }
                                    if (currentTask.bytesDownloaded > 0) {
                                        Text(
                                            text = " (${formatByteSize(currentTask.bytesDownloaded)}${if (currentTask.totalBytes > 0) " / " + formatByteSize(currentTask.totalBytes) else ""})",
                                            style = Typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                    if (currentTask.eta.isNotBlank()) {
                                        Text(
                                            text = " • ${currentTask.eta}",
                                            style = Typography.bodySmall,
                                            color = TextTertiary
                                        )
                                    }
                                    if (queuedTasks.isNotEmpty()) {
                                        Text(
                                            text = " (+${queuedTasks.size} queued)",
                                            style = Typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val cancelIntent = Intent(context, DownloadService::class.java).apply {
                                        action = DownloadService.ACTION_CANCEL_DOWNLOAD
                                    }
                                    context.startService(cancelIntent)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextTertiary)
                            }
                        }
                    }
                }

                // Completion Pill
                AnimatedVisibility(
                    visible = currentTask.status == DownloadStatus.SUCCESS,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = TextCrisp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Download Complete",
                                    style = Typography.titleSmall,
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
                            currentTask.filePath?.let { filePath ->
                                Button(
                                    onClick = {
                                        activePlaybackUri = filePath
                                        activePlaybackTitle = currentTask.title
                                        activePlaybackIsAudio = currentTask.isAudio
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
                            }
                        }
                    }
                }
            }
        }

        // Built-In Player Modal Sheet
        activePlaybackUri?.let { uri ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                BuiltInPlayerSheet(
                    mediaUri = uri,
                    title = activePlaybackTitle,
                    isAudio = activePlaybackIsAudio,
                    onDismiss = { activePlaybackUri = null }
                )
            }
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
    onInspect: () -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════
        // SUPPORTED PLATFORMS MARQUEE STRIP (WHEN NO LINK ACTIVE)
        // ═══════════════════════════════════════════════════════════════
        if (resolvedInfo == null && !isResolving) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "YouTube", "Instagram", "TikTok", "X / Twitter", "Spotify", "Facebook", "Reddit"
                ).forEach { platform ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(TextSecondary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = platform,
                                style = Typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SMART CLIPBOARD DETECTION PILL
        // ═══════════════════════════════════════════════════════════════
        if (!clipboardUrl.isNullOrBlank() && clipboardUrl != inputUrl && resolvedInfo == null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
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
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onUrlChange(clipboardUrl)
                            onClearClipboard()
                            onInspect()
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
        // SEAMLESS UNIFIED INPUT CAPSULE (56dp APPLE / SEAL STYLE)
        // ═══════════════════════════════════════════════════════════════
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (detectedPlatform != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceHighlight,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = detectedPlatform,
                            style = Typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextCrisp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 4.dp)
                    )
                }

                BasicTextField(
                    value = inputUrl,
                    onValueChange = onUrlChange,
                    modifier = Modifier.weight(1f),
                    textStyle = Typography.bodyLarge.copy(color = TextCrisp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (inputUrl.startsWith("http")) onInspect()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (inputUrl.isEmpty()) {
                            Text(
                                "Paste video or audio link...",
                                style = Typography.bodyLarge,
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
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextTertiary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (inputUrl.startsWith("http")) onInspect()
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextCrisp,
                            contentColor = CanvasDark
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Inspect", tint = CanvasDark, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            val clean = DownloadEngine.sanitizeUrl(clip)
                            if (clean.startsWith("http")) {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onUrlChange(clean)
                                onInspect()
                            } else {
                                Toast.makeText(context, "No link in clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceElevated,
                            contentColor = TextCrisp
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste", style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = TextCrisp)
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
                        onClick = onInspect,
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
                                            onClick = { onFormatSelect(fmt.id) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(56.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) TextCrisp else SurfaceElevated,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) TextCrisp else BorderHairline
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                                        text = if (fmt.sizeStr.isNotBlank()) fmt.sizeStr else fmt.note.ifBlank { fmt.ext.uppercase() },
                                                        style = Typography.bodySmall,
                                                        color = if (isSelected) CanvasDark.copy(alpha = 0.8f) else TextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (isSelected) {
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
                                        onCheckedChange = { isTrimming = it },
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

                        // High-Contrast Master Download CTA Button
                        val selectedFmt = info.availableFormats.find { it.id == selectedFormat }
                        val selectedLabel = selectedFmt?.label?.replace(" Video", "")?.replace(" Audio", "") ?: selectedFormat.uppercase()
                        val sizeText = if (!selectedFmt?.sizeStr.isNullOrBlank()) " • ${selectedFmt!!.sizeStr}" else ""

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                val start = if (isTrimming) trimStart.ifBlank { null } else null
                                val end = if (isTrimming) trimEnd.ifBlank { null } else null
                                onDownloadCustom(info, selectedFormat, start, end)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextCrisp,
                                contentColor = CanvasDark
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = CanvasDark, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTrimming) "Download Trimmed Clip" else "Download $selectedLabel$sizeText",
                                style = Typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = CanvasDark
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 2: ACTIVE QUEUE TAB (ZERO DUPLICATE HEADERS)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun QueueTab(
    currentTask: DownloadTask,
    queuedTasks: List<DownloadTask>,
    onCancelCurrent: () -> Unit,
    onRemoveQueued: (String) -> Unit,
    onNavigateHome: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Downloading Task Card
        if (currentTask.status == DownloadStatus.DOWNLOADING) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceHighlight,
                                modifier = Modifier.padding(end = 10.dp)
                            ) {
                                Text(
                                    text = if (currentTask.isAudio) "MP3" else "MP4",
                                    style = Typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextCrisp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = currentTask.title.ifBlank { "Downloading stream..." },
                                style = Typography.titleSmall,
                                color = TextCrisp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onCancelCurrent, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Cancel", tint = StatusError)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (currentTask.progress >= 0) {
                        LinearProgressIndicator(
                            progress = { currentTask.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = TextCrisp,
                            trackColor = SurfaceHighlight
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = TextCrisp,
                            trackColor = SurfaceHighlight
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentTask.progress >= 0) "${currentTask.progress}%" else formatByteSize(currentTask.bytesDownloaded),
                            style = Typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextCrisp
                        )
                        Text(
                            text = buildString {
                                if (currentTask.speed.isNotBlank()) {
                                    append(currentTask.speed)
                                    append(" • ")
                                }
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
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Queued Tasks Section
        if (queuedTasks.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Queued (${queuedTasks.size})",
                    style = Typography.titleSmall,
                    color = TextSecondary
                )

                queuedTasks.forEachIndexed { index, task ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SurfaceElevated,
                                modifier = Modifier.size(24.dp)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = Typography.titleSmall,
                                    color = TextCrisp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (task.isAudio) "Audio (MP3)" else "Video (Best)",
                                    style = Typography.bodySmall,
                                    color = TextTertiary
                                )
                            }
                            IconButton(
                                onClick = { onRemoveQueued(task.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextTertiary)
                            }
                        }
                    }
                }
            }
        }

        // Clean Empty State
        if (currentTask.status != DownloadStatus.DOWNLOADING && queuedTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No active downloads",
                        style = Typography.titleSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onNavigateHome,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextCrisp,
                            contentColor = CanvasDark
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = CanvasDark, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Download", style = Typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(76.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 3: REDESIGNED LIBRARY TAB (16:9 THUMBNAILS, SLEEK FILTER)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun LibraryTab(
    history: List<DownloadTask>,
    onPlay: (DownloadTask) -> Unit,
    onShare: (DownloadTask) -> Unit,
    onDelete: (DownloadTask) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("All") }

    val filteredHistory = remember(history, searchQuery, filterType) {
        history.filter { task ->
            val matchesSearch = searchQuery.isBlank() || task.title.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterType) {
                "Videos" -> !task.isAudio
                "Audio" -> task.isAudio
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (history.isNotEmpty()) {
            // Search Capsule
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search downloaded media...", style = Typography.bodyMedium, color = TextQuaternary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TextCrisp,
                    unfocusedBorderColor = BorderHairline,
                    focusedTextColor = TextCrisp,
                    unfocusedTextColor = TextCrisp,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("All", "Videos", "Audio").forEach { filter ->
                    val isSelected = filterType == filter
                    Surface(
                        onClick = { filterType = filter },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) TextCrisp else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) TextCrisp else BorderHairline
                        )
                    ) {
                        Text(
                            text = filter,
                            style = Typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) CanvasDark else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (filteredHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (history.isEmpty()) "Your library is empty" else "No matching downloads",
                        style = Typography.titleSmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredHistory, key = { it.id }) { task ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 16:9 Thumbnail with Play overlay
                            Box(
                                modifier = Modifier
                                    .width(76.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CanvasDark)
                                    .clickable { onPlay(task) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (task.thumbnail.isNotBlank()) {
                                    AsyncImage(
                                        model = task.thumbnail,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.35f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        if (task.isAudio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = TextCrisp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = Typography.titleSmall,
                                    color = TextCrisp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SurfaceHighlight
                                    ) {
                                        Text(
                                            text = if (task.isAudio) "MP3" else "MP4",
                                            color = TextCrisp,
                                            style = Typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (task.totalBytes > 0) {
                                        Text(
                                            text = formatByteSize(task.totalBytes),
                                            style = Typography.bodySmall,
                                            color = TextTertiary
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { onPlay(task) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = TextCrisp)
                                }
                                IconButton(onClick = { onShare(task) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSecondary)
                                }
                                IconButton(onClick = { onDelete(task) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextTertiary)
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

// ═══════════════════════════════════════════════════════════════════════
// TAB 4: REDESIGNED SETTINGS TAB (CLEAN SECTIONS, ZERO DUPLICATE TITLE)
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun SettingsTab() {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

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
                            color = if (testStatus?.startsWith("✓") == true) TextCrisp else TextTertiary
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

                // App version & branding
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Downloadivo Mobile", style = Typography.titleSmall, color = TextCrisp)
                        Text("v3.6.0 Studio Edition (4K & Pre-Size)", style = Typography.bodySmall, color = TextSecondary)
                    }
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(76.dp))
    }
}

private fun formatByteSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
