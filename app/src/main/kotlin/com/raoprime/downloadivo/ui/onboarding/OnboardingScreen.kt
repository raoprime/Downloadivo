package com.raoprime.downloadivo.ui.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raoprime.downloadivo.R
import com.raoprime.downloadivo.ui.theme.HapticUtil
import com.raoprime.downloadivo.ui.theme.Typography
import kotlinx.coroutines.launch

// ── Obsidian Studio Design Tokens (Apple / Linear Tier) ───────
private val CanvasVoid = Color(0xFF060608)
private val SurfaceGlassCard = Color(0x08FFFFFF)      // bg-white/[0.03]
private val BorderGlassCard = Color(0x12FFFFFF)       // border-white/[0.07]
private val SurfaceBadge = Color(0x0FFFFFFF)          // bg-white/[0.06]
private val BorderBadge = Color(0x14FFFFFF)           // border-white/[0.08]
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
        // Subtle Ambient Background Mesh (Apple / Linear style soft mesh blur)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .size(280.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x266366F1), // indigo-500 at 15%
                            Color(0x0C6366F1),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top Header Bar ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Mark: White "D" Badge + Downloadivo Typography
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "D",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
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

            // ── Bottom Pagination & Tactile CTA ─────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Expanding Animated Indicator Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPages) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 6.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "dot_width"
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

                // Tactile CTA Button with micro-press physics
                val isLastPage = pagerState.currentPage == totalPages - 1
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.98f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "cta_press_scale"
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
// SLIDE 1: Hero Visual & Fixed 2x2 Bento Feature Grid
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
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Borderless Hero Stage (Ready for <Rive /> / <Lottie /> asset)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Sleek Glass Core Placeholder
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glassmorphic Outer Card with 1px Specular Border
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.09f),
                                    Color.White.copy(alpha = 0.02f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.18f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            ),
                            RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Center Floating Brand Emblem
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .shadow(12.dp, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "D",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }
            }

            Text(
                text = "The Next-Gen Media Suite",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Fetch, stream, convert, and manage high-definition media across platforms without limits.",
                fontSize = 13.sp,
                color = TextZinc400,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Fixed 2x2 Bento Grid — Zero Awkward Line Wrapping
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
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
// SLIDE 3: Turbo Engine & Speed
// ─────────────────────────────────────────────────────────────
@Composable
private fun SlideTurboEngine() {
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
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "UNTHROTTLED PIPELINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "16x Multi-Thread Turbo",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextWhite,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Aria2c multi-connection acceleration splits heavy 4K/8K video into parallel channels with automatic HTTP Range resume.",
            fontSize = 13.sp,
            color = TextZinc400,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

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
// SLIDE 4: Media Studio & Control
// ─────────────────────────────────────────────────────────────
@Composable
private fun SlideMediaStudio() {
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
                    imageVector = Icons.Default.PictureInPicture,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "TOTAL CONTROL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Floating PiP, MP3 & Trimmer",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextWhite,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Stream links ad-free with fluid Picture-in-Picture, extract 320kbps studio audio, or trim custom clips directly into storage.",
            fontSize = 13.sp,
            color = TextZinc400,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
