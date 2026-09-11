package com.draftlock.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Identity: Ink Vault — typewriter + safe. Pro tool, game HUD.
// Palette anchors from brief's canopy: ink paper + vault steel + lock lime.
// 4–6 hex core: Ink #080808, Panel #151515, Paper #F4F4F0, Lime #B7FF4A, Steel #00E5FF, Alert #FF2E93, Gold #FFC93C
private val Bg = Color(0xFF09090B) // vault ink
private val Panel = Color(0xFF141418) // steel panel
private val PanelElevated = Color(0xFF1E1E22) // brushed steel
private val Line = Color(0xFF2A2A2E) // hairline vault seam
private val Ink = Color(0xFFF4F4F0) // paper
private val Muted = Color(0xFFA1A1A3)
private val MutedAlt = Color(0xFF8B8B86)
private val Accent = Color(0xFFB7FF4A) // lock lime — the one memorable accent, keep quiet elsewhere
private val AccentPressed = Color(0xFF9FE040)
private val NeonCyan = Color(0xFF22DDFF) // steel blue — subtle, not acid
private val NeonPink = Color(0xFFFF2E93) // alert — only for BLOCKED
private val Gold = Color(0xFFFFC93C)
private val XpGradientStart = Color(0xFFB7FF4A)
private val XpGradientEnd = Color(0xFF22DDFF)

private val DraftLockDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    primaryContainer = Accent,
    onPrimaryContainer = Color.Black,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    secondaryContainer = PanelElevated,
    onSecondaryContainer = Ink,
    tertiary = NeonPink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2A1020),
    onTertiaryContainer = NeonPink,
    background = Bg,
    onBackground = Ink,
    surface = Bg,
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Line.copy(alpha = 0.5f)
)

private val DisplayFont = androidx.compose.ui.text.font.FontFamily.SansSerif // will feel like Space Grotesk — geometric, typewriter-ish
private val MonoFont = androidx.compose.ui.text.font.FontFamily.Monospace // vault HUD mono
val DraftLockTypography = Typography(
    displaySmall = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp),
    titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.2).sp),
    titleSmall = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp),
    labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = MonoFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
    labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = MonoFont, fontSize = 12.sp, letterSpacing = 0.4.sp),
    bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontSize = 13.sp, lineHeight = 18.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun DraftLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DraftLockDarkColorScheme,
        typography = DraftLockTypography,
        content = content
    )
}

// Animation specs – instant, no stagger delay (fix lag report)
object DraftLockMotion {
    val SpringMedium = spring<Float>(dampingRatio = 0.9f, stiffness = 500f)
    val SpringGentle = spring<Float>(dampingRatio = 0.9f, stiffness = 400f)
    val SpringBouncy = spring<Float>(dampingRatio = 0.85f, stiffness = 600f)
    val EaseOut = tween<Float>(140, easing = EaseOutCubic)
    val EaseInOut = tween<Float>(160, easing = EaseInOutCubic)
    val QuickSnap = tween<Float>(110, easing = LinearOutSlowInEasing)
    const val StaggerMs = 0 // no delay – instant
    const val HeroDuration = 180
}

// Infinite pulse for cursor/active states
@Composable
fun rememberCursorPulse(): InfiniteTransition {
    return rememberInfiniteTransition(label = "cursorPulse")
}

// Colors exposed for prototype helpers + Game vibe
object DraftLockColors {
    val bg = Bg
    val panel = Panel
    val panelElevated = PanelElevated
    val line = Line
    val ink = Ink
    val muted = Muted
    val mutedAlt = MutedAlt
    val accent = Accent
    val accentPressed = AccentPressed
    val neonCyan = NeonCyan
    val neonPink = NeonPink
    val gold = Gold
    val xpStart = XpGradientStart
    val xpEnd = XpGradientEnd
    // game gradients
    val bossGradient = listOf(Color(0xFF2A1020), Color(0xFF151515))
    val questGradient = listOf(Color(0xFF142010), Color(0xFF151515))
}
