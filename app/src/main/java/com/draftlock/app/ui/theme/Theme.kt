package com.draftlock.app.ui.theme

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// DraftLock visual system: premium dark glass, blue highlights, restrained bento surfaces.
private val Bg = Color(0xFF050A14)
private val BgBlue = Color(0xFF08162B)
private val Panel = Color(0xCC0D1B31)
private val PanelElevated = Color(0xE6162945)
private val Line = Color(0x663A6EA8)
private val Ink = Color(0xFFF4F8FF)
private val Muted = Color(0xFF9BAFC8)
private val MutedAlt = Color(0xFF7187A5)
private val Accent = Color(0xFF3D9BFF)
private val AccentPressed = Color(0xFF257FE6)
private val NeonCyan = Color(0xFF64D8FF)
private val NeonPink = Color(0xFFFF6B8A)
private val Gold = Color(0xFFFFD166)
private val XpGradientStart = Color(0xFF4BB3FF)
private val XpGradientEnd = Color(0xFF586DFF)

// Legacy compatibility tokens kept for existing screens/components.
private val MelonGreen = Accent
private val MelonGreenDark = AccentPressed
private val MelonGreenLogin = Accent
private val MelonNearBlack = Color(0xFF0A1220)
private val MelonGray666 = Color(0xFF66758C)
private val MelonGray999 = Color(0xFF9AA9BE)
private val MelonCanvas = Color.White
private val MelonRed = NeonPink

private val DraftLockDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF164B82),
    onPrimaryContainer = Ink,
    secondary = NeonCyan,
    onSecondary = Color(0xFF041019),
    secondaryContainer = Color(0xFF123A5A),
    onSecondaryContainer = Ink,
    tertiary = NeonPink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4A2030),
    onTertiaryContainer = Color(0xFFFFD9E1),
    background = Bg,
    onBackground = Ink,
    surface = Bg,
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Color(0x443A6EA8)
)

private val DisplayFont = FontFamily.SansSerif
private val MonoFont = FontFamily.Monospace
val DraftLockTypography = Typography(
    displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontFamily = MonoFont, fontSize = 12.sp, letterSpacing = 0.4.sp),
    bodySmall = TextStyle(fontFamily = DisplayFont, fontSize = 13.sp, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontFamily = DisplayFont, fontSize = 14.sp, lineHeight = 20.sp)
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

object DraftLockMotion {
    val SpringMedium = spring<Float>(dampingRatio = 0.88f, stiffness = 520f)
    val SpringGentle = spring<Float>(dampingRatio = 0.92f, stiffness = 380f)
    val SpringBouncy = spring<Float>(dampingRatio = 0.82f, stiffness = 580f)
    val EaseOut = tween<Float>(220, easing = EaseOutCubic)
    val EaseInOut = tween<Float>(260, easing = EaseInOutCubic)
    val QuickSnap = tween<Float>(140, easing = LinearOutSlowInEasing)
    val Micro = tween<Float>(160, easing = EaseOutCubic)
    const val StaggerMs = 42
    const val HeroDuration = 520
    const val SectionDuration = 420
}

@Composable
fun rememberCursorPulse(): InfiniteTransition {
    return rememberInfiniteTransition(label = "cursorPulse")
}

object MelonTokens {
    val xs = 2; val sm = 4; val md = 8; val base = 12; val lg = 16; val xl = 24; val xxl = 32; val section = 48
    val radiusSharp = 0; val radiusPill = 999
}

object GlassTokens {
    // Layered blue-tinted glass: visible against the dark blue backdrop instead of flat gray cards.
    val glass = Color(0x241D5A91)
    val glassStrong = Color(0x3D1D6AA8)
    val glassBorder = Color(0x524E9DDA)
    val glassBorderStrong = Color(0x806BB6FF)
    val bentoRadius = 18
    val glassStroke = 1
}

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
    val melonGreen = MelonGreen
    val melonGreenDark = MelonGreenDark
    val melonLogin = MelonGreenLogin
    val melonNearBlack = MelonNearBlack
    val melonGray666 = MelonGray666
    val melonGray999 = MelonGray999
    val melonCanvas = MelonCanvas
    val melonRed = MelonRed
    val glass = GlassTokens.glass
    val glassStrong = GlassTokens.glassStrong
    val glassBorder = GlassTokens.glassBorder
    val bossGradient = listOf(Color(0xFF241626), Color(0xFF0D1727))
    val questGradient = listOf(Color(0xFF0D3152), Color(0xFF10182A))
    val melonGradient = listOf(MelonGreen, MelonGreenDark)
    val vaultMelonGradient = listOf(Accent, NeonCyan)
    val backgroundGradient = listOf(Bg, BgBlue, Color(0xFF071326))
}
