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
    QUEUE("Active"),
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

    // Built-in In-App Player State
    var activePlaybackUri by remember { mutableStateOf<String?>(null) }
    var activePlaybackTitle by remember { mutableStateOf("") }
    var activePlaybackIsAudio by remember { mutableStateOf(false) }

    val currentTask by DownloadService.currentTask.collectAsState()
    val queuedTasks by DownloadService.queuedTasks.collectAsState()
    val historyList by DownloadRepository.history.collectAsState()

    // Trigger auto-fill when sharedUrl is passed
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

    // Helper for starting download without jarring tab switch
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
                message = "⚡ Download started in background!",
                actionLabel = "View Queue",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                currentTab = AppTab.QUEUE
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 8.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SurfaceCardDark,
                    contentColor = TextPrimary,
                    actionColor = PrimaryEmerald,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo_white),
                            contentDescription = "Downloadivo",
                            modifier = Modifier.height(26.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PrimaryEmerald.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "PRO",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryEmerald
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { currentTab = AppTab.SETTINGS }) {
                        Icon(Icons.Default.Tune, contentDescription = "Settings", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark)
            ) {
                // ═══════════════════════════════════════════════════════════════
                // FLOATING MINI DOWNLOAD BAR (SPOTIFY / SEAL 10X STYLE)
                // ═══════════════════════════════════════════════════════════════
                AnimatedVisibility(
                    visible = currentTask.status == DownloadStatus.DOWNLOADING,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceCardDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.6f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clickable { currentTab = AppTab.QUEUE }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryEmerald.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = {
                                        if (currentTask.progress >= 0) currentTask.progress / 100f else 0f
                                    },
                                    modifier = Modifier.size(28.dp),
                                    color = PrimaryEmerald,
                                    strokeWidth = 3.dp,
                                    trackColor = CardBorderDark
                                )
                                Icon(
                                    if (currentTask.isAudio) Icons.Default.MusicNote else Icons.Default.Download,
                                    contentDescription = null,
                                    tint = PrimaryEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentTask.title.ifBlank { "Downloading media..." },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (currentTask.progress >= 0) "${currentTask.progress}%" else "Streaming",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald
                                    )
                                    if (currentTask.speed.isNotBlank()) {
                                        Text(
                                            text = " • ${currentTask.speed}",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                    if (currentTask.eta.isNotBlank()) {
                                        Text(
                                            text = " • ETA: ${currentTask.eta}",
                                            fontSize = 11.sp,
                                            color = TextSubtle
                                        )
                                    }
                                    if (queuedTasks.isNotEmpty()) {
                                        Text(
                                            text = " (+${queuedTasks.size} queued)",
                                            fontSize = 11.sp,
                                            color = PrimaryEmerald,
                                            fontWeight = FontWeight.SemiBold
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
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextMuted)
                            }
                        }
                    }
                }

                // Download Completed Mini-Alert
                AnimatedVisibility(
                    visible = currentTask.status == DownloadStatus.SUCCESS,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceCardDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = PrimaryEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Download Complete!",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = currentTask.title,
                                    fontSize = 11.sp,
                                    color = TextMuted,
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
                                        containerColor = PrimaryEmerald,
                                        contentColor = Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Standard 4-Tab Bottom Navigation Bar
                NavigationBar(
                    containerColor = SurfaceCardDark,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.HOME,
                        onClick = { currentTab = AppTab.HOME },
                        icon = { Icon(Icons.Default.Download, contentDescription = "Downloader") },
                        label = { Text("Download", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = PrimaryEmerald,
                            indicatorColor = PrimaryEmerald,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == AppTab.QUEUE,
                        onClick = { currentTab = AppTab.QUEUE },
                        icon = {
                            val activeBadgeCount = (if (currentTask.status == DownloadStatus.DOWNLOADING) 1 else 0) + queuedTasks.size
                            BadgedBox(badge = {
                                if (activeBadgeCount > 0) {
                                    Badge(containerColor = PrimaryEmerald) {
                                        Text("$activeBadgeCount", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Sync, contentDescription = "Queue")
                            }
                        },
                        label = { Text("Active", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = PrimaryEmerald,
                            indicatorColor = PrimaryEmerald,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == AppTab.LIBRARY,
                        onClick = { currentTab = AppTab.LIBRARY },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Library") },
                        label = { Text("Library", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = PrimaryEmerald,
                            indicatorColor = PrimaryEmerald,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == AppTab.SETTINGS,
                        onClick = { currentTab = AppTab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = PrimaryEmerald,
                            indicatorColor = PrimaryEmerald,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
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
                            url.contains("youtube") || url.contains("youtu.be") -> "YouTube Media"
                            url.contains("instagram.com") -> "Instagram Reel"
                            url.contains("tiktok.com") -> "TikTok Video"
                            url.contains("twitter.com") || url.contains("x.com") -> "X Video"
                            url.contains("spotify.com") -> "Spotify Track"
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
        }

        // ═══════════════════════════════════════════════════════════════════════
        // IN-APP BUILT-IN EXOPLAYER SHEET
        // ═══════════════════════════════════════════════════════════════════════
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
// TAB 1: HOME DOWNLOADER (SEAL 10X ZERO-FRICTION WORKFLOW)
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

    var activePlatformFilter by remember { mutableStateOf("All") }
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
            lower.contains("twitter.com") || lower.contains("x.com") -> "X / Twitter"
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Sleek Compact Header (Matching Website Downloadivo Brand)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon_white),
                    contentDescription = "Logo",
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Downloadivo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = PrimaryEmerald.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "SEAL 10X",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryEmerald
                        )
                    }
                }
                Text(
                    text = "Instant 8K/4K Video & Audio Downloader",
                    fontSize = 11.5.sp,
                    color = TextMuted
                )
            }
        }

        // 2. Horizontal Platform Pills (Brand Colors)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val platforms = listOf(
                "All" to TextPrimary,
                "YouTube" to BrandYouTube,
                "Instagram" to BrandInstagram,
                "TikTok" to BrandTikTok,
                "X / Twitter" to BrandTwitter,
                "Spotify" to BrandSpotify,
                "Facebook" to BrandFacebook,
                "Reddit" to BrandReddit
            )
            platforms.forEach { (name, color) ->
                val isSelected = activePlatformFilter == name
                Surface(
                    onClick = { activePlatformFilter = name },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) PrimaryEmerald.copy(alpha = 0.2f) else SurfaceCardDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) PrimaryEmerald else CardBorderDark
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        if (name != "All") {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            color = if (isSelected) PrimaryEmerald else TextMuted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Smart Clipboard Auto-Detection Card
        if (!clipboardUrl.isNullOrBlank() && clipboardUrl != inputUrl) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = PrimaryEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Link detected in clipboard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = clipboardUrl,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onUrlChange(clipboardUrl)
                            onClearClipboard()
                            onInstantDownload(clipboardUrl, "best")
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryEmerald,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("⚡ Video", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = onClearClipboard,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSubtle)
                    }
                }
            }
        }

        // 4. Central Input Card + INSTANT 1-TAP DOWNLOAD BUTTONS (ZERO FRICTION)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = onUrlChange,
                    placeholder = {
                        Text("Paste video, reel, or audio link...", color = TextSubtle, fontSize = 13.5.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    leadingIcon = {
                        if (detectedPlatform != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PrimaryEmerald.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f)),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = detectedPlatform,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryEmerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Icon(Icons.Default.Link, contentDescription = null, tint = TextSubtle, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingIcon = {
                        if (inputUrl.isNotBlank()) {
                            IconButton(onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onClear()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                            }
                        } else {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                val clean = DownloadEngine.sanitizeUrl(clip)
                                if (clean.startsWith("http")) {
                                    onUrlChange(clean)
                                } else {
                                    Toast.makeText(context, "No link in clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = PrimaryEmerald)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryEmerald,
                        unfocusedBorderColor = CardBorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = PrimaryEmerald
                    )
                )

                // ⚡ 1-TAP ZERO FRICTION PRIMARY ACTION BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Instant Video Button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (inputUrl.isNotBlank()) {
                                onInstantDownload(inputUrl, "best")
                            } else {
                                Toast.makeText(context, "Please paste a media URL first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryEmerald,
                            contentColor = Color.Black
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Instant Video", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }

                    // Instant Audio Button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (inputUrl.isNotBlank()) {
                                onInstantDownload(inputUrl, "mp3")
                            } else {
                                Toast.makeText(context, "Please paste a media URL first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceCardDark,
                            contentColor = PrimaryEmerald
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, PrimaryEmerald)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = PrimaryEmerald, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Instant Audio", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }

                // ⚙️ Customization Toggle
                Surface(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onToggleCustomize()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = BackgroundDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isCustomizeExpanded) Icons.Default.ExpandLess else Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isCustomizeExpanded) PrimaryEmerald else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCustomizeExpanded) "Hide Options" else "Choose Resolution, 4K or Trim Clip",
                                fontSize = 12.sp,
                                color = if (isCustomizeExpanded) PrimaryEmerald else TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryEmerald
                            )
                        } else {
                            Text(
                                text = if (resolvedInfo != null) "Inspected" else "Options",
                                fontSize = 11.sp,
                                color = TextSubtle
                            )
                        }
                    }
                }
            }
        }

        // Error Banner
        AnimatedVisibility(visible = errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ErrorContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = ErrorRed)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = TextPrimary,
                        fontSize = 12.5.sp
                    )
                }
            }
        }

        // 5. Expandable Customization Card (Resolutions, Trimmer, Custom Settings)
        AnimatedVisibility(
            visible = isCustomizeExpanded && (resolvedInfo != null || isResolving),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            if (isResolving) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SurfaceCardDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
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
                            color = PrimaryEmerald,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Analyzing link metadata...", fontSize = 13.sp, color = TextPrimary)
                    }
                }
            } else {
                resolvedInfo?.let { info ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Thumbnail Preview
                            if (info.thumbnail.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(BackgroundDark)
                                        .border(1.dp, CardBorderDark, RoundedCornerShape(14.dp))
                                ) {
                                    AsyncImage(
                                        model = info.thumbnail,
                                        contentDescription = "Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = info.extractor,
                                            color = PrimaryEmerald,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (info.duration.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = info.duration,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Text(
                                text = info.title.ifBlank { "Media Stream" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (info.author.isNotBlank()) {
                                Text(
                                    text = "Creator: ${info.author}",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Mode Switcher: Video vs Audio
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BackgroundDark)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    onClick = { mediaMode = "video"; onFormatSelect("best") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (mediaMode == "video") PrimaryEmerald else Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 7.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = if (mediaMode == "video") Color.Black else TextMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Video (MP4)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (mediaMode == "video") Color.Black else TextMuted
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { mediaMode = "audio"; onFormatSelect("mp3") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (mediaMode == "audio") PrimaryEmerald else Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 7.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = if (mediaMode == "audio") Color.Black else TextMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Audio (MP3)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (mediaMode == "audio") Color.Black else TextMuted
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Resolution matrix
                            Text(
                                text = if (mediaMode == "video") "SELECT VIDEO RESOLUTION" else "SELECT AUDIO BITRATE",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSubtle,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

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

                            Spacer(modifier = Modifier.height(12.dp))

                            // Clip Trimmer
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BackgroundDark,
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCut,
                                                contentDescription = null,
                                                tint = PrimaryEmerald,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Trim Media Segment",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.5.sp,
                                                color = TextPrimary
                                            )
                                        }
                                        Switch(
                                            checked = isTrimming,
                                            onCheckedChange = { isTrimming = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = PrimaryEmerald
                                            )
                                        )
                                    }
                                    if (isTrimming) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = trimStart,
                                                onValueChange = { trimStart = it },
                                                placeholder = { Text("Start: 00:00", fontSize = 11.sp, color = TextSubtle) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = PrimaryEmerald,
                                                    unfocusedBorderColor = CardBorderDark,
                                                    focusedTextColor = TextPrimary,
                                                    unfocusedTextColor = TextPrimary
                                                )
                                            )
                                            OutlinedTextField(
                                                value = trimEnd,
                                                onValueChange = { trimEnd = it },
                                                placeholder = { Text("End: 01:00", fontSize = 11.sp, color = TextSubtle) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = PrimaryEmerald,
                                                    unfocusedBorderColor = CardBorderDark,
                                                    focusedTextColor = TextPrimary,
                                                    unfocusedTextColor = TextPrimary
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Download with Custom Settings Button
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
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryEmerald,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTrimming) "Download Trimmed Clip" else "Download with Selected Settings",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 2: ACTIVE QUEUE (WITH LIVE SPEEDOMETER & MULTI-ITEM FIFO QUEUE)
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
                text = "Active Downloads",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (currentTask.status == DownloadStatus.DOWNLOADING) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryEmerald.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = currentTask.speed.ifBlank { "Line-Rate" },
                        color = PrimaryEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Active Downloading Task Card
        if (currentTask.status == DownloadStatus.DOWNLOADING) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.5f)),
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
                                color = PrimaryEmerald.copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = if (currentTask.isAudio) "MP3" else "MP4",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryEmerald,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = currentTask.title.ifBlank { "Downloading stream..." },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onCancelCurrent, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Cancel", tint = ErrorRed)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (currentTask.progress >= 0) {
                        LinearProgressIndicator(
                            progress = { currentTask.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = PrimaryEmerald,
                            trackColor = CardBorderDark
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = PrimaryEmerald,
                            trackColor = CardBorderDark
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (currentTask.progress >= 0) "${currentTask.progress}%" else "Chunk Stream...",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                        Text(
                            text = buildString {
                                append(formatByteSize(currentTask.bytesDownloaded))
                                append(" / ")
                                append(if (currentTask.totalBytes > 0) formatByteSize(currentTask.totalBytes) else "Stream")
                                if (currentTask.eta.isNotBlank()) {
                                    append(" • ")
                                    append(currentTask.eta)
                                }
                            },
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Queued Tasks Section (Up Next in Queue)
        if (queuedTasks.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "UP NEXT IN QUEUE (${queuedTasks.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSubtle,
                    letterSpacing = 0.5.sp
                )

                queuedTasks.forEachIndexed { index, task ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCardDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = BackgroundDark,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "#${index + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryEmerald
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (task.isAudio) "Audio (MP3) • Waiting in queue" else "Video (Best) • Waiting in queue",
                                    fontSize = 11.sp,
                                    color = TextSubtle
                                )
                            }
                            IconButton(
                                onClick = { onRemoveQueued(task.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextSubtle)
                            }
                        }
                    }
                }
            }
        }

        // Empty State (Neither active nor queued)
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
                        tint = TextSubtle,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No active downloads in queue",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onNavigateHome,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = PrimaryEmerald.copy(alpha = 0.2f),
                            contentColor = PrimaryEmerald
                        )
                    ) {
                        Text("Add New Download", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 3: LIBRARY / HISTORY
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
                text = "Download Library (${history.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear All", color = ErrorRed, fontSize = 12.sp)
                }
            }
        }

        if (history.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search downloaded media...", fontSize = 13.sp, color = TextSubtle) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSubtle) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSubtle)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryEmerald,
                    unfocusedBorderColor = CardBorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
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
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) PrimaryEmerald.copy(alpha = 0.2f) else SurfaceCardDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) PrimaryEmerald else CardBorderDark
                        )
                    ) {
                        Text(
                            text = filter,
                            fontSize = 12.sp,
                            color = if (isSelected) PrimaryEmerald else TextMuted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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
                        tint = TextSubtle,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (history.isEmpty()) "Your library is empty" else "No matching downloads found",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredHistory, key = { it.id }) { task ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
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
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BackgroundDark)
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
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        if (task.isAudio) Icons.Default.MusicNote else Icons.Default.SmartDisplay,
                                        contentDescription = null,
                                        tint = PrimaryEmerald
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
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
                                        color = if (task.isAudio) PrimaryEmerald.copy(alpha = 0.15f) else Color(0xFF3B82F6).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (task.isAudio) "MP3" else "MP4",
                                            color = if (task.isAudio) PrimaryEmerald else Color(0xFF60A5FA),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    if (task.totalBytes > 0) {
                                        Text(
                                            text = formatByteSize(task.totalBytes),
                                            fontSize = 11.sp,
                                            color = TextSubtle
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { onPlay(task) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = PrimaryEmerald)
                                }
                                IconButton(onClick = { onShare(task) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = TextMuted)
                                }
                                IconButton(onClick = { onDelete(task) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextSubtle)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TAB 4: SETTINGS & ENGINE
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
            text = "Settings & Engine",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "DOWNLOAD ENGINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSubtle,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Downloadivo Cloud Turbo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("Line-rate edge streaming & 8K/4K proxy", fontSize = 12.sp, color = TextMuted)
                    }
                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = PrimaryEmerald
                        )
                    )
                }

                HorizontalDivider(color = CardBorderDark)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backend Server Endpoint", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
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
                            focusedBorderColor = PrimaryEmerald,
                            unfocusedBorderColor = CardBorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = testStatus ?: "Production: https://downloadivo.vercel.app",
                            fontSize = 11.5.sp,
                            color = if (testStatus?.startsWith("✓") == true) PrimaryEmerald else TextSubtle
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
                                            testStatus = "✗ Connection Failed"
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
                            Text(if (isTesting) "..." else "Test Connection", fontSize = 11.5.sp)
                        }
                    }
                }

                HorizontalDivider(color = CardBorderDark)

                Column {
                    Text("Storage Directories", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Videos: Movies/Downloadivo", fontSize = 12.sp, color = TextMuted)
                    Text("• Audio: Music/Downloadivo", fontSize = 12.sp, color = TextMuted)
                }

                HorizontalDivider(color = CardBorderDark)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Downloadivo Mobile Version", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                        Text("v3.2.0 (FIFO Queue & Zero-Glitch)", fontSize = 12.sp, color = PrimaryEmerald)
                    }
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
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
        color = if (isSelected) PrimaryEmerald.copy(alpha = 0.15f) else SurfaceCardDark,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) PrimaryEmerald else CardBorderDark
        ),
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PrimaryEmerald else TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
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
