package com.raoprime.downloadivo.ui.theme

import androidx.compose.ui.graphics.Color

// Obsidian & Emerald Master Design Tokens (Apple / Linear Caliber)
val CanvasDark = Color(0xFF09090B)          // Deep Obsidian Background
val SurfaceDark = Color(0xFF121215)         // Primary Container Surface
val SurfaceElevated = Color(0xFF18181C)     // Secondary Glass / Elevated Surface
val SurfaceHighlight = Color(0xFF222228)    // Hover / Active Surface

// Borders & Dividers
val BorderHairline = Color(0xFF1E1E24)      // Hairline boundary (subtle, 0.5dp/1dp)
val BorderSubtle = Color(0xFF2E2E36)        // Focused / Hover boundary

// Core Brand Accents
val AccentEmerald = Color(0xFF10B981)       // Vibrant Electric Emerald (Pure Action)
val AccentEmeraldGlow = Color(0x3310B981)   // Emerald Glow (20% alpha)
val AccentEmeraldMuted = Color(0x1A10B981)  // Emerald Tint (10% alpha)

// Text Tokens
val TextCrisp = Color(0xFFFAFAFA)           // Primary Heading White
val TextSecondary = Color(0xFFA1A1AA)       // High-readability Body Silver
val TextTertiary = Color(0xFF71717A)        // Muted Captions & Details
val TextQuaternary = Color(0xFF52525B)      // Sub-labels & Placeholders

// Status Tokens
val StatusSuccess = Color(0xFF10B981)
val StatusError = Color(0xFFEF4444)
val StatusErrorBg = Color(0x26EF4444)

// Platform Brand Favicon Colors
val BrandYouTube = Color(0xFFFF0000)
val BrandInstagram = Color(0xFFE1306C)
val BrandTikTok = Color(0xFF00F2FE)
val BrandTwitter = Color(0xFFE7E9EA)
val BrandSpotify = Color(0xFF1DB954)
val BrandReddit = Color(0xFFFF4500)
val BrandFacebook = Color(0xFF1877F2)

// Compatibility Aliases
val BackgroundDark = CanvasDark
val SurfaceCardDark = SurfaceDark
val CardBorderDark = BorderHairline
val CardBorderFocus = BorderSubtle
val PrimaryEmerald = AccentEmerald
val PrimaryEmeraldDark = Color(0xFF059669)
val TextPrimary = TextCrisp
val TextMuted = TextSecondary
val TextSubtle = TextTertiary
val BadgeBackground = SurfaceElevated
val ErrorRed = StatusError
val ErrorContainer = StatusErrorBg
val AccentBlue = Color(0xFF3B82F6)
