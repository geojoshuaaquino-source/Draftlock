package com.draftlock.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF080808)
private val Panel = Color(0xFF151515)
private val PanelElevated = Color(0xFF1E1E1E)
private val Line = Color(0xFF30302D)
private val Ink = Color(0xFFF4F4F0)
private val Muted = Color(0xFFA0A0A0) // improved contrast from 0xFF8B8B86 for accessibility
private val MutedAlt = Color(0xFF8B8B86)
private val Accent = Color(0xFFB7FF4A) // lime XP
private val AccentPressed = Color(0xFF9FE040)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPink = Color(0xFFFF2E93)
private val Gold = Color(0xFFFFC93C)
private val XpGradientStart = Color(0xFFB7FF4A)
private val XpGradientEnd = Color(0xFF00E5FF)

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

private val DisplayFont = androidx.compose.ui.text.font.FontFamily.SansSerif
private val MonoFont = androidx.compose.ui.text.font.FontFamily.Monospace
val DraftLockTypography = Typography(
    headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = (-0.5).sp),
    titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = MonoFont, letterSpacing = 0.4.sp),
    bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = DisplayFont)
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
