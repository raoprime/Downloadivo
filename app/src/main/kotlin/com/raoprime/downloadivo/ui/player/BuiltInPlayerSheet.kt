package com.raoprime.downloadivo.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.raoprime.downloadivo.MainActivity
import com.raoprime.downloadivo.network.DownloadEngine
import com.raoprime.downloadivo.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun BuiltInPlayerSheet(
    exoPlayer: ExoPlayer,
    mediaUri: String,
    title: String,
    author: String = "",
    isAudio: Boolean,
    duration: String = "",
    availableQualities: List<String> = emptyList(),
    currentQuality: String = "",
    availableAudioTracks: List<String> = emptyList(),
    currentAudioTrack: String = "",
    inPipMode: Boolean = false,
    isFullscreen: Boolean = false,
    onFullscreenChange: ((Boolean) -> Unit)? = null,
    onQualitySelected: ((String) -> Unit)? = null,
    onAudioTrackSelected: ((String) -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mainActivity = activity as? MainActivity

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isBuffering by remember { mutableStateOf(exoPlayer.playbackState == Player.STATE_BUFFERING) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var showFullscreenControls by remember { mutableStateOf(true) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var isZoomToFill by remember { mutableStateOf(false) }
    var autoRetryCount by remember { mutableIntStateOf(0) }

    // Double tap feedback
    var showDoubleTapRewind by remember { mutableStateOf(false) }
    var showDoubleTapForward by remember { mutableStateOf(false) }

    // Sub-dialogs
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    // Playback Progress & Duration
    var currentPositionMs by remember { mutableLongStateOf(exoPlayer.currentPosition) }
    var durationMs by remember(duration) { mutableLongStateOf(DownloadEngine.parseDurationToMillis(duration)) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    // Pure Video PiP Mode: Full bleed zero-overlay video surface
    if (inPipMode && !isAudio) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        keepScreenOn = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { pv ->
                    if (pv.player != exoPlayer) {
                        pv.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    // Lock orientation when toggling fullscreen
    DisposableEffect(isFullscreen) {
        if (isFullscreen && !isAudio) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Auto PiP capability
    DisposableEffect(isAudio) {
        mainActivity?.pipEnabled = !isAudio
        onDispose {
            mainActivity?.pipEnabled = false
        }
    }

    // Load or switch media URL only if not already loaded to prevent minimize resets
    LaunchedEffect(mediaUri) {
        val currentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri != mediaUri) {
            playerError = null
            isBuffering = true
            val uri = Uri.parse(mediaUri)
            val mimeType = if (isAudio) MimeTypes.AUDIO_MPEG else MimeTypes.VIDEO_MP4
            val item = MediaItem.Builder().setUri(uri).setMimeType(mimeType).build()
            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Dynamic duration update if parsed from stream
    LaunchedEffect(duration) {
        val parsed = DownloadEngine.parseDurationToMillis(duration)
        if (parsed > 0) {
            durationMs = parsed
        }
    }

    // Ticker for currentPosition
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                currentPositionMs = exoPlayer.currentPosition
                if (exoPlayer.duration > 0 && exoPlayer.duration != androidx.media3.common.C.TIME_UNSET) {
                    durationMs = exoPlayer.duration
                }
            }
            isPlaying = exoPlayer.isPlaying
            delay(400)
        }
    }

    // Auto-hide fullscreen controls after 3.5 seconds
    LaunchedEffect(showFullscreenControls, isPlaying, isFullscreen) {
        if (isFullscreen && showFullscreenControls && isPlaying) {
            delay(3500)
            showFullscreenControls = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    playerError = null
                    autoRetryCount = 0
                    if (exoPlayer.duration > 0 && exoPlayer.duration != androidx.media3.common.C.TIME_UNSET) {
                        durationMs = exoPlayer.duration
                    }
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                if (autoRetryCount < 3) {
                    autoRetryCount++
                    isBuffering = true
                    exoPlayer.prepare()
                    exoPlayer.play()
                } else {
                    playerError = error.localizedMessage ?: "Stream connection error (${error.errorCodeName})"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Disc rotation for Audio Mode
    val infiniteTransition = rememberInfiniteTransition(label = "audio_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.wrapContentHeight()),
        shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(22.dp),
        color = SurfaceDark,
        border = if (isFullscreen) null else androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isFullscreen) 0.dp else 14.dp)
        ) {
            // ═════════════════════════════════════════════════════════════════
            // PROMINENT TOP HEADER (LIFTED & VISIBLE OUTSIDE VIDEO STAGE)
            // ═════════════════════════════════════════════════════════════════
            if (!isFullscreen) {
                // Top drag handle indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 2.dp, bottom = 10.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(BorderSubtle)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.SmartDisplay,
                                contentDescription = null,
                                tint = TextCrisp,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title.ifBlank { "Now Playing" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextCrisp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (author.isNotBlank()) author else (if (isAudio) "Live Audio Stream • Studio 320k" else "HD Video Stream • Ad-Free"),
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Aspect Ratio Toggle (Fit vs Zoom/Crop)
                        if (!isAudio) {
                            IconButton(
                                onClick = { isZoomToFill = !isZoomToFill },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isZoomToFill) Icons.Default.FitScreen else Icons.Default.CropFree,
                                    contentDescription = if (isZoomToFill) "Fit to Screen" else "Zoom to Fill",
                                    tint = if (isZoomToFill) TextCrisp else TextTertiary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        // Minimize Button (dock to bottom floating miniplayer)
                        IconButton(onClick = onMinimize, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = TextCrisp, modifier = Modifier.size(24.dp))
                        }
                        // PiP Button
                        if (!isAudio && onEnterPip != null) {
                            IconButton(onClick = onEnterPip, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.PictureInPicture, contentDescription = "PiP", tint = TextCrisp, modifier = Modifier.size(20.dp))
                            }
                        }
                        // Close Button
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextTertiary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // VIDEO / AUDIO STAGE CONTAINER
            // ═════════════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFullscreen) Modifier.fillMaxSize()
                        else if (isAudio) Modifier.height(180.dp)
                        else Modifier.aspectRatio(16f / 9f)
                    )
                    .clip(if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp))
                    .background(Color.Black)
            ) {
                if (isAudio) {
                    // Audio Mode Stage
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .rotate(if (isPlaying) rotation else 0f)
                                .clip(CircleShape)
                                .background(Color(0xFF141418))
                                .border(3.dp, TextCrisp, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = TextCrisp,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                } else {
                    // Video Mode Stage: Native ExoPlayer view
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                keepScreenOn = true
                                resizeMode = if (isZoomToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { pv ->
                            if (pv.player != exoPlayer) {
                                pv.player = exoPlayer
                            }
                            pv.resizeMode = if (isZoomToFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Double Tap Detection Surface (Left = -10s, Right = +10s, Center = toggle play/pause or fullscreen overlay)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    val width = size.width
                                    if (offset.x < width / 2f) {
                                        val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(target)
                                        currentPositionMs = target
                                        showDoubleTapRewind = true
                                    } else {
                                        val maxDur = if (durationMs > 0) durationMs else Long.MAX_VALUE
                                        val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxDur)
                                        exoPlayer.seekTo(target)
                                        currentPositionMs = target
                                        showDoubleTapForward = true
                                    }
                                },
                                onTap = {
                                    if (isFullscreen) {
                                        showFullscreenControls = !showFullscreenControls
                                    } else {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        isPlaying = !isPlaying
                                    }
                                }
                            )
                        }
                )

                // Double Tap -10s Ripple Feedback
                LaunchedEffect(showDoubleTapRewind) {
                    if (showDoubleTapRewind) {
                        delay(650)
                        showDoubleTapRewind = false
                    }
                }
                if (showDoubleTapRewind) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.5f)
                            .align(Alignment.CenterStart)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                            Text("-10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Double Tap +10s Ripple Feedback
                LaunchedEffect(showDoubleTapForward) {
                    if (showDoubleTapForward) {
                        delay(650)
                        showDoubleTapForward = false
                    }
                }
                if (showDoubleTapForward) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.5f)
                            .align(Alignment.CenterEnd)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                            Text("+10s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Buffering Spinner
                if (isBuffering && playerError == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = TextCrisp, strokeWidth = 3.dp, modifier = Modifier.size(42.dp))
                    }
                }

                // Player Error State
                if (playerError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.90f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusError, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Playback Interrupted", color = TextCrisp, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(playerError ?: "Connecting to stream...", color = TextSecondary, fontSize = 11.5.sp, textAlign = TextAlign.Center, maxLines = 2)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    playerError = null
                                    isBuffering = true
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TextCrisp, contentColor = CanvasDark),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Retry", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Fullscreen On-Screen Overlay Controls
                if (isFullscreen) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showFullscreenControls || !isPlaying,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(180)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.75f),
                                            Color.Black.copy(alpha = 0.25f),
                                            Color.Black.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                        ) {
                            // Top Bar inside Fullscreen
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title.ifBlank { "Now Playing" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onFullscreenChange?.invoke(false) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }

                            // Center Playback Buttons in Fullscreen
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth(0.55f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(target)
                                        currentPositionMs = target
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(34.dp))
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.6f),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clickable {
                                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                            isPlaying = !isPlaying
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        val maxDur = if (durationMs > 0) durationMs else Long.MAX_VALUE
                                        val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxDur)
                                        exoPlayer.seekTo(target)
                                        currentPositionMs = target
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(34.dp))
                                }
                            }

                            // Bottom Scrubber in Fullscreen
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                val totalDur = if (durationMs > 0) durationMs else 1L
                                val pos = if (isScrubbing) scrubPositionMs else currentPositionMs
                                val progressFrac = (pos.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)
                                Slider(
                                    value = progressFrac,
                                    onValueChange = { frac ->
                                        isScrubbing = true
                                        scrubPositionMs = (frac * totalDur).toLong()
                                    },
                                    onValueChangeFinished = {
                                        exoPlayer.seekTo(scrubPositionMs)
                                        currentPositionMs = scrubPositionMs
                                        isScrubbing = false
                                    },
                                    modifier = Modifier.fillMaxWidth().height(20.dp),
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.35f))
                                )
                                val curStr = formatMsToTime(if (isScrubbing) scrubPositionMs else currentPositionMs)
                                val durStr = if (duration.isNotBlank()) duration else formatMsToTime(durationMs)
                                Text(text = "$curStr / $durStr", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ═════════════════════════════════════════════════════════════════
            // DEDICATED EXTERNAL SCRUBBER & CONTROLS (ALWAYS VISIBLE & EASY TO REACH)
            // ═════════════════════════════════════════════════════════════════
            if (!isFullscreen) {
                Spacer(modifier = Modifier.height(10.dp))

                // Progress Slider
                val totalDur = if (durationMs > 0) durationMs else 1L
                val pos = if (isScrubbing) scrubPositionMs else currentPositionMs
                val progressFrac = (pos.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)

                Slider(
                    value = progressFrac,
                    onValueChange = { frac ->
                        isScrubbing = true
                        scrubPositionMs = (frac * totalDur).toLong()
                    },
                    onValueChangeFinished = {
                        exoPlayer.seekTo(scrubPositionMs)
                        currentPositionMs = scrubPositionMs
                        isScrubbing = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = TextCrisp,
                        activeTrackColor = TextCrisp,
                        inactiveTrackColor = CardBorderDark
                    )
                )

                // Timestamps & Fullscreen Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val curStr = formatMsToTime(if (isScrubbing) scrubPositionMs else currentPositionMs)
                    val durStr = if (duration.isNotBlank()) duration else formatMsToTime(durationMs)
                    Text(
                        text = "$curStr / $durStr",
                        color = TextCrisp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    if (!isAudio) {
                        IconButton(
                            onClick = { onFullscreenChange?.invoke(!isFullscreen) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = TextCrisp, modifier = Modifier.size(22.dp))
                        }
                    }
                }

                // Dedicated Playback Control Row (-10s, Play/Pause, +10s)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = TextCrisp, modifier = Modifier.size(30.dp))
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Surface(
                        shape = CircleShape,
                        color = TextCrisp,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(54.dp)
                            .clickable {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                    isPlaying = false
                                } else {
                                    exoPlayer.play()
                                    isPlaying = true
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = CanvasDark,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    IconButton(
                        onClick = {
                            val maxDur = if (durationMs > 0) durationMs else Long.MAX_VALUE
                            val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(maxDur)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = TextCrisp, modifier = Modifier.size(30.dp))
                    }
                }

                // Quick Controls Chips Row: Audio Track, Quality, Speed
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Audio Track / Multi-Language Button
                    val activeAudioLabel = if (currentAudioTrack.isNotBlank()) currentAudioTrack else "Default Audio"
                    Surface(
                        onClick = { showAudioTrackSheet = true },
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Audio: $activeAudioLabel",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextCrisp
                            )
                        }
                    }

                    // Video Quality Button
                    if (!isAudio) {
                        val activeQualLabel = when {
                            currentQuality.contains("4k", ignoreCase = true) || currentQuality.contains("2160") -> "4K UHD"
                            currentQuality.contains("1440") -> "2K QHD"
                            currentQuality.contains("1080") -> "1080p FHD"
                            currentQuality.contains("720") -> "720p HD"
                            currentQuality.contains("480") -> "480p SD"
                            currentQuality.contains("360") -> "360p"
                            else -> currentQuality.ifBlank { "Auto / 1080p" }
                        }
                        Surface(
                            onClick = { showQualitySheet = true },
                            shape = RoundedCornerShape(8.dp),
                            color = SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Quality: $activeQualLabel",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextCrisp
                                )
                            }
                        }
                    }

                    // Speed Selector Button
                    Surface(
                        onClick = { showSpeedSheet = true },
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Speed: ${currentSpeed}x",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextCrisp
                            )
                        }
                    }
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // AUDIO TRACK SELECTOR MODAL DIALOG (Hindi, English, Original, etc.)
    // ═════════════════════════════════════════════════════════════════════════
    if (showAudioTrackSheet) {
        val tracks = if (availableAudioTracks.isNotEmpty()) availableAudioTracks else listOf("Original Audio", "Hindi (हिन्दी)", "English")
        AlertDialog(
            onDismissRequest = { showAudioTrackSheet = false },
            containerColor = SurfaceDark,
            title = {
                Text("Select Audio Track (भाषा)", color = TextCrisp, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tracks.forEach { track ->
                        val isSelected = currentAudioTrack.equals(track, ignoreCase = true) ||
                            (currentAudioTrack.isBlank() && track.contains("Original", ignoreCase = true))
                        Surface(
                            onClick = {
                                onAudioTrackSelected?.invoke(track)
                                showAudioTrackSheet = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) SurfaceHighlight else SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) TextCrisp else BorderHairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(track, color = if (isSelected) TextCrisp else TextSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioTrackSheet = false }) {
                    Text("Close", color = TextCrisp)
                }
            }
        )
    }

    // ═════════════════════════════════════════════════════════════════════════
    // QUALITY SELECTOR MODAL DIALOG
    // ═════════════════════════════════════════════════════════════════════════
    if (showQualitySheet && onQualitySelected != null) {
        val defaultQualities = if (availableQualities.isNotEmpty()) availableQualities else listOf("best", "1080p", "720p", "480p", "360p")
        AlertDialog(
            onDismissRequest = { showQualitySheet = false },
            containerColor = SurfaceDark,
            title = {
                Text("Video Quality", color = TextCrisp, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    defaultQualities.forEach { q ->
                        val isSelected = currentQuality.equals(q, ignoreCase = true) ||
                            (currentQuality.isBlank() && q.contains("1080", ignoreCase = true))
                        val label = when {
                            q.contains("4k") || q.contains("2160") -> "4K Ultra HD"
                            q.contains("1440") -> "2K Quad HD"
                            q.contains("1080") -> "1080p Full HD"
                            q.contains("720") -> "720p HD"
                            q.contains("480") -> "480p Standard"
                            q.contains("360") -> "360p Data Saver"
                            else -> q.uppercase()
                        }
                        Surface(
                            onClick = {
                                onQualitySelected(q)
                                showQualitySheet = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) SurfaceHighlight else SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) TextCrisp else BorderHairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, color = if (isSelected) TextCrisp else TextSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualitySheet = false }) {
                    Text("Close", color = TextCrisp)
                }
            }
        )
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SPEED SELECTOR MODAL DIALOG
    // ═════════════════════════════════════════════════════════════════════════
    if (showSpeedSheet) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedSheet = false },
            containerColor = SurfaceDark,
            title = {
                Text("Playback Speed", color = TextCrisp, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    speeds.forEach { sp ->
                        val isSelected = currentSpeed == sp
                        Surface(
                            onClick = {
                                currentSpeed = sp
                                exoPlayer.playbackParameters = PlaybackParameters(sp)
                                showSpeedSheet = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) SurfaceHighlight else SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) TextCrisp else BorderHairline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (sp == 1.0f) "Normal (1.0x)" else "${sp}x", color = if (isSelected) TextCrisp else TextSecondary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = TextCrisp, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedSheet = false }) {
                    Text("Close", color = TextCrisp)
                }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// YOUTUBE-STYLE DOCKED BOTTOM FLOATING MINIPLAYER
// (Plays continuously when player is minimized, letting user browse other tabs!)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun YouTubeFloatingMiniplayer(
    title: String,
    author: String = "",
    thumbnail: String = "",
    isAudio: Boolean,
    isPlaying: Boolean = true,
    onTogglePlay: () -> Unit = {},
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 18.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable { onExpand() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniature 16:9 Thumbnail
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isAudio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = TextCrisp,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "Now Playing" },
                    style = Typography.titleSmall,
                    color = TextCrisp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (author.isNotBlank()) author else (if (isAudio) "Playing Audio" else "Playing Video"),
                    style = Typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onTogglePlay, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = TextCrisp
                )
            }

            IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Expand", tint = TextCrisp)
            }

            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextTertiary)
            }
        }
    }
}

private fun formatMsToTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val sec = ms / 1000L
    val h = sec / 3600L
    val m = (sec % 3600L) / 60L
    val s = sec % 60L
    return if (h > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }
}
