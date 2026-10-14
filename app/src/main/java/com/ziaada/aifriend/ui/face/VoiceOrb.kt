package com.ziaada.aifriend.ui.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ziaada.aifriend.domain.FaceState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Modern animated voice orb. Replaces the cartoon face with a glowing sphere that reacts to
 * state and (while listening) to the live microphone level.
 */
@Composable
fun VoiceOrb(
    state: FaceState,
    level: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "breathe",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "pulse",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "rotation",
    )
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "wave",
    )

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = min(size.width, size.height) * 0.22f
        val lvl = level.coerceIn(0f, 1.5f)

        val orbRadius = baseRadius * when (state) {
            FaceState.LISTENING -> 1f + lvl * 0.28f
            FaceState.SPEAKING -> breathe * (1f + lvl * 0.12f)
            FaceState.THINKING -> 0.96f
            FaceState.SURPRISED -> 1.1f
            FaceState.HAPPY -> breathe * 1.04f
            FaceState.IDLE -> breathe
        }

        val tint = if (state == FaceState.LISTENING) Color(0xFF59DCCB) else Color(0xFF63BFE1)

        // Ambient breathing aura.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0f)),
                center = center,
                radius = orbRadius * 2.1f,
            ),
            radius = orbRadius * 2.1f,
            center = center,
        )

        // Expanding pulse rings while listening.
        if (state == FaceState.LISTENING) {
            for (i in 0..2) {
                val phase = (pulse + i / 3f) % 1f
                drawCircle(
                    color = tint.copy(alpha = (1f - phase) * 0.35f),
                    radius = orbRadius * (1.15f + phase * 0.95f),
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
            }
        }

        // Rotating arc while thinking.
        if (state == FaceState.THINKING) {
            val arcRadius = orbRadius * 1.35f
            drawArc(
                color = Color(0xFF7FDCEB),
                startAngle = rotation,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color(0xFF7FDCEB).copy(alpha = 0.35f),
                startAngle = rotation + 180f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Core orb.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF8FF6E8), Color(0xFF2FA9C9), Color(0xFF12506F)),
                center = Offset(center.x - orbRadius * 0.3f, center.y - orbRadius * 0.35f),
                radius = orbRadius * 1.4f,
            ),
            radius = orbRadius,
            center = center,
        )
        // Rim highlight.
        drawCircle(
            color = Color.White.copy(alpha = if (state == FaceState.LISTENING) 0.30f else 0.18f),
            radius = orbRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
        // Specular glint.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)),
                center = Offset(center.x - orbRadius * 0.35f, center.y - orbRadius * 0.4f),
                radius = orbRadius * 0.5f,
            ),
            radius = orbRadius * 0.5f,
            center = Offset(center.x - orbRadius * 0.35f, center.y - orbRadius * 0.4f),
        )

        // Waveform bars across the orb while speaking (or reacting to mic while listening).
        if (state == FaceState.SPEAKING || state == FaceState.LISTENING) {
            drawWaveform(center, orbRadius, wave, lvl, state)
        }
    }
}

private fun DrawScope.drawWaveform(
    center: Offset,
    orbRadius: Float,
    wave: Float,
    level: Float,
    state: FaceState,
) {
    val bars = 9
    val span = orbRadius * 1.3f
    val spacing = span / (bars - 1)
    val startX = center.x - span / 2f
    val energy = if (state == FaceState.LISTENING) (0.35f + level) else 0.9f
    val color = Color(0xFF0B2C42).copy(alpha = 0.75f)
    for (i in 0 until bars) {
        val x = startX + i * spacing
        val phase = wave + i * 0.55f
        val factor = 0.25f + 0.75f * abs(sin(phase))
        val halfHeight = orbRadius * 0.42f * factor * energy
        drawLine(
            color = color,
            start = Offset(x, center.y - halfHeight),
            end = Offset(x, center.y + halfHeight),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
