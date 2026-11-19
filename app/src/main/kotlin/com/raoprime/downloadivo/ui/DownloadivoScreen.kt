package com.raoprime.downloadivo.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.raoprime.downloadivo.model.DownloadStatus
import com.raoprime.downloadivo.model.MediaInfo
import com.raoprime.downloadivo.network.DownloadEngine
import com.raoprime.downloadivo.service.DownloadService
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

    var inputUrl by remember { mutableStateOf(sharedUrl ?: "") }
    var resolvedInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var selectedFormat by remember { mutableStateOf("best") }
    var isResolving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val downloadTask by DownloadService.currentTask.collectAsState()

    // Auto-resolve when URL is shared via Intent or pasted
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            inputUrl = sharedUrl
            isResolving = true
            errorMessage = null
            coroutineScope.launch {
                val res = DownloadEngine.fetchMediaInfo(sharedUrl)
                isResolving = false
                res.onSuccess { resolvedInfo = it }
                    .onFailure { errorMessage = it.message ?: "Failed to resolve link" }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Downloadivo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "PRO",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // ── URL INPUT CARD ───────────────────────────────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = {
                            inputUrl = it
                            errorMessage = null
                        },
                        placeholder = { Text("Paste video or audio link...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            if (inputUrl.isNotBlank()) {
                                IconButton(onClick = {
                                    inputUrl = ""
                                    resolvedInfo = null
                                    errorMessage = null
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
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
                                            res.onSuccess { resolvedInfo = it }
                                                .onFailure { errorMessage = it.message }
                                        }
                                    } else {
                                        Toast.makeText(context, "No URL in clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (inputUrl.isNotBlank()) {
                                    isResolving = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        val res = DownloadEngine.fetchMediaInfo(inputUrl)
                                        isResolving = false
                                        res.onSuccess { resolvedInfo = it }
                                            .onFailure { errorMessage = it.message }
                                    }
                                }
                            },
                            enabled = inputUrl.isNotBlank() && !isResolving,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching...")
                            } else {
                                Text("Inspect Link")
                            }
                        }
                    }
                }
            }

            // ── ERROR BANNER ─────────────────────────────────────────
            AnimatedVisibility(visible = errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // ── RESOLVED MEDIA CARD ──────────────────────────────────
            resolvedInfo?.let { info ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (info.thumbnail.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                AsyncImage(
                                    model = info.thumbnail,
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (info.duration.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.75f),
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
                            text = info.title.ifBlank { "Media File" },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (info.author.isNotBlank()) {
                            Text(
                                text = info.author,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Format Chips
                        Text(
                            text = "Select Format",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FormatChip("best", "Best Video", selectedFormat) { selectedFormat = it }
                            FormatChip("1080p", "1080p", selectedFormat) { selectedFormat = it }
                            FormatChip("720p", "720p", selectedFormat) { selectedFormat = it }
                            FormatChip("mp3", "320k MP3", selectedFormat) { selectedFormat = it }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Download Action Button
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
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start Download",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            // ── LIVE ACTIVE DOWNLOAD CARD ────────────────────────────
            if (downloadTask.status == DownloadStatus.DOWNLOADING) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = downloadTask.title.ifBlank { "Downloading media..." },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = downloadTask.speed.ifBlank { "Line-Rate" },
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
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
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = formatByteSize(downloadTask.bytesDownloaded),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // ── COMPLETED SUCCESS CARD ───────────────────────────────
            if (downloadTask.status == DownloadStatus.SUCCESS) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "⚡ Download Complete!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${downloadTask.title} is saved directly into your device Gallery & Movies folder.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatChip(
    key: String,
    label: String,
    currentSelected: String,
    onSelect: (String) -> Unit
) {
    val isSelected = currentSelected == key
    FilterChip(
        selected = isSelected,
        onClick = { onSelect(key) },
        label = { Text(label, fontSize = 12.sp) },
        shape = RoundedCornerShape(10.dp)
    )
}

private fun formatByteSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
