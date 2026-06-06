package com.lgclaw.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Refined visual tokens for the chat surface. Inspired by the Agnes-style
 * glassmorphic lavender palette. No pure-AI blue/purple gradient look,
 * no dark surfaces; everything sits on a soft lavender canvas with white
 * paper cards and indigo accents for interactive elements.
 */
@Immutable
data class ChatVisuals(
    val canvasTop: Color,
    val canvasBottom: Color,
    val canvasPattern: Color,
    val paper: Color,
    val glassWhite: Color,
    val ink: Color,
    val muted: Color,
    val whisper: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val userBubble: Color,
    val userBubbleInk: Color,
    val assistantInk: Color,
    val toolAccent: Color,
    val systemAccent: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val accent2: Color,
    val liveAccent: Color,
    val liveAccentSoft: Color,
    val liveInk: Color,
    val doneAccent: Color,
    val doneAccentSoft: Color,
    val doneInk: Color,
    val focusRing: Color,
    val rXS: Dp, val rSM: Dp, val rMD: Dp, val rLG: Dp, val rXL: Dp, val rPill: Dp,
    val gap1: Dp, val gap2: Dp, val gap3: Dp, val gap4: Dp, val gap5: Dp, val gap6: Dp,
    val fontDisplay: TextUnit,
    val fontBody: TextUnit,
    val fontLabel: TextUnit,
    val fontMicro: TextUnit,
    val fontMono: TextUnit,
    val lineBody: TextUnit,
    val hairlineStroke: Dp
) {
    val canvas: Color get() = canvasTop
    val shadow: Color get() = Color(0x1A6366F1)
    val rCard: Dp get() = rLG
}

internal val LightVisuals = ChatVisuals(
    canvasTop = Color(0xFFF6F4FB),
    canvasBottom = Color(0xFFECE7F7),
    canvasPattern = Color(0xFFD9D2EE),
    paper = Color(0xFFFFFFFF),
    glassWhite = Color(0xCCFFFFFF),
    ink = Color(0xFF1E1B2E),
    muted = Color(0xFF6B6781),
    whisper = Color(0xFF9C99AB),
    hairline = Color(0xFFE0DDEC),
    hairlineStrong = Color(0xFFC7C0DD),
    userBubble = Color(0xFF1E1B2E),
    userBubbleInk = Color(0xFFFFFFFF),
    assistantInk = Color(0xFF1E1B2E),
    toolAccent = Color(0xFF6366F1),
    systemAccent = Color(0xFF6B6781),
    accent = Color(0xFF6366F1),
    accentSoft = Color(0xFFEEEAFE),
    accentInk = Color(0xFF4338CA),
    accent2 = Color(0xFF3B82F6),
    liveAccent = Color(0xFF0EA5E9),
    liveAccentSoft = Color(0xFFE0F2FE),
    liveInk = Color(0xFF0369A1),
    doneAccent = Color(0xFF10B981),
    doneAccentSoft = Color(0xFFECFDF5),
    doneInk = Color(0xFF065F46),
    focusRing = Color(0xFF6366F1),
    rXS = 4.dp, rSM = 6.dp, rMD = 10.dp, rLG = 14.dp, rXL = 20.dp, rPill = 999.dp,
    gap1 = 4.dp, gap2 = 6.dp, gap3 = 10.dp, gap4 = 14.dp, gap5 = 20.dp, gap6 = 28.dp,
    fontDisplay = 22.sp,
    fontBody = 15.sp,
    fontLabel = 12.sp,
    fontMicro = 11.sp,
    fontMono = 12.sp,
    lineBody = 23.sp,
    hairlineStroke = 1.dp
)

internal val DarkVisuals = LightVisuals.copy(
    canvasTop = Color(0xFF1A1825),
    canvasBottom = Color(0xFF221F2E),
    canvasPattern = Color(0xFF3A3550),
    paper = Color(0xFF2A2738),
    glassWhite = Color(0xE62A2738),
    ink = Color(0xFFEFECF8),
    muted = Color(0xFFB5B0C7),
    whisper = Color(0xFF7E7892),
    hairline = Color(0xFF3A3550),
    hairlineStrong = Color(0xFF5A5470),
    userBubble = Color(0xFFEFECF8),
    userBubbleInk = Color(0xFF1A1825),
    accentSoft = Color(0xFF312E47),
    liveAccentSoft = Color(0xFF1E3A4A),
    doneAccentSoft = Color(0xFF1E3A2C)
)


