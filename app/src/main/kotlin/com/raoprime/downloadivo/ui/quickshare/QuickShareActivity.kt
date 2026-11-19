package com.raoprime.downloadivo.ui.quickshare

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.raoprime.downloadivo.MainActivity
import com.raoprime.downloadivo.R
import com.raoprime.downloadivo.model.MediaInfo
import com.raoprime.downloadivo.network.DownloadEngine
import com.raoprime.downloadivo.service.DownloadService
import com.raoprime.downloadivo.ui.theme.*
import kotlinx.coroutines.launch

class QuickShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rawText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString ?: ""
        val sharedUrl = extractUrl(rawText)

        if (sharedUrl.isBlank()) {
            Toast.makeText(this, "No valid link found in shared content", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            DownloadivoTheme {
                QuickShareOverlay(
                    url = sharedUrl,
                    onDismiss = { finish() },
                    onDownloadVideo = { info, format ->
                        HapticUtil.heavyClick(this@QuickShareActivity)
                        val expectedBytes = info?.availableFormats?.find { it.id == format }?.sizeBytes ?: 0L
                        DownloadService.startDownload(
                            context = this@QuickShareActivity,
                            url = sharedUrl,
                            title = info?.title ?: "Shared Video",
                            format = format,
                            thumbnail = info?.thumbnail ?: "",
                            duration = info?.duration ?: "",
                            expectedBytes = expectedBytes
                        )
                        Toast.makeText(
                            this@QuickShareActivity,
                            "⚡ Downloadivo: Video downloading in background...",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    },
                    onDownloadAudio = { info ->
                        HapticUtil.heavyClick(this@QuickShareActivity)
                        val expectedBytes = info?.availableFormats?.find { it.id == "mp3" }?.sizeBytes ?: 0L
                        DownloadService.startDownload(
                            context = this@QuickShareActivity,
                            url = sharedUrl,
                            title = info?.title ?: "Shared Audio",
                            format = "mp3",
                            thumbnail = info?.thumbnail ?: "",
                            duration = info?.duration ?: "",
                            expectedBytes = expectedBytes
                        )
                        Toast.makeText(
                            this@QuickShareActivity,
                            "🎵 Downloadivo: MP3 audio downloading...",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    },
                    onOpenApp = {
                        val appIntent = Intent(this@QuickShareActivity, MainActivity::class.java).apply {
                            putExtra("shared_url", sharedUrl)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(appIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun extractUrl(text: String): String {
        return DownloadEngine.sanitizeUrl(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickShareOverlay(
    url: String,
    onDismiss: () -> Unit,
    onDownloadVideo: (info: MediaInfo?, format: String) -> Unit,
    onDownloadAudio: (info: MediaInfo?) -> Unit,
    onOpenApp: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var resolvedInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var isResolving by remember { mutableStateOf(true) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var selectedFormat by remember { mutableStateOf("best") }
    var showCustomOptions by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Parallel fetch metadata
    LaunchedEffect(url) {
        coroutineScope.launch {
            isResolving = true
            resolveError = null
            val res = DownloadEngine.fetchMediaInfo(url)
            isResolving = false
            res.onSuccess {
                resolvedInfo = it
            }.onFailure {
                resolveError = it.message ?: "Failed to inspect link"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = SurfaceCardDark,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextSubtle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Bar: App Logo + Title + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_white),
                        contentDescription = "Downloadivo Icon",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Downloadivo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextCrisp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SurfaceHighlight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline)
                    ) {
                        Text(
                            text = "Quick Share",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextCrisp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            // Media Preview or Resolving State
            if (isResolving) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BackgroundDark,
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
                            color = TextCrisp,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Analyzing link at line-rate...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            text = url.take(45) + if (url.length > 45) "..." else "",
                            fontSize = 11.sp,
                            color = TextSubtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (resolvedInfo != null) {
                val info = resolvedInfo!!
                // Thumbnail + Platform Tag + Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BackgroundDark)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.thumbnail.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceCardDark)
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
                                        .padding(4.dp)
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
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SurfaceHighlight
                        ) {
                            Text(
                                text = info.extractor.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCrisp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = info.title.ifBlank { "Media Stream" },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (info.author.isNotBlank()) {
                            Text(
                                text = info.author,
                                fontSize = 11.5.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                // If resolving failed or took too long, fallback display
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BackgroundDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = TextCrisp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ready to Download",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = url,
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // PRIMARY 1-TAP ZERO-FRICTION BUTTONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Instant Video (Best)
                Button(
                    onClick = { onDownloadVideo(resolvedInfo, selectedFormat) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextCrisp,
                        contentColor = CanvasDark
                    )
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = CanvasDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download Video",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = CanvasDark
                    )
                }

                // Instant Audio (MP3)
                Button(
                    onClick = { onDownloadAudio(resolvedInfo) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceElevated,
                        contentColor = TextCrisp
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextCrisp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download Audio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = TextCrisp
                    )
                }
            }

            // Custom Options Toggle
            resolvedInfo?.let { info ->
                TextButton(
                    onClick = { showCustomOptions = !showCustomOptions },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (showCustomOptions) Icons.Default.ExpandLess else Icons.Default.Tune,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showCustomOptions) "Hide Resolutions" else "Choose Specific Resolution",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }

                AnimatedVisibility(visible = showCustomOptions) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val videoFormats = info.availableFormats.filter { !it.isAudio }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            videoFormats.take(4).forEach { fmt ->
                                val isSelected = selectedFormat == fmt.id
                                Surface(
                                    onClick = {
                                        HapticUtil.lightClick(context)
                                        selectedFormat = fmt.id
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) TextCrisp else SurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) TextCrisp else BorderHairline
                                    )
                                ) {
                                    val chipTitle = "${fmt.label.replace(" Video", "").replace(" Quality", "")}${if (fmt.sizeStr.isNotBlank()) "\n" + fmt.sizeStr else ""}"
                                    Text(
                                        text = chipTitle,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) CanvasDark else TextSecondary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Footer: Open in Full App Link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenApp
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Need Clip Trimming or Library? ",
                    fontSize = 12.sp,
                    color = TextSubtle
                )
                Text(
                    text = "Open Full App",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCrisp
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextCrisp,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
