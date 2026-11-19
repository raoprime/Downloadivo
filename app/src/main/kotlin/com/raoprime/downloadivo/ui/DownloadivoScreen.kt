package com.raoprime.downloadivo.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.raoprime.downloadivo.model.DownloadStatus
import com.raoprime.downloadivo.model.MediaInfo
import com.raoprime.downloadivo.network.DownloadEngine
import com.raoprime.downloadivo.service.DownloadService
import com.raoprime.downloadivo.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadivoScreen(
    sharedUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var inputUrl by remember { mutableStateOf("") }
    var resolvedInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var selectedFormat by remember { mutableStateOf("best") }
    var isResolving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Seal-style Quick Share BottomSheet State
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val downloadTask by DownloadService.currentTask.collectAsState()

    // Auto-trigger Seal-style Quick BottomSheet when URL is shared via Android Intent
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            inputUrl = sharedUrl
            isResolving = true
            errorMessage = null
            showBottomSheet = true
            coroutineScope.launch {
                val res = DownloadEngine.fetchMediaInfo(sharedUrl)
                isResolving = false
                res.onSuccess {
                    resolvedInfo = it
                }.onFailure {
                    errorMessage = it.message ?: "Failed to resolve media info"
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Downloadivo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryEmerald.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "PRO",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryEmerald
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── HERO LOGO & TITLE (Website Matched) ──────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCardDark)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Logo",
                        tint = PrimaryEmerald,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Universal Downloader",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "High-speed line-rate media extractor",
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Platform tags matching website
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlatformPill("YouTube")
                    PlatformPill("Instagram")
                    PlatformPill("X / Twitter")
                    PlatformPill("TikTok")
                    PlatformPill("Spotify")
                }
            }

            // ── CENTRAL INPUT CARD (Exact Website & Seal Parity) ────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = {
                            inputUrl = it
                            errorMessage = null
                        },
                        placeholder = {
                            Text("Paste video, reel, or audio link...", color = TextSubtle, fontSize = 14.sp)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryEmerald,
                            unfocusedBorderColor = CardBorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryEmerald
                        ),
                        trailingIcon = {
                            if (inputUrl.isNotBlank()) {
                                IconButton(onClick = {
                                    inputUrl = ""
                                    resolvedInfo = null
                                    errorMessage = null
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                }
                            } else {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    if (clip.startsWith("http")) {
                                        inputUrl = clip
                                        coroutineScope.launch {
                                            isResolving = true
                                            val res = DownloadEngine.fetchMediaInfo(clip)
                                            isResolving = false
                                            res.onSuccess {
                                                resolvedInfo = it
                                                showBottomSheet = true
                                            }.onFailure {
                                                errorMessage = it.message
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "No link in clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = PrimaryEmerald)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (inputUrl.isNotBlank()) {
                                    isResolving = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        val res = DownloadEngine.fetchMediaInfo(inputUrl)
                                        isResolving = false
                                        res.onSuccess {
                                            resolvedInfo = it
                                            showBottomSheet = true
                                        }.onFailure {
                                            errorMessage = it.message
                                        }
                                    }
                                }
                            },
                            enabled = inputUrl.isNotBlank() && !isResolving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryEmerald,
                                contentColor = Color.Black,
                                disabledContainerColor = CardBorderDark,
                                disabledContentColor = TextSubtle
                            )
                        ) {
                            if (isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Inspecting...", fontWeight = FontWeight.Bold)
                            } else {
                                Text("Inspect Link", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── ERROR BANNER ─────────────────────────────────────────────────
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
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = ErrorRed
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // ── RESOLVED MEDIA CARD (Synchronized Preview) ───────────────────
            resolvedInfo?.let { info ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (info.thumbnail.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BackgroundDark)
                            ) {
                                AsyncImage(
                                    model = info.thumbnail,
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
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
                                            fontWeight = FontWeight.Medium,
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
                            fontSize = 15.sp,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (info.author.isNotBlank()) {
                            Text(
                                text = info.author,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Format Selector Pills
                        Text(
                            text = "SELECT QUALITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSubtle,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SyncedFormatChip("best", "Best Video", selectedFormat) { selectedFormat = it }
                            SyncedFormatChip("1080p", "1080p HD", selectedFormat) { selectedFormat = it }
                            SyncedFormatChip("720p", "720p", selectedFormat) { selectedFormat = it }
                            SyncedFormatChip("mp3", "320k MP3", selectedFormat) { selectedFormat = it }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Big Download Action Button
                        Button(
                            onClick = {
                                DownloadService.startDownload(
                                    context = context,
                                    url = info.url,
                                    title = info.title,
                                    format = selectedFormat
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryEmerald,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Download Stream",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            // ── LIVE ACTIVE DOWNLOAD CARD (Line-Rate Speed & Notification Sync) ──
            if (downloadTask.status == DownloadStatus.DOWNLOADING) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = downloadTask.title.ifBlank { "Downloading stream..." },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PrimaryEmerald.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = downloadTask.speed.ifBlank { "Line-Rate" },
                                    color = PrimaryEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    val cancelIntent = android.content.Intent(context, DownloadService::class.java).apply {
                                        action = DownloadService.ACTION_CANCEL_DOWNLOAD
                                    }
                                    context.startService(cancelIntent)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Cancel Download",
                                    tint = ErrorRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (downloadTask.progress >= 0) {
                            LinearProgressIndicator(
                                progress = { downloadTask.progress / 100f },
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (downloadTask.progress >= 0) "${downloadTask.progress}%" else "Streaming chunk...",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Text(
                                text = formatByteSize(downloadTask.bytesDownloaded),
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // ── COMPLETED SUCCESS CARD ───────────────────────────────────────
            if (downloadTask.status == DownloadStatus.SUCCESS) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryEmerald),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "⚡ Download Complete!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PrimaryEmerald
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${downloadTask.title} is saved directly to your Movies & Music folder.",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    downloadTask.filePath?.let { uriStr ->
                                        try {
                                            val playIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(
                                                    android.net.Uri.parse(uriStr),
                                                    if (downloadTask.format == "mp3") "audio/*" else "video/*"
                                                )
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(playIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No media player found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryEmerald,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SmartDisplay, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play Media", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ── SEAL-STYLE MODAL BOTTOM SHEET (When Shared via Android Intent) ──
        if (showBottomSheet && (resolvedInfo != null || isResolving)) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = SurfaceCardDark,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TextSubtle)
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .padding(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (isResolving) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PrimaryEmerald)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Analyzing media stream...", fontSize = 14.sp, color = TextPrimary)
                            }
                        }
                    } else {
                        resolvedInfo?.let { info ->
                            // Thumbnail Preview
                            if (info.thumbnail.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BackgroundDark)
                                ) {
                                    AsyncImage(
                                        model = info.thumbnail,
                                        contentDescription = "Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
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
                            }

                            // Title & Author
                            Text(
                                text = info.title.ifBlank { "Media Stream" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (info.author.isNotBlank()) {
                                Text(
                                    text = info.author,
                                    fontSize = 13.sp,
                                    color = TextMuted
                                )
                            }

                            // Format Selector Chips
                            Text(
                                text = "SELECT QUALITY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSubtle,
                                letterSpacing = 0.5.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SyncedFormatChip("best", "Best Video", selectedFormat) { selectedFormat = it }
                                SyncedFormatChip("1080p", "1080p HD", selectedFormat) { selectedFormat = it }
                                SyncedFormatChip("720p", "720p", selectedFormat) { selectedFormat = it }
                                SyncedFormatChip("mp3", "320k MP3", selectedFormat) { selectedFormat = it }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // One-Tap Download Button
                            Button(
                                onClick = {
                                    DownloadService.startDownload(
                                        context = context,
                                        url = info.url,
                                        title = info.title,
                                        format = selectedFormat
                                    )
                                    showBottomSheet = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryEmerald,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Start Download",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformPill(name: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = SurfaceCardDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            color = TextSubtle,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SyncedFormatChip(
    key: String,
    label: String,
    currentSelected: String,
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
        )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PrimaryEmerald else TextMuted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
