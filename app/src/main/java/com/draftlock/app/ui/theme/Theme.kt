package com.draftlock.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun DraftLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DraftLockDarkColorScheme,
        typography = Typography(),
        content = content
    )
}

// Animation specs - centralized for consistency (game vibe: Stripe/Linear/Apple)
object DraftLockMotion {
    val SpringMedium = spring<Float>(dampingRatio = 0.8f, stiffness = 300f)
    val SpringGentle = spring<Float>(dampingRatio = 0.85f, stiffness = 200f)
    val SpringBouncy = spring<Float>(dampingRatio = 0.6f, stiffness = 400f) // for level-up pops
    val EaseOut = tween<Float>(300, easing = EaseOutCubic)
    val EaseInOut = tween<Float>(400, easing = EaseInOutCubic)
    val QuickSnap = tween<Float>(200, easing = LinearOutSlowInEasing)
    const val StaggerMs = 45 // ms per card for bento stagger
    const val HeroDuration = 600
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
