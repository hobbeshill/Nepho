package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared synchronized lighting phase for all calming borders across Nepho:
 * - [breath]: 0f..1f smooth cosine-eased pulse (~3.8s half-cycle)
 * - [sweep]: 0f..1f continuous directional highlight glide (~5.2s full loop)
 */
@Immutable
data class CalmLightingPhase(
    val breath: Float = 0.5f,
    val sweep: Float = 0.25f
)

val LocalCalmLightingPhase = staticCompositionLocalOf { CalmLightingPhase() }

enum class CalmBorderIntensity {
    /** New/unread notifications, active Inbox header/bar, and Soothing Quotes card. */
    ACTIVE,
    /** Reviewed notifications, app group headers, Clock, Music, Calendar, Vault, Drawer, Minimized bars, Dock. */
    AMBIENT,
    /** Nested pills, chips, letter jumpers, and secondary compact surfaces. */
    SUBTLE
}

/**
 * Runs a single top-level infinite animation clock so every border on screen
 * breathes and sweeps smoothly in visual harmony without spawning dozens of
 * independent transitions inside scrolling lists.
 */
@Composable
fun ProvideCalmLightingClock(
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "nepho_shared_calm_lighting")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nepho_calm_breath"
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nepho_calm_sweep"
    )

    CompositionLocalProvider(
        LocalCalmLightingPhase provides CalmLightingPhase(breath = breath, sweep = sweep),
        content = content
    )
}

/**
 * Draws a slow breathing glow paired with a gentle border light sweep along a rounded border
 * (or top edge when [topEdgeOnly] is true, e.g., for the bottom shortcut dock).
 */
fun Modifier.calmingLitBorder(
    accentColor: Color,
    cornerRadius: Dp = 16.dp,
    strokeWidth: Dp = 1.2.dp,
    intensity: CalmBorderIntensity = CalmBorderIntensity.AMBIENT,
    topEdgeOnly: Boolean = false
): Modifier = composed {
    val phase = LocalCalmLightingPhase.current
    val highlightColor = lerp(accentColor, Color.White, 0.44f)

    val (baseMinAlpha, baseMaxAlpha, sweepPeakAlpha, glowAlpha, glowSpreadMultiplier) = when (intensity) {
        CalmBorderIntensity.ACTIVE -> BorderIntensitySpec(
            baseMinAlpha = 0.36f,
            baseMaxAlpha = 0.72f,
            sweepPeakAlpha = 0.92f,
            glowAlpha = 0.24f,
            glowSpreadMultiplier = 2.6f
        )
        CalmBorderIntensity.AMBIENT -> BorderIntensitySpec(
            baseMinAlpha = 0.22f,
            baseMaxAlpha = 0.46f,
            sweepPeakAlpha = 0.66f,
            glowAlpha = 0.13f,
            glowSpreadMultiplier = 2.0f
        )
        CalmBorderIntensity.SUBTLE -> BorderIntensitySpec(
            baseMinAlpha = 0.16f,
            baseMaxAlpha = 0.34f,
            sweepPeakAlpha = 0.48f,
            glowAlpha = 0.08f,
            glowSpreadMultiplier = 1.6f
        )
    }

    drawWithContent {
        drawContent()
        if (size.width <= 0f || size.height <= 0f) return@drawWithContent

        val breath = phase.breath.coerceIn(0f, 1f)
        val sweep = phase.sweep.coerceIn(0f, 1f)
        val baseAlpha = baseMinAlpha + (baseMaxAlpha - baseMinAlpha) * breath
        val strokePx = strokeWidth.toPx().coerceAtLeast(1f)

        if (topEdgeOnly) {
            val y = strokePx / 2f
            val centerX = (-0.25f + sweep * 1.5f) * size.width
            val halfSpan = size.width * 0.36f
            val brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.0f to accentColor.copy(alpha = baseAlpha),
                    0.35f to accentColor.copy(alpha = (baseAlpha + 0.14f).coerceAtMost(1f)),
                    0.50f to highlightColor.copy(alpha = sweepPeakAlpha),
                    0.65f to accentColor.copy(alpha = (baseAlpha + 0.14f).coerceAtMost(1f)),
                    1.0f to accentColor.copy(alpha = baseAlpha)
                ),
                startX = centerX - halfSpan,
                endX = centerX + halfSpan
            )
            // Soft top bloom
            drawLine(
                color = accentColor.copy(alpha = glowAlpha * (0.55f + 0.45f * breath)),
                start = Offset(0f, y + strokePx * 0.5f),
                end = Offset(size.width, y + strokePx * 0.5f),
                strokeWidth = strokePx * glowSpreadMultiplier
            )
            drawLine(
                brush = brush,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokePx
            )
            return@drawWithContent
        }

        val radiusPx = cornerRadius.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
        val halfStroke = strokePx / 2f
        val rectTopLeft = Offset(halfStroke, halfStroke)
        val rectSize = Size(
            width = (size.width - strokePx).coerceAtLeast(1f),
            height = (size.height - strokePx).coerceAtLeast(1f)
        )
        val cr = CornerRadius(radiusPx, radiusPx)

        // 1. Soft breathing rim bloom around the border
        val bloomStrokePx = strokePx * glowSpreadMultiplier
        drawRoundRect(
            color = accentColor.copy(alpha = glowAlpha * (0.50f + 0.50f * breath)),
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = cr,
            style = Stroke(width = bloomStrokePx)
        )

        // 2. Smoothly gliding diagonal light sweep around the perimeter
        val angleRad = sweep * 2.0 * PI
        val dx = cos(angleRad).toFloat()
        val dy = sin(angleRad).toFloat()
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val rx = size.width * 0.65f
        val ry = size.height * 0.65f
        val start = Offset(cx - dx * rx, cy - dy * ry)
        val end = Offset(cx + dx * rx, cy + dy * ry)

        val sweepBrush = Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to accentColor.copy(alpha = baseAlpha),
                0.34f to accentColor.copy(alpha = (baseAlpha + 0.08f).coerceAtMost(1f)),
                0.52f to highlightColor.copy(alpha = sweepPeakAlpha * (0.78f + 0.22f * breath)),
                0.70f to accentColor.copy(alpha = (baseAlpha + 0.08f).coerceAtMost(1f)),
                1.0f to accentColor.copy(alpha = baseAlpha)
            ),
            start = start,
            end = end
        )

        drawRoundRect(
            brush = sweepBrush,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = cr,
            style = Stroke(width = strokePx)
        )
    }
}

private data class BorderIntensitySpec(
    val baseMinAlpha: Float,
    val baseMaxAlpha: Float,
    val sweepPeakAlpha: Float,
    val glowAlpha: Float,
    val glowSpreadMultiplier: Float
)
