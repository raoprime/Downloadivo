package com.raoprime.downloadivo.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

import com.raoprime.downloadivo.R
import com.raoprime.downloadivo.ui.theme.HapticUtil
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// ── Google Material 3 Expressive & Obsidian Tokens ────────────
private val CanvasVoid = Color(0xFF060608)
private val SurfaceGlassCard = Color(0x0AFFFFFF)      // 4% white card
private val BorderGlassCard = Color(0x14FFFFFF)       // 8% white hairline
private val SurfaceBadge = Color(0x0FFFFFFF)          // 6% white badge
private val BorderBadge = Color(0x18FFFFFF)           // 9% white border
private val TextWhite = Color(0xFFFFFFFF)
private val TextZinc100 = Color(0xFFF4F4F5)
private val TextZinc400 = Color(0xFFA1A1AA)
private val TextZinc500 = Color(0xFF71717A)
private val TextZinc700 = Color(0xFF3F3F46)

data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

data class PlatformItem(
    val name: String,
    val color: Color,
    val tag: String
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val totalPages = 4
    val pagerState = rememberPagerState(pageCount = { totalPages })

    val platforms = remember {
        listOf(
            PlatformItem("YouTube", Color(0xFFFF0000), "4K / 8K • Shorts"),
            PlatformItem("Instagram", Color(0xFFE1306C), "Reels • Stories"),
            PlatformItem("TikTok", Color(0xFF00F2FE), "No Watermark"),
            PlatformItem("X / Twitter", Color(0xFFFFFFFF), "Videos • GIFs"),
            PlatformItem("Facebook", Color(0xFF1877F2), "Watch • HD"),
            PlatformItem("Reddit", Color(0xFFFF4500), "Audio+Video"),
            PlatformItem("Pinterest", Color(0xFFE60023), "Pins • Media"),
            PlatformItem("SoundCloud", Color(0xFFFF5500), "HQ Audio"),
            PlatformItem("Vimeo", Color(0xFF1AB7EA), "1080p • 4K"),
            PlatformItem("1,000+ More", Color(0xFF10B981), "Universal")
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasVoid)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Google Ambient Light Mesh (Soft multi-point radial gradient)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .size(320.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x286366F1), // Indigo aura (16%)
                            Color(0x0C8B5CF6), // Purple falloff
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top Header Bar (Google/Apple Tier Minimalist) ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Mark: High-Contrast "D" Emblem + Typography
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = "Downloadivo",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = "Downloadivo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextZinc100,
                        letterSpacing = (-0.2).sp
                    )
                }

                // Step Counter Pill + Skip Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceBadge,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderBadge)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / $totalPages",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextZinc400,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = "Skip",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextZinc400,
                        modifier = Modifier
                            .clickable {
                                HapticUtil.mediumClick(context)
                                onFinish()
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            // ── Central Interactive Pager ───────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> SlideHeroBrand()
                    1 -> SlidePlatformsGrid(platforms)
                    2 -> SlideTurboEngine()
                    3 -> SlideMediaStudio()
                }
            }

            // ── Bottom Navigation & Expressive CTA ──────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Expanding Spring Pagination Indicator Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPages) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 26.dp else 6.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "pill_width"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isSelected) TextWhite
                                    else TextZinc700
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        )
                    }
                }

                // Tactile CTA Button with Micro-Press Physics
                val isLastPage = pagerState.currentPage == totalPages - 1
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.98f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "cta_press"
                )

                Button(
                    onClick = {
                        if (isLastPage) {
                            HapticUtil.heavyClick(context)
                            onFinish()
                        } else {
                            HapticUtil.mediumClick(context)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = Color.White.copy(alpha = 0.05f),
                            spotColor = Color.White.copy(alpha = 0.1f)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextWhite,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isLastPage) "Get Started with Downloadivo" else "Continue",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (isLastPage) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SLIDE 1: Google Expressive Media Core & Fixed 2x2 Bento Grid
// ─────────────────────────────────────────────────────────────
@Composable
private fun SlideHeroBrand() {
    val features = remember {
        listOf(
            FeatureItem(Icons.Default.AllInclusive, "100% Free", "No Paywalls"),
            FeatureItem(Icons.Default.Shield, "0 Watermarks", "Pure Quality"),
            FeatureItem(Icons.Default.Lock, "100% Private", "Device Direct"),
            FeatureItem(Icons.Default.Bolt, "Ultra Fast", "Aria2c Multi-Thread")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Google Material 3 Expressive Floating Orbit Core (120Hz Hardware Accelerated)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Flagship3DMediaHero(
                modifier = Modifier
                    .size(200.dp)
                    .padding(bottom = 6.dp)
            )

            Text(
                text = "The Next-Gen Media Suite",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Fetch, stream, convert, and manage high-definition media across platforms without limits.",
                fontSize = 13.sp,
                color = TextZinc400,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Fixed 2x2 Bento Grid (Zero Line-Wrapping, 1px Specular Border)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BentoCard(features[0], modifier = Modifier.weight(1f))
                BentoCard(features[1], modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BentoCard(features[2], modifier = Modifier.weight(1f))
                BentoCard(features[3], modifier = Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// COMPOSABLE 1: Google Expressive 120Hz Floating Media Core
// ─────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────
// COMPOSABLE 1: Flagship 3D Media Core (Cinema4D / Octane 60fps Loop)
// ─────────────────────────────────────────────────────────────
@Composable
private fun Flagship3DMediaHero(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val rawUri = Uri.parse("android.resource://${context.packageName}/${R.raw.intro_3d}")
            setMediaItem(MediaItem.fromUri(rawUri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Soft ambient radial glow matching cyan/violet caustics
        Box(
            modifier = Modifier
                .size(190.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.16f),
                            Color(0xFF7C4DFF).copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Instant base 3D render plate (pure Downloadivo official emblem)
        Image(
            painter = painterResource(id = R.drawable.intro_3d_render),
            contentDescription = "Downloadivo 3D Media Core",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(24.dp))
        )

        // Hardware-accelerated Media3 ExoPlayer looping view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(24.dp))
        )
    }
}

@Composable
private fun GoogleExpressiveSpeedStream(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "speed_transition")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.width * 0.42f

            // Outer Speed Gauge Arc
            drawCircle(
                color = Color(0x18FFFFFF),
                radius = maxR,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Dynamic 16 High-Speed Stream Beams radiating outward
            val streamCount = 16
            for (i in 0 until streamCount) {
                val baseAngle = (i.toFloat() / streamCount.toFloat()) * 2f * Math.PI.toFloat()
                val currentPhase = (phase + (i * 0.0625f)) % 1f

                val startDist = maxR * (0.35f + currentPhase * 0.45f)
                val endDist = startDist + (maxR * 0.25f)

                val x1 = center.x + cos(baseAngle) * startDist
                val y1 = center.y + sin(baseAngle) * startDist
                val x2 = center.x + cos(baseAngle) * endDist
                val y2 = center.y + sin(baseAngle) * endDist

                val beamColor = if (i % 2 == 0) Color(0xFF10B981) else Color.White
                drawLine(
                    color = beamColor.copy(alpha = (1f - currentPhase) * 0.75f),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Central Bolt Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x3310B981),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR * 0.6f
                ),
                radius = maxR * 0.6f,
                center = center
            )
        }

        // Center Bolt Icon Pill
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White)
                .shadow(8.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// COMPOSABLE 3: Google Expressive Equalizer Visualizer (Slide 4)
// ─────────────────────────────────────────────────────────────
@Composable
private fun GoogleExpressiveEqualizer(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_transition")
    
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val barCount = 19
            val barWidth = 4.dp.toPx()
            val spacing = 3.5.dp.toPx()
            val totalWidth = barCount * barWidth + (barCount - 1) * spacing
            val startX = center.x - totalWidth / 2f

            for (i in 0 until barCount) {
                // Multi-frequency harmonic wave algorithm (like Google Recorder / Pixel Sound)
                val factor = sin(waveOffset + (i * 0.45f)) * 0.5f + 0.5f
                val harmonic = cos(waveOffset * 1.5f + (i * 0.3f)) * 0.3f
                val normalizedHeight = (factor + harmonic).coerceIn(0.15f, 1.0f)

                val barH = (size.height * 0.38f) * normalizedHeight
                val bx = startX + i * (barWidth + spacing)
                val by = center.y - barH / 2f

                // Google Gradient Color from White to Sapphire Blue
                val barColor = if (i in 7..11) Color(0xFF3B82F6) else Color.White

                drawRoundRect(
                    color = barColor.copy(alpha = 0.3f + normalizedHeight * 0.65f),
                    topLeft = Offset(bx, by),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }

        // Floating Minimalist PiP Play Pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceBadge)
                .border(1.dp, BorderBadge, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "320kbps Studio Master",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextZinc100
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Reusable Bento Card Component (Pixel-Perfect Specular 1px Border)
// ─────────────────────────────────────────────────────────────
@Composable
private fun BentoCard(
    item: FeatureItem,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceGlassCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.04f)
                )
            )
        ),
        modifier = modifier.height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceBadge)
                    .border(1.dp, BorderBadge, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = TextZinc100,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Text Info: Single-line bounds with Ellipsis
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextZinc100,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextZinc500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SLIDE 2: 1,000+ Platforms Supported
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlidePlatformsGrid(platforms: List<PlatformItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceBadge,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderBadge)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "UNIVERSAL COMPATIBILITY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "1,000+ Platforms Supported",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextWhite,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Download, stream, or extract from any major platform in pristine quality with zero watermark barriers.",
            fontSize = 13.sp,
            color = TextZinc400,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            platforms.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceGlassCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlassCard),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(item.color)
                            )
                            Text(
                                text = item.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextZinc100
                            )
                        }
                        Text(
                            text = item.tag,
                            fontSize = 10.sp,
                            color = TextZinc500
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SLIDE 3: Turbo Engine & Speed with Google Expressive Stream
// ─────────────────────────────────────────────────────────────
@Composable
private fun SlideTurboEngine() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            GoogleExpressiveSpeedStream(
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 6.dp)
            )

            Text(
                text = "16x Multi-Thread Turbo",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                letterSpacing = (-0.3).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Aria2c multi-connection acceleration splits heavy 4K/8K video into parallel channels with automatic HTTP Range resume.",
                fontSize = 12.sp,
                color = TextZinc400,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearFeatureRow(
                icon = Icons.Default.Speed,
                title = "16 Parallel Stream Connections",
                desc = "Maximizes line bandwidth and eliminates socket timeouts"
            )
            LinearFeatureRow(
                icon = Icons.Default.Sync,
                title = "HTTP Range Auto-Resume",
                desc = "Reconnection continues exactly where it left off on dropout"
            )
            LinearFeatureRow(
                icon = Icons.Default.HighQuality,
                title = "Lossless FFmpeg Merging",
                desc = "High-definition video and audio combined into universal MP4"
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SLIDE 4: Media Studio & Control with Google Expressive Wave
// ─────────────────────────────────────────────────────────────
@Composable
private fun SlideMediaStudio() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            GoogleExpressiveEqualizer(
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 6.dp)
            )

            Text(
                text = "Floating PiP, MP3 & Trimmer",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                letterSpacing = (-0.3).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Stream links ad-free with fluid Picture-in-Picture, extract 320kbps studio audio, or trim custom clips directly into storage.",
                fontSize = 12.sp,
                color = TextZinc400,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearFeatureRow(
                icon = Icons.Default.PlayArrow,
                title = "Zero-Buffer Ad-Free Stream",
                desc = "Play any link instantly with floating background PiP"
            )
            LinearFeatureRow(
                icon = Icons.Default.MusicNote,
                title = "320kbps Studio Audio Extractor",
                desc = "Convert any music video to high-fidelity MP3"
            )
            LinearFeatureRow(
                icon = Icons.Default.ContentCut,
                title = "Precision Clip Trimmer",
                desc = "Trim and save custom audio or video seconds directly"
            )
        }
    }
}

@Composable
private fun LinearFeatureRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceGlassCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlassCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceBadge)
                    .border(1.dp, BorderBadge, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextZinc100,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextZinc100,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = TextZinc500,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
