package com.raoprime.downloadivo.ui.quickshare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.raoprime.downloadivo.MainActivity
import com.raoprime.downloadivo.R
import com.raoprime.downloadivo.data.DownloadRepository
import com.raoprime.downloadivo.model.DownloadStatus
import com.raoprime.downloadivo.model.DownloadTask
import com.raoprime.downloadivo.model.FormatOption
import com.raoprime.downloadivo.model.MediaInfo
import com.raoprime.downloadivo.network.DownloadEngine
import com.raoprime.downloadivo.service.DownloadService
import com.raoprime.downloadivo.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

class QuickShareActivity : ComponentActivity() {

    private var activeUrl by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure DownloadRepository is grounded
        DownloadRepository.init(applicationContext)

        val rawText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString ?: ""
        val sharedUrl = extractUrl(rawText)

        if (sharedUrl.isBlank()) {
            Toast.makeText(this, "No valid link found in shared content", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        activeUrl = sharedUrl

        setContent {
            DownloadivoTheme {
                QuickShareOverlay(
                    url = activeUrl,
                    onDismiss = { finish() },
                    onDownloadVideo = { info, format ->
                        HapticUtil.heavyClick(this@QuickShareActivity)
                        val expectedBytes = info?.availableFormats?.find { it.id == format }?.sizeBytes ?: 0L
                        DownloadService.startDownload(
                            context = this@QuickShareActivity,
                            url = activeUrl,
                            title = info?.title ?: "Shared Video",
                            format = format,
                            thumbnail = info?.thumbnail ?: "",
                            duration = info?.duration ?: "",
                            expectedBytes = expectedBytes
                        )
                        Toast.makeText(
                            this@QuickShareActivity,
                            "Added to Queue",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    },
                    onDownloadAudio = { info ->
                        HapticUtil.heavyClick(this@QuickShareActivity)
                        val expectedBytes = info?.availableFormats?.find { it.id == "mp3" }?.sizeBytes ?: 0L
                        DownloadService.startDownload(
                            context = this@QuickShareActivity,
                            url = activeUrl,
                            title = info?.title ?: "Shared Audio",
                            format = "mp3",
                            thumbnail = info?.thumbnail ?: "",
                            duration = info?.duration ?: "",
                            expectedBytes = expectedBytes
                        )
                        Toast.makeText(
                            this@QuickShareActivity,
                            "Added to Queue",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    },
                    onOpenApp = { currentUrl, selectedFormat ->
                        val appIntent = Intent(this@QuickShareActivity, MainActivity::class.java).apply {
                            putExtra("shared_url", currentUrl)
                            putExtra("selected_format", selectedFormat)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(appIntent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString ?: ""
        val newUrl = extractUrl(rawText)
        if (newUrl.isNotBlank()) {
            activeUrl = newUrl
        }
    }

    private fun extractUrl(text: String): String {
        return DownloadEngine.sanitizeUrl(text)
    }
}

private enum class OutputAction {
    DOWNLOAD,
    VIEW_IN_QUEUE,
    PLAY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickShareOverlay(
    url: String,
    onDismiss: () -> Unit,
    onDownloadVideo: (info: MediaInfo?, format: String) -> Unit,
    onDownloadAudio: (info: MediaInfo?) -> Unit,
    onOpenApp: (url: String, selectedFormat: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var resolvedInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var isResolving by remember { mutableStateOf(true) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var selectedFormat by remember { mutableStateOf("best") }
    var isQualityExpanded by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Monotonic request binding so late results from an old link never overwrite new link
    var currentRequestId by remember { mutableStateOf(0) }

    fun startResolution(targetUrl: String) {
        val reqId = ++currentRequestId
        isResolving = true
        resolveError = null
        resolvedInfo = null
        isQualityExpanded = false
        selectedFormat = "best"
        isSubmitting = false

        coroutineScope.launch {
            val res = DownloadEngine.fetchMediaInfo(targetUrl)
            if (reqId == currentRequestId) {
                isResolving = false
                res.onSuccess {
                    resolvedInfo = it
                }.onFailure {
                    resolveError = it.message ?: "Could not fetch download options"
                }
            }
        }
    }

    LaunchedEffect(url) {
        startResolution(url)
    }

    // Ground queue and library state for duplicate checking
    val currentTask by DownloadService.currentTask.collectAsState()
    val queuedTasks by DownloadService.queuedTasks.collectAsState()
    val historyList by DownloadRepository.history.collectAsState()

    val activeTasks = remember(currentTask, queuedTasks) {
        val list = mutableListOf<DownloadTask>()
        if (currentTask.status == DownloadStatus.DOWNLOADING || currentTask.status == DownloadStatus.RESOLVING) {
            list.add(currentTask)
        }
        list.addAll(queuedTasks)
        list
    }

    val mediaKey = remember(url) { extractMediaKey(url) }

    // ── Check separate status for VIDEO ──
    val videoMatchingActive = remember(mediaKey, activeTasks) {
        activeTasks.find { !it.isAudio && extractMediaKey(it.url) == mediaKey }
    }

    val videoMatchingCompleted = remember(mediaKey, historyList, selectedFormat) {
        historyList.find { task ->
            !task.isAudio &&
            task.status == DownloadStatus.SUCCESS &&
            extractMediaKey(task.url) == mediaKey &&
            (selectedFormat == "best" || task.format == selectedFormat || task.format == "best") &&
            !task.filePath.isNullOrBlank() &&
            File(task.filePath).exists()
        }
    }

    val videoAction = when {
        videoMatchingActive != null -> OutputAction.VIEW_IN_QUEUE
        videoMatchingCompleted != null -> OutputAction.PLAY
        else -> OutputAction.DOWNLOAD
    }

    // ── Check separate status for AUDIO ──
    val audioMatchingActive = remember(mediaKey, activeTasks) {
        activeTasks.find { it.isAudio && extractMediaKey(it.url) == mediaKey }
    }

    val audioMatchingCompleted = remember(mediaKey, historyList) {
        historyList.find { task ->
            task.isAudio &&
            task.status == DownloadStatus.SUCCESS &&
            extractMediaKey(task.url) == mediaKey &&
            !task.filePath.isNullOrBlank() &&
            File(task.filePath).exists()
        }
    }

    val audioAction = when {
        audioMatchingActive != null -> OutputAction.VIEW_IN_QUEUE
        audioMatchingCompleted != null -> OutputAction.PLAY
        else -> OutputAction.DOWNLOAD
    }

    // Button Labels and Icons
    val videoActionLabel = when (videoAction) {
        OutputAction.VIEW_IN_QUEUE -> "View in Queue"
        OutputAction.PLAY -> "Play video"
        OutputAction.DOWNLOAD -> "Download video"
    }
    val videoActionIcon = when (videoAction) {
        OutputAction.VIEW_IN_QUEUE -> Icons.AutoMirrored.Filled.FormatListBulleted
        OutputAction.PLAY -> Icons.Default.PlayArrow
        OutputAction.DOWNLOAD -> Icons.Default.Download
    }

    val audioActionLabel = when (audioAction) {
        OutputAction.VIEW_IN_QUEUE -> "View in Queue"
        OutputAction.PLAY -> "Play audio"
        OutputAction.DOWNLOAD -> "Download audio"
    }
    val audioActionIcon = when (audioAction) {
        OutputAction.VIEW_IN_QUEUE -> Icons.AutoMirrored.Filled.FormatListBulleted
        OutputAction.PLAY -> Icons.Default.PlayArrow
        OutputAction.DOWNLOAD -> Icons.Default.MusicNote
    }

    // Action execution with rapid double-tap guard
    fun handleVideoClick() {
        if (isSubmitting) return
        when (videoAction) {
            OutputAction.VIEW_IN_QUEUE -> {
                HapticUtil.lightClick(context)
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("select_tab", "queue")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
                onDismiss()
            }
            OutputAction.PLAY -> {
                HapticUtil.mediumClick(context)
                videoMatchingCompleted?.let { task ->
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("playback_uri", task.filePath)
                        putExtra("playback_title", task.title)
                        putExtra("playback_is_audio", false)
                        putExtra("playback_duration", task.duration)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(intent)
                }
                onDismiss()
            }
            OutputAction.DOWNLOAD -> {
                isSubmitting = true
                HapticUtil.heavyClick(context)
                onDownloadVideo(resolvedInfo, selectedFormat)
            }
        }
    }

    fun handleAudioClick() {
        if (isSubmitting) return
        when (audioAction) {
            OutputAction.VIEW_IN_QUEUE -> {
                HapticUtil.lightClick(context)
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("select_tab", "queue")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
                onDismiss()
            }
            OutputAction.PLAY -> {
                HapticUtil.mediumClick(context)
                audioMatchingCompleted?.let { task ->
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("playback_uri", task.filePath)
                        putExtra("playback_title", task.title)
                        putExtra("playback_is_audio", true)
                        putExtra("playback_duration", task.duration)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(intent)
                }
                onDismiss()
            }
            OutputAction.DOWNLOAD -> {
                isSubmitting = true
                HapticUtil.heavyClick(context)
                onDownloadAudio(resolvedInfo)
            }
        }
    }

    BackHandler { onDismiss() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color(0xFF141416), // Matte Graphite surface
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF383840))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 1. HEADER: Small D-arrow + "Downloadivo" + "Quick Share" + Close ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_white),
                        contentDescription = "Downloadivo D-Arrow",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloadivo",
                        style = Typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF222228),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF32323C))
                    ) {
                        Text(
                            text = "Quick Share",
                            style = Typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            color = Color(0xFFD4D4DC),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        HapticUtil.lightClick(context)
                        onDismiss()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF9E9EA6),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── 2. MEDIA PREVIEW (RESERVED SPACE TO PREVENT LAYOUT JUMPS) ──
            if (isResolving) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1A1A1E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26262E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 74.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val detected = detectSource(url)
                                if (detected.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF25252D),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = detected.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD4D4DC),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Getting download options…",
                                    style = Typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    ),
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = url,
                                style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = Color(0xFF71717A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (resolvedInfo != null) {
                val info = resolvedInfo!!
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1A1A1E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26262E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 74.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Aspect-correct 16:9 Thumbnail
                        Box(
                            modifier = Modifier
                                .width(86.dp)
                                .height(49.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF25252D))
                                .border(0.5.dp, Color(0xFF383842), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (info.thumbnail.isNotBlank()) {
                                AsyncImage(
                                    model = info.thumbnail,
                                    contentDescription = "Preview Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.SmartDisplay,
                                    contentDescription = null,
                                    tint = Color(0xFF71717A),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (info.duration.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = Color.Black.copy(alpha = 0.82f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(3.dp)
                                ) {
                                    Text(
                                        text = info.duration,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = info.title.replace(Regex("\\s+"), " ").trim().ifBlank { "Media Stream" },
                                style = Typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    lineHeight = 18.sp
                                ),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics {
                                    contentDescription = info.title
                                }
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            val metaParts = buildList {
                                val platform = if (info.extractor.isNotBlank() && info.extractor != "Web" && info.extractor != "generic") info.extractor else detectSource(url)
                                if (platform.isNotBlank()) add(platform)
                                if (info.duration.isNotBlank()) add(info.duration)
                                if (info.author.isNotBlank() && !info.author.equals("Unknown Author", ignoreCase = true) && !info.author.equals("Unknown", ignoreCase = true)) {
                                    add(info.author)
                                }
                            }
                            if (metaParts.isNotEmpty()) {
                                Text(
                                    text = metaParts.joinToString(" · "),
                                    style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = Color(0xFF9E9EA6),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                // Resolution failed or temporary error with Retry action
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1A1A1E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26262E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = resolveError ?: "Could not fetch stream details",
                                style = Typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = url,
                                style = Typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color(0xFF71717A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = {
                                HapticUtil.lightClick(context)
                                startResolution(url)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383842)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ── 3. COMPACT "VIDEO QUALITY" DISCLOSURE (WHEN RESOLVED) ──
            if (resolvedInfo != null) {
                val availableVideoFormats = remember(resolvedInfo) {
                    val formats = resolvedInfo?.availableFormats?.filter { !it.isAudio } ?: emptyList()
                    formats.sortedWith(compareByDescending<FormatOption> { it.id == "best" }.thenByDescending { it.resolution })
                }

                if (availableVideoFormats.isNotEmpty()) {
                    val currentSelection = availableVideoFormats.find { it.id == selectedFormat }
                        ?: availableVideoFormats.firstOrNull()
                        ?: FormatOption("best", "Best available", "mp4", false)

                    Surface(
                        onClick = {
                            HapticUtil.lightClick(context)
                            isQualityExpanded = !isQualityExpanded
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1A1A1E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26262E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color(0xFF9E9EA6),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = "Video quality:",
                                style = Typography.bodyMedium.copy(fontSize = 12.5.sp),
                                color = Color(0xFF9E9EA6)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentSelection.label.replace(" Video", "").replace(" Quality", ""),
                                style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val effSize = currentSelection.getEffectiveSize(resolvedInfo?.duration ?: "")
                            if (effSize.isNotBlank() && effSize != "0 B" && effSize != "0 KB") {
                                Text(
                                    text = effSize,
                                    style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = Color(0xFF71717A),
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            Icon(
                                imageVector = if (isQualityExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isQualityExpanded) "Collapse" else "Expand",
                                tint = Color(0xFF9E9EA6),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Expanded Descending Resolution List
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isQualityExpanded,
                        enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
                        exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF16161A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26262E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                availableVideoFormats.forEachIndexed { idx, fmt ->
                                    val isSelected = selectedFormat == fmt.id
                                    Surface(
                                        onClick = {
                                            HapticUtil.lightClick(context)
                                            selectedFormat = fmt.id
                                            isQualityExpanded = false
                                        },
                                        color = if (isSelected) Color(0xFF222228) else Color.Transparent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = fmt.label,
                                                    style = Typography.bodyMedium.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 13.sp
                                                    ),
                                                    color = if (isSelected) Color.White else Color(0xFFD4D4DC)
                                                )
                                                val limitLabel = if (fmt.id == "best") "Best available stream" else if (fmt.resolution > 0) "Resolution limit: ${fmt.resolution}p" else ""
                                                val subLabel = if (fmt.note.isNotBlank()) fmt.note else limitLabel
                                                if (subLabel.isNotBlank()) {
                                                    Text(
                                                        text = subLabel,
                                                        style = Typography.bodySmall.copy(fontSize = 11.sp),
                                                        color = Color(0xFF71717A)
                                                    )
                                                }
                                            }
                                            val fmtSize = fmt.getEffectiveSize(resolvedInfo?.duration ?: "")
                                            if (fmtSize.isNotBlank() && fmtSize != "0 B" && fmtSize != "0 KB") {
                                                Text(
                                                    text = fmtSize,
                                                    style = Typography.bodySmall.copy(fontSize = 11.5.sp),
                                                    color = Color(0xFF9E9EA6),
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (idx < availableVideoFormats.lastIndex) {
                                        HorizontalDivider(
                                            color = Color(0xFF1E1E24),
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

            // ── 4. DIRECT DOWNLOAD ACTIONS (NO CLIPPING, ADAPTIVE STACKING) ──
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val fontScale = density.fontScale
                val shouldStack = maxWidth < 380.dp || fontScale > 1.05f

                if (shouldStack) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryActionButton(
                            label = videoActionLabel,
                            icon = videoActionIcon,
                            isPrimary = true,
                            enabled = !isSubmitting,
                            onClick = { handleVideoClick() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        PrimaryActionButton(
                            label = audioActionLabel,
                            icon = audioActionIcon,
                            isPrimary = false,
                            enabled = !isSubmitting,
                            onClick = { handleAudioClick() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryActionButton(
                            label = videoActionLabel,
                            icon = videoActionIcon,
                            isPrimary = true,
                            enabled = !isSubmitting,
                            onClick = { handleVideoClick() },
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryActionButton(
                            label = audioActionLabel,
                            icon = audioActionIcon,
                            isPrimary = false,
                            enabled = !isSubmitting,
                            onClick = { handleAudioClick() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 5. FOOTER: "Open in Downloadivo" WITH CURRENT URL & OPTIONS ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        HapticUtil.lightClick(context)
                        onOpenApp(url, selectedFormat)
                    }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open in Downloadivo",
                    style = Typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = Color(0xFFD4D4DC)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFFD4D4DC),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: ImageVector,
    isPrimary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        color = if (isPrimary) Color.White else Color(0xFF1E1E22),
        contentColor = if (isPrimary) Color.Black else Color.White,
        border = if (isPrimary) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E36)),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPrimary) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = Typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = if (isPrimary) Color.Black else Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private fun detectSource(url: String): String {
    val lower = url.lowercase()
    return when {
        "youtube.com" in lower || "youtu.be" in lower -> "YouTube"
        "instagram.com" in lower -> "Instagram"
        "tiktok.com" in lower -> "TikTok"
        "twitter.com" in lower || "x.com" in lower -> "X"
        "spotify.com" in lower -> "Spotify"
        "facebook.com" in lower || "fb.watch" in lower -> "Facebook"
        "reddit.com" in lower -> "Reddit"
        "vimeo.com" in lower -> "Vimeo"
        "soundcloud.com" in lower -> "SoundCloud"
        else -> "Web"
    }
}

private fun extractMediaKey(rawUrl: String): String {
    val clean = DownloadEngine.sanitizeUrl(rawUrl)
    val ytMatch = Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?v=|embed/|shorts/|v/))([a-zA-Z0-9_-]{11})""").find(clean)
    if (ytMatch != null) return "yt:${ytMatch.groupValues[1]}"

    val igMatch = Regex("""instagram\.com/(?:[a-zA-Z0-9_\.]+/)?(?:reel|reels|p|tv|share/reel|share/p|share/r)/([a-zA-Z0-9_-]+)""").find(clean)
    if (igMatch != null) return "ig:${igMatch.groupValues[1]}"

    val ttMatch = Regex("""tiktok\.com/(?:@[^/]+/video/|v/|t/)?([0-9a-zA-Z_-]+)""").find(clean)
    if (ttMatch != null && ttMatch.groupValues[1].length > 5) return "tt:${ttMatch.groupValues[1]}"

    val xMatch = Regex("""(?:twitter\.com|x\.com)/[^/]+/status/([0-9]+)""").find(clean)
    if (xMatch != null) return "tw:${xMatch.groupValues[1]}"

    return clean.replace(Regex("""[?&](?:si|utm_[^&=]+|fbclid|ref|feature)=[^&#]*"""), "")
        .trimEnd('?', '&')
        .lowercase()
}
