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
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Download started in background",
                actionLabel = "View",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                currentTab = AppTab.QUEUE
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CanvasDark,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 8.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SurfaceElevated,
                    contentColor = TextCrisp,
                    actionColor = AccentEmerald,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_white),
                        contentDescription = "Downloadivo",
                        modifier = Modifier.height(24.dp),
                        contentScale = ContentScale.Fit
                    )
                },
                actions = {
                    IconButton(onClick = { currentTab = AppTab.SETTINGS }) {
                        Icon(Icons.Default.Tune, contentDescription = "Settings", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CanvasDark
                )
            )
        },
        bottomBar = {
            // Stable 4-Tab Bottom Navigation Bar (Fixed Height, Zero Jitter)
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
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentEmerald,
                        indicatorColor = AccentEmerald,
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
                                Badge(containerColor = AccentEmerald) {
                                    Text("$activeCount", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Sync, contentDescription = "Queue")
                        }
                    },
                    label = { Text("Queue", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentEmerald,
                        indicatorColor = AccentEmerald,
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
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentEmerald,
                        indicatorColor = AccentEmerald,
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
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentEmerald,
                        indicatorColor = AccentEmerald,
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
                    isCustomizeExpanded = isCustomizeExpanded,
                    onToggleCustomize = {
                        isCustomizeExpanded = !isCustomizeExpanded
                        if (isCustomizeExpanded && resolvedInfo == null && inputUrl.isNotBlank()) {
                            isResolving = true
                            errorMessage = null
                            coroutineScope.launch {
                                val res = DownloadEngine.fetchMediaInfo(inputUrl)
                                isResolving = false
                                res.onSuccess { resolvedInfo = it }.onFailure { errorMessage = it.message }
                            }
                        }
                    },
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
                    },
                    onClearAll = {
                        DownloadRepository.clearHistory()
                    }
                )

                AppTab.SETTINGS -> SettingsTab()
            }

            // ═══════════════════════════════════════════════════════════════
            // FLOATING DYNAMIC MINI-BAR & NOTIFICATION (ABOVE BOTTOM NAV)
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
                                    .background(AccentEmeraldMuted),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentTask.progress >= 0) {
                                    CircularProgressIndicator(
                                        progress = { currentTask.progress / 100f },
                                        modifier = Modifier.size(26.dp),
                                        color = AccentEmerald,
                                        strokeWidth = 2.5.dp,
                                        trackColor = BorderHairline
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(26.dp),
                                        color = AccentEmerald,
                                        strokeWidth = 2.5.dp
                                    )
                                }
                                Icon(
                                    if (currentTask.isAudio) Icons.Default.MusicNote else Icons.Default.Download,
                                    contentDescription = null,
                                    tint = AccentEmerald,
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
                                        color = AccentEmerald
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
                                            color = AccentEmerald
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.5f)),
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
                                tint = AccentEmerald,
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
                                        containerColor = AccentEmerald,
                                        contentColor = Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play", style = Typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Built-In Player Sheet
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
// TAB 1: REDESIGNED FLUID HOME DOWNLOADER
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
    isCustomizeExpanded: Boolean,
    onToggleCustomize: () -> Unit,
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
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════
        // HERO BRANDING (MATCHES OFFICIAL WEBSITE DESIGN)
        // ═══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = resolvedInfo == null && !isResolving,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderHairline, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_white),
                        contentDescription = "Downloadivo",
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Downloadivo",
                    style = Typography.displayLarge,
                    color = TextCrisp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Universal Media Studio",
                    style = Typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // SMART CLIPBOARD DETECTION PILL
        // ═══════════════════════════════════════════════════════════════
        if (!clipboardUrl.isNullOrBlank() && clipboardUrl != inputUrl) {
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
                        tint = AccentEmerald,
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
                            containerColor = AccentEmerald,
                            contentColor = Color.Black
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
        // CENTRAL UNIFIED INPUT CARD (EXACT WEBSITE STYLING)
        // ═══════════════════════════════════════════════════════════════
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = onUrlChange,
                    placeholder = {
                        Text("Paste link to download...", color = TextQuaternary, fontSize = 14.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (inputUrl.startsWith("http")) {
                                onInspect()
                            }
                        }
                    ),
                    leadingIcon = {
                        if (detectedPlatform != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = detectedPlatform,
                                    style = Typography.labelSmall,
                                    color = AccentEmerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Icon(Icons.Default.Link, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingIcon = {
                        if (inputUrl.isNotBlank()) {
                            IconButton(onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onClear()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                            }
                        } else {
                            IconButton(onClick = {
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
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = AccentEmerald)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = BorderHairline,
                        focusedTextColor = TextCrisp,
                        unfocusedTextColor = TextCrisp,
                        cursorColor = AccentEmerald
                    )
                )

                // Master Dual Actions: Batch Mode vs Single Mode
                if (resolvedInfo == null && !isResolving) {
                    val detectedUrls = remember(inputUrl) { DownloadEngine.extractAllUrls(inputUrl) }
                    if (detectedUrls.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.35f)),
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
                                            tint = AccentEmerald,
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
                                        color = AccentEmeraldMuted
                                    ) {
                                        Text(
                                            text = "${detectedUrls.size} LINKS",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentEmerald,
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
                                            containerColor = AccentEmerald,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("All Videos (${detectedUrls.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("All MP3s (${detectedUrls.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextCrisp)
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    if (inputUrl.isNotBlank()) {
                                        onInstantDownload(inputUrl, "best")
                                    } else {
                                        Toast.makeText(context, "Please paste a link first", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentEmerald,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download Video", style = Typography.labelLarge, color = Color.Black)
                            }

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    if (inputUrl.isNotBlank()) {
                                        onInstantDownload(inputUrl, "mp3")
                                    } else {
                                        Toast.makeText(context, "Please paste a link first", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceElevated,
                                    contentColor = TextCrisp
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download Audio", style = Typography.labelLarge, color = TextCrisp)
                            }
                        }

                        // Inspect Details Trigger
                        TextButton(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (inputUrl.isNotBlank()) {
                                    onInspect()
                                } else {
                                    Toast.makeText(context, "Please paste a link first", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Inspect Available Resolutions & 4K Options", style = Typography.bodySmall, color = AccentEmerald)
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // ERROR BANNER WITH RETRY
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
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = AccentEmerald)
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // RESOLVING STATE (SLEEK PULSE CARD)
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
                        color = AccentEmerald,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Analyzing media streams...", style = Typography.bodyMedium, color = TextCrisp, fontWeight = FontWeight.Medium)
                    Text("Extracting 1080p, 4K & 320k audio tracks", style = Typography.bodySmall, color = TextTertiary)
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // CINEMATIC PREVIEW & FORMAT SELECTOR (ALWAYS VISIBLE WHEN LOADED)
        // ═══════════════════════════════════════════════════════════════
        resolvedInfo?.let { info ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Compact Horizontal Preview (Seal-Style Precision Layout)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CanvasDark)
                            .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (info.thumbnail.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceDark)
                            ) {
                                AsyncImage(
                                    model = info.thumbnail,
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (info.duration.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.Black.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(3.dp)
                                    ) {
                                        Text(
                                            text = info.duration,
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AccentEmeraldMuted,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = info.extractor.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentEmerald,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = info.title.ifBlank { "Media Stream" },
                                style = Typography.titleSmall,
                                color = TextCrisp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (info.author.isNotBlank()) {
                                Text(
                                    text = info.author,
                                    style = Typography.bodySmall,
                                    color = TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextTertiary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mode Segmented Switch: Video vs Audio
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CanvasDark)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { mediaMode = "video"; onFormatSelect("best") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = if (mediaMode == "video") SurfaceElevated else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = if (mediaMode == "video") AccentEmerald else TextTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Video",
                                    style = Typography.labelMedium,
                                    color = if (mediaMode == "video") TextCrisp else TextTertiary,
                                    fontWeight = if (mediaMode == "video") FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                        Surface(
                            onClick = { mediaMode = "audio"; onFormatSelect("mp3") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = if (mediaMode == "audio") SurfaceElevated else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (mediaMode == "audio") AccentEmerald else TextTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Audio",
                                    style = Typography.labelMedium,
                                    color = if (mediaMode == "audio") TextCrisp else TextTertiary,
                                    fontWeight = if (mediaMode == "audio") FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quality & Resolution Matrix
                    Text(
                        text = if (mediaMode == "video") "Select resolution" else "Select audio format",
                        style = Typography.labelSmall,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (mediaMode == "video") {
                        val videoFormats = info.availableFormats.filter { !it.isAudio }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            videoFormats.chunked(3).forEach { rowList ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowList.forEach { fmt ->
                                        SyncedChip(
                                            key = fmt.id,
                                            label = fmt.label.replace(" Video", "").replace(" Quality", ""),
                                            currentSelected = selectedFormat,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            onFormatSelect(it)
                                        }
                                    }
                                    for (i in 0 until (3 - rowList.size)) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        val audioFormats = info.availableFormats.filter { it.isAudio }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            audioFormats.forEach { fmt ->
                                SyncedChip(
                                    key = fmt.id,
                                    label = fmt.label,
                                    currentSelected = selectedFormat,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    onFormatSelect(it)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Collapsible Precision Clip Trimmer
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
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Precision Clip Trimmer",
                                        style = Typography.titleSmall,
                                        color = TextCrisp
                                    )
                                }
                                Switch(
                                    checked = isTrimming,
                                    onCheckedChange = { isTrimming = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = AccentEmerald
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
                                            focusedBorderColor = AccentEmerald,
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
                                            focusedBorderColor = AccentEmerald,
                                            unfocusedBorderColor = BorderHairline,
                                            focusedTextColor = TextCrisp,
                                            unfocusedTextColor = TextCrisp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Master Download Action Button
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
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentEmerald,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val selectedLabel = info.availableFormats.find { it.id == selectedFormat }?.label ?: selectedFormat.uppercase()
                        Text(
                            text = if (isTrimming) "Download Trimmed Clip" else "Download $selectedLabel",
                            style = Typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // Padding at bottom to ensure no overlap with floating mini-player
        Spacer(modifier = Modifier.height(72.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 2: ACTIVE QUEUE
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
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloads",
                style = Typography.titleLarge,
                color = TextCrisp
            )
            if (currentTask.status == DownloadStatus.DOWNLOADING) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = AccentEmeraldMuted,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = currentTask.speed.ifBlank { "Active" },
                        color = AccentEmerald,
                        style = Typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

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
                                shape = RoundedCornerShape(4.dp),
                                color = AccentEmeraldMuted,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = if (currentTask.isAudio) "MP3" else "MP4",
                                    style = Typography.labelSmall,
                                    color = AccentEmerald,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentEmerald,
                            trackColor = BorderHairline
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentEmerald,
                            trackColor = BorderHairline
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentTask.progress >= 0) "${currentTask.progress}%" else formatByteSize(currentTask.bytesDownloaded),
                            style = Typography.titleSmall,
                            color = AccentEmerald
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
                            color = TextCrisp
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
                    text = "Queue (${queuedTasks.size})",
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = CanvasDark,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        style = Typography.labelSmall,
                                        color = AccentEmerald
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
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No active downloads",
                        style = Typography.titleSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    FilledTonalButton(
                        onClick = onNavigateHome,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SurfaceElevated,
                            contentColor = TextCrisp
                        )
                    ) {
                        Text("Add new download", style = Typography.labelMedium)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(76.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 3: REDESIGNED LIBRARY
// ═══════════════════════════════════════════════════════════════════════
@Composable
private fun LibraryTab(
    history: List<DownloadTask>,
    onPlay: (DownloadTask) -> Unit,
    onShare: (DownloadTask) -> Unit,
    onDelete: (DownloadTask) -> Unit,
    onClearAll: () -> Unit
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
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library (${history.size})",
                style = Typography.titleLarge,
                color = TextCrisp
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear All", color = StatusError, style = Typography.labelMedium)
                }
            }
        }

        if (history.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search library...", style = Typography.bodyMedium, color = TextQuaternary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextTertiary) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextTertiary)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentEmerald,
                    unfocusedBorderColor = BorderHairline,
                    focusedTextColor = TextCrisp,
                    unfocusedTextColor = TextCrisp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("All", "Videos", "Audio").forEach { filter ->
                    val isSelected = filterType == filter
                    Surface(
                        onClick = { filterType = filter },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) SurfaceHighlight else SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AccentEmerald else BorderHairline
                        )
                    ) {
                        Text(
                            text = filter,
                            style = Typography.labelMedium,
                            color = if (isSelected) TextCrisp else TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
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
                        modifier = Modifier.size(48.dp)
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
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
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
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        if (task.isAudio) Icons.Default.MusicNote else Icons.Default.SmartDisplay,
                                        contentDescription = null,
                                        tint = AccentEmerald
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
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (task.isAudio) AccentEmeraldMuted else Color(0x263B82F6)
                                    ) {
                                        Text(
                                            text = if (task.isAudio) "MP3" else "MP4",
                                            color = if (task.isAudio) AccentEmerald else Color(0xFF60A5FA),
                                            style = Typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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
                                IconButton(onClick = { onPlay(task) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = AccentEmerald)
                                }
                                IconButton(onClick = { onShare(task) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSecondary)
                                }
                                IconButton(onClick = { onDelete(task) }, modifier = Modifier.size(34.dp)) {
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
// TAB 4: REDESIGNED SETTINGS
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
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = Typography.titleLarge,
            color = TextCrisp
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cloud Turbo", style = Typography.titleSmall, color = TextCrisp)
                        Text("Line-rate edge acceleration", style = Typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = AccentEmerald
                        )
                    )
                }

                HorizontalDivider(color = BorderHairline)

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
                            focusedBorderColor = AccentEmerald,
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
                            color = if (testStatus?.startsWith("✓") == true) AccentEmerald else TextTertiary
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

                Column {
                    Text("Storage location", style = Typography.titleSmall, color = TextCrisp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Movies/Downloadivo & Music/Downloadivo", style = Typography.bodySmall, color = TextSecondary)
                }

                HorizontalDivider(color = BorderHairline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Downloadivo Mobile", style = Typography.titleSmall, color = TextCrisp)
                        Text("v3.5.0 Monochrome Edition", style = Typography.bodySmall, color = TextCrisp)
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

@Composable
private fun SyncedChip(
    key: String,
    label: String,
    currentSelected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    val isSelected = currentSelected == key
    Surface(
        onClick = { onSelect(key) },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) AccentEmeraldMuted else SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) AccentEmerald else BorderHairline
        ),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = Typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) AccentEmerald else TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
        )
    }
}

private fun formatByteSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