// ---------------------------------------------------------------------------
// Brutalist "Structural Intelligence" palette. Sharp edges, 100% opacity,
// 2px structural borders, hard offsets instead of shadows.
// ---------------------------------------------------------------------------

internal val BrutalistLightVisuals = ChatVisuals(
    canvasTop = Color(0xFFF9F9F9),
    canvasBottom = Color(0xFFF9F9F9),
    canvasPattern = Color(0xFFE0E0E0),
    paper = Color(0xFFFFFFFF),
    glassWhite = Color(0xFFFFFFFF),
    ink = Color(0xFF1B1B1B),
    muted = Color(0xFF474747),
    whisper = Color(0xFF848484),
    hairline = Color(0xFF000000),
    hairlineStrong = Color(0xFF000000),
    userBubble = Color(0xFFEEEEEE),
    userBubbleInk = Color(0xFF1B1B1B),
    assistantInk = Color(0xFF1B1B1B),
    toolAccent = Color(0xFF0356FF),
    systemAccent = Color(0xFF699000),
    accent = Color(0xFF0356FF),
    accentSoft = Color(0xFFDCE1FF),
    accentInk = Color(0xFFFFFFFF),
    accent2 = Color(0xFFFF5E00),
    liveAccent = Color(0xFF0356FF),
    liveAccentSoft = Color(0xFFDCE1FF),
    liveInk = Color(0xFF001551),
    doneAccent = Color(0xFF699000),
    doneAccentSoft = Color(0xFFB6F700),
    doneInk = Color(0xFF141F00),
    focusRing = Color(0xFF0356FF),
    rXS = 0.dp, rSM = 0.dp, rMD = 0.dp, rLG = 0.dp, rXL = 0.dp, rPill = 0.dp,
    gap1 = 4.dp, gap2 = 6.dp, gap3 = 8.dp, gap4 = 12.dp, gap5 = 16.dp, gap6 = 24.dp,
    fontDisplay = 22.sp,
    fontBody = 15.sp,
    fontLabel = 12.sp,
    fontMicro = 11.sp,
    fontMono = 12.sp,
    lineBody = 22.sp,
    hairlineStroke = 2.dp
)

internal val BrutalistDarkVisuals = BrutalistLightVisuals.copy(
    canvasTop = Color(0xFF1B1B1B),
    canvasBottom = Color(0xFF1B1B1B),
    canvasPattern = Color(0xFF303030),
    paper = Color(0xFF1B1B1B),
    glassWhite = Color(0xFF1B1B1B),
    ink = Color(0xFFF1F1F1),
    muted = Color(0xFFB5B5B5),
    whisper = Color(0xFF848484),
    userBubble = Color(0xFF2A2A2A),
    userBubbleInk = Color(0xFFF1F1F1),
    accentSoft = Color(0xFF001551),
    liveAccentSoft = Color(0xFF001551),
    doneAccentSoft = Color(0xFF141F00),
    hairline = Color(0xFF000000),
    hairlineStrong = Color(0xFF000000)
)

val LocalChatVisuals = compositionLocalOf { LightVisuals }

object ChatSurface {
    val visuals: ChatVisuals
        @Composable @ReadOnlyComposable
        get() = LocalChatVisuals.current

    /** Soft lavender background gradient for the chat canvas. */
    val backgroundBrush: Brush
        @Composable @ReadOnlyComposable
        get() = Brush.verticalGradient(
            listOf(visuals.canvasTop, visuals.canvasBottom)
        )

    /** Subtle accent gradient for live/thinking affordances. */
    val liveBrush: Brush
        @Composable @ReadOnlyComposable
        get() = Brush.horizontalGradient(
            listOf(
                visuals.liveAccentSoft,
                visuals.accentSoft,
                visuals.canvasTop
            )
        )
}

@Composable
fun ProvideChatVisuals(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalChatVisuals provides if (darkTheme) DarkVisuals else LightVisuals,
        content = content
    )
}

@Composable
fun ChatCanvasBackground(modifier: Modifier = Modifier.fillMaxSize()) {
    val v = ChatSurface.visuals
    Canvas(modifier = modifier) {
        val spacing = 22.dp.toPx()
        val radius = 0.9.dp.toPx()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawCircle(
                    color = v.canvasPattern,
                    radius = radius,
                    center = Offset(x, y)
                )
                x += spacing
            }
            y += spacing
        }
    }
}
