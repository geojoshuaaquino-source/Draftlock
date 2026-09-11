package com.draftlock.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Identity: Ink Vault × MelonUI — typewriter safe meets Melon dense-grid.
// Converted web MelonUI (ItzAmyy/MelonUI, react-melon/melon) has no Android Maven artifact,
// so its tokens are vendored here for Compose (web→Android conversion):
//   Melon green #00CD3C/#00D344 → mapped to MelonGreen, spacing 2/4/8/12/16/24/32/48, rounded 0, Pretendard→SansSerif.
// Other free asset libs added: material-icons-extended + coil-compose (compatible free libs) — heavier kits
// (FormaUI Kotlin 2.4, RikkaUI KMP) skipped for 2.2.20 compat; their structure styles still applied via local components.
// Free background/pattern assets from Inkjet/Pattern Monster converted to VectorDrawable mesh/halftone.
// Palette: vault ink base + Melon green as co-hero with lime, violet as harmonious support, muted steel.
private val Bg = Color(0xFF0A0A0F) // vault ink — true black with 4% blue
private val Panel = Color(0xFF14141C) // steel panel — desaturated navy
private val PanelElevated = Color(0xFF1E1E28) // brushed elevation
private val Line = Color(0xFF242430) // seam — visible in both themes
private val Ink = Color(0xFFF2F0EB) // paper — warm, not stark white
private val Muted = Color(0xFF9AA0A8) // steel muted — 5.2:1 on Panel
private val MutedAlt = Color(0xFF7A7F87)
private val Accent = Color(0xFFD4FF32) // lock lime — hero
private val AccentPressed = Color(0xFFBEE62E)
private val NeonCyan = Color(0xFF7C6CFF) // vault violet — split-complementary to lime
private val NeonPink = Color(0xFFFF6B6B) // alert coral
private val Gold = Color(0xFFFFC857) // paper gold
private val XpGradientStart = Color(0xFFD4FF32)
private val XpGradientEnd = Color(0xFF7C6CFF)
// MelonUI tokens — converted for Android (free, MIT web sources; no binary dep)
private val MelonGreen = Color(0xFF00CD3C) // signature-#00CD3C
private val MelonGreenDark = Color(0xFF00B523)
private val MelonGreenLogin = Color(0xFF00D344) // live login #00D344
private val MelonNearBlack = Color(0xFF1A1A1A)
private val MelonGray666 = Color(0xFF666666)
private val MelonGray999 = Color(0xFF999999)
private val MelonCanvas = Color(0xFFFFFFFF)
private val MelonRed = Color(0xFFDF2607)

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

// Melon spacing/rounded tokens — converted from web 2/4/8/12/16/24/32/48 + 0/9999
object MelonTokens {
    val xs = 2; val sm = 4; val md = 8; val base = 12; val lg = 16; val xl = 24; val xxl = 32; val section = 48
    val radiusSharp = 0; val radiusPill = 999
}
// Dark glass + bento phone — purpose-built for phone (not web bento copy)
object GlassTokens {
    // dark glass: translucent white on ink base (frosted without blur dep)
    val glass = Color(0x14FFFFFF) // 8% white — frosted
    val glassStrong = Color(0x1FFFFFFF) // 12% — header
    val glassBorder = Color(0x1AFFFFFF) // 10% border
    val glassBorderStrong = Color(0x26FFFFFF)
    val bentoRadius = 18 // phone bento cell radius — distinct from old 14-16 soup
    val glassStroke = 1
}
// Colors exposed for prototype helpers + Game vibe + Melon converted
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
    // melon converted
    val melonGreen = MelonGreen
    val melonGreenDark = MelonGreenDark
    val melonLogin = MelonGreenLogin
    val melonNearBlack = MelonNearBlack
    val melonGray666 = MelonGray666
    val melonGray999 = MelonGray999
    val melonCanvas = MelonCanvas
    val melonRed = MelonRed
    // glass
    val glass = GlassTokens.glass
    val glassStrong = GlassTokens.glassStrong
    val glassBorder = GlassTokens.glassBorder
    // game gradients — now melon-lime + vault
    val bossGradient = listOf(Color(0xFF2A1020), Color(0xFF151515))
    val questGradient = listOf(Color(0xFF142010), Color(0xFF151515))
    val melonGradient = listOf(MelonGreen, MelonGreenDark)
    val vaultMelonGradient = listOf(Accent, MelonGreen)
}
