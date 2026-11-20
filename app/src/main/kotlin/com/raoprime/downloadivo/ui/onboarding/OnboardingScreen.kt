package com.raoprime.downloadivo.ui.onboarding

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.raoprime.downloadivo.R
import com.raoprime.downloadivo.ui.theme.BorderHairline
import com.raoprime.downloadivo.ui.theme.BorderSubtle
import com.raoprime.downloadivo.ui.theme.CanvasDark
import com.raoprime.downloadivo.ui.theme.HapticUtil
import com.raoprime.downloadivo.ui.theme.SurfaceDark
import com.raoprime.downloadivo.ui.theme.SurfaceElevated
import com.raoprime.downloadivo.ui.theme.SurfaceHighlight
import com.raoprime.downloadivo.ui.theme.TextCrisp
import com.raoprime.downloadivo.ui.theme.TextSecondary
import com.raoprime.downloadivo.ui.theme.TextTertiary
import com.raoprime.downloadivo.ui.theme.Typography
import kotlinx.coroutines.launch

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
            .background(CanvasDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Ambient background gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SurfaceElevated.copy(alpha = 0.5f),
                            CanvasDark,
                            CanvasDark
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ── Top Header Bar ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Official Brand Logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon_white),
                        contentDescription = "Downloadivo Emblem",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Image(
                        painter = painterResource(id = R.drawable.app_logo_white),
                        contentDescription = "Downloadivo",
                        modifier = Modifier.height(20.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // Step Indicator & Skip Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / $totalPages",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    TextButton(
                        onClick = {
                            HapticUtil.mediumClick(context)
                            onFinish()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Skip",
                            style = Typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Interactive Horizontal Pager ────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> SlideBrandHero()
                    1 -> SlidePlatformShowcase(platforms)
                    2 -> SlideTurboSpeed()
                    3 -> SlideStudioControl()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Bottom Navigation Bar ───────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Expanding Animated Indicator Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPages) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 32.dp else 8.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "pill_width"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isSelected) TextCrisp
                                    else BorderSubtle
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        )
                    }
                }

                // CTA Action Button
                val isLastPage = pagerState.currentPage == totalPages - 1
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLastPage) TextCrisp else SurfaceElevated,
                        contentColor = if (isLastPage) CanvasDark else TextCrisp
                    ),
                    border = if (!isLastPage) androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle) else null
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isLastPage) "Get Started with Downloadivo" else "Continue",
                            style = Typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (isLastPage) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// CINEMATIC 3D HERO VIEW (ExoPlayer Continuous Loop)
// ─────────────────────────────────────────────────────────────
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun Cinematic3DHeroView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.intro_3d}")
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
            .size(210.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(26.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = false
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
}

// ─────────────────────────────────────────────────────────────
// SLIDE 1: Brand Hero with 3D Cinematic Animation
// ─────────────────────────────────────────────────────────────
@Composable
private fun SlideBrandHero() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Real 3D Cinematic Animation Loop
        Cinematic3DHeroView()

        Spacer(modifier = Modifier.height(20.dp))

        // Brand Name Tagline
        Image(
            painter = painterResource(id = R.drawable.app_logo_white),
            contentDescription = "Downloadivo",
            modifier = Modifier.height(30.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "The Next-Gen Ultimate Media Suite",
            style = Typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextCrisp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "All supported platforms unite into one powerful engine. Fetch, stream, convert, and manage high-definition media without limits.",
            style = Typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 4 Core Brand Value Pillars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BrandPill(
                icon = Icons.Default.AllInclusive,
                title = "100% Free",
                subtitle = "No Paywalls",
                modifier = Modifier.weight(1f)
            )
            BrandPill(
                icon = Icons.Default.Shield,
                title = "0 Watermarks",
                subtitle = "Pure Quality",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BrandPill(
                icon = Icons.Default.Lock,
                title = "100% Private",
                subtitle = "Device Direct",
                modifier = Modifier.weight(1f)
            )
            BrandPill(
                icon = Icons.Default.Bolt,
                title = "Ultra Fast",
                subtitle = "Aria2c Multi-Thread",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SLIDE 2: 1,000+ Platforms Supported (Showcase Grid)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlidePlatformShowcase(platforms: List<PlatformItem>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Section Header
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = TextCrisp,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "UNIVERSAL COMPATIBILITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCrisp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "1,000+ Platforms Supported",
            style = Typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextCrisp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Download, stream, or extract from any major platform in pristine quality with zero watermark barriers.",
            style = Typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Visual Platform Chips Grid
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            platforms.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderHairline),
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
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(item.color)
                            )
                            Text(
                                text = item.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextCrisp
                            )
                        }
                        Text(
                            text = item.tag,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextTertiary
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SLIDE 3: Turbo Engine & Speed (Aria2c + 4K/8K HDR)
// ─────────────────────────────────────────────────────────────
@Composable
private fun SlideTurboSpeed() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // High-Tech Speed Orb
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x2210B981),
                            Color(0x0810B981),
                            Color.Transparent
                        )
                    )
                )
                .border(1.dp, Color(0x3310B981), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.size(86.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Turbo Engine",
                        tint = TextCrisp,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "16x Multi-Thread Turbo",
            style = Typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextCrisp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "aria2c High-Speed Chunking & FFmpeg Merging",
            style = Typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Large 4K/8K videos are split into 16 parallel stream channels for maximum bandwidth utilization with automatic HTTP Range resume.",
            style = Typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 10.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Speed Capabilities Breakdown
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CapabilityRow(
                icon = Icons.Default.Bolt,
                title = "16 Concurrent Connections",
                desc = "Bypasses ISP stream bandwidth throttles"
            )
            CapabilityRow(
                icon = Icons.Default.Sync,
                title = "HTTP Range Auto-Resume",
                desc = "Picks up right where it left off on dropout"
            )
            CapabilityRow(
                icon = Icons.Default.HighQuality,
                title = "Lossless FFmpeg Merging",
                desc = "Separated 4K video & audio remuxed into single MP4"
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SLIDE 4: Media Control & Freedom (Player, MP3, Trimmer)
// ─────────────────────────────────────────────────────────────
@Composable
private fun SlideStudioControl() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Media Studio Orb
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x223B82F6),
                            Color(0x083B82F6),
                            Color.Transparent
                        )
                    )
                )
                .border(1.dp, Color(0x333B82F6), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.size(86.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "Studio Control",
                        tint = TextCrisp,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Total Media Freedom",
            style = Typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextCrisp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Floating PiP, Studio MP3 & Trimmer",
            style = Typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Stream links ad-free with fluid Picture-in-Picture, extract 320kbps lossless audio, or trim custom clips directly into your phone gallery.",
            style = Typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 10.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Control Capabilities Breakdown
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CapabilityRow(
                icon = Icons.Default.PlayArrow,
                title = "Zero-Buffer Ad-Free Stream",
                desc = "Play any link instantly with floating background PiP"
            )
            CapabilityRow(
                icon = Icons.Default.MusicNote,
                title = "320kbps Studio Audio Extractor",
                desc = "Convert any music video to high-fidelity MP3"
            )
            CapabilityRow(
                icon = Icons.Default.ContentCut,
                title = "Precision Clip Trimmer",
                desc = "Trim and save custom audio or video seconds directly"
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Reusable UI Components
// ─────────────────────────────────────────────────────────────
@Composable
private fun BrandPill(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderHairline),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SurfaceHighlight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextCrisp,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCrisp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CapabilityRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderHairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextCrisp,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextCrisp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
