package com.ziaada.aifriend.ui.face

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.ziaada.aifriend.domain.FaceState
import kotlin.math.sin

@Composable
fun AiFace(state: FaceState, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "face")
    val blink by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = keyframes {
            durationMillis = 4200
            1f at 0
            1f at 3700
            0.08f at 3800 using FastOutSlowInEasing
            1f at 3920 using FastOutSlowInEasing
            1f at 4200
        }),
        label = "blink",
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "breathe",
    )
    val lookPhase by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2300), RepeatMode.Reverse),
        label = "look",
    )
    val listeningPulse by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "pulse",
    )

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height * 0.49f)
        val base = minOf(size.width * 0.28f, size.height * 0.56f) * breathe
        val radius = when (state) {
            FaceState.SURPRISED -> base * 1.04f
            FaceState.THINKING -> base * 0.98f
            else -> base
        }

        val balloon = Rect(
            left = -size.width * 0.18f,
            top = -size.height * 0.28f,
            right = size.width * 1.18f,
            bottom = size.height * 1.28f,
        )
        drawBalloon(balloon, state, listeningPulse)
        drawAura(center, radius, state, listeningPulse)
        drawFace(center, radius, state, listeningPulse)

        if (state == FaceState.LISTENING) {
            drawListeningRings(center, radius, listeningPulse)
        }

        drawEyes(
            center = center,
            radius = radius,
            blinkScale = when (state) {
                FaceState.SPEAKING -> maxOf(0.2f, blink * 0.9f)
                else -> maxOf(0.12f, blink)
            },
            lookPhase = lookPhase,
            state = state,
        )
        drawBrows(center, radius, state)
        drawMouth(center, radius, state, listeningPulse)
    }
}

private fun DrawScope.drawBalloon(bounds: Rect, state: FaceState, pulse: Float) {
    val edge = if (state == FaceState.LISTENING) Color(0xFF77F1DF) else Color(0xFF7FDCEB)
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF78DFD6), Color(0xFF388FB0), Color(0xFF194F78), Color(0xFF123B61)),
            startY = bounds.top,
            endY = bounds.bottom,
        ),
        topLeft = Offset(bounds.left, bounds.top),
        size = Size(bounds.width, bounds.height),
    )
    drawOval(
        color = Color.White.copy(alpha = 0.075f),
        topLeft = Offset(bounds.left + bounds.width * 0.09f, bounds.top + bounds.height * 0.08f),
        size = Size(bounds.width * 0.72f, bounds.height * 0.78f),
    )
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0f)),
            center = Offset(bounds.left + bounds.width * 0.29f, bounds.top + bounds.height * 0.29f),
            radius = bounds.width * 0.32f,
        ),
        topLeft = Offset(bounds.left, bounds.top),
        size = Size(bounds.width, bounds.height),
    )
    drawOval(
        color = edge.copy(alpha = if (state == FaceState.LISTENING) 0.23f * pulse else 0.14f),
        topLeft = Offset(bounds.left + bounds.width * 0.015f, bounds.top + bounds.height * 0.018f),
        size = Size(bounds.width * 0.97f, bounds.height * 0.96f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
    )
}

private fun DrawScope.drawAura(center: Offset, radius: Float, state: FaceState, pulse: Float) {
    val tint = if (state == FaceState.LISTENING) Color(0xFF59DCCB) else Color(0xFF63BFE1)
    drawCircle(color = tint.copy(alpha = 0.035f), center = center, radius = radius * 1.38f * pulse)
    drawCircle(
        color = tint.copy(alpha = 0.16f),
        center = center,
        radius = radius * 1.11f,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.012f),
    )
}

private fun DrawScope.drawFace(center: Offset, radius: Float, state: FaceState, pulse: Float) {
    val cheekTint = if (state == FaceState.HAPPY) Color(0xFFFFB5A7) else Color(0xFFFFAFA4)
    drawCircle(
        color = cheekTint.copy(alpha = 0.35f),
        center = Offset(center.x - radius * 0.74f, center.y + radius * 0.40f),
        radius = radius * 0.13f,
    )
    drawCircle(
        color = cheekTint.copy(alpha = 0.35f),
        center = Offset(center.x + radius * 0.74f, center.y + radius * 0.40f),
        radius = radius * 0.13f,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.22f * pulse),
        center = Offset(center.x - radius * 0.72f, center.y - radius * 0.58f),
        radius = radius * 0.065f,
    )
}

private fun DrawScope.drawMouth(center: Offset, radius: Float, state: FaceState, pulse: Float) {
    val mouthY = center.y + radius * 0.48f
    if (state == FaceState.SPEAKING) {
        val opening = 0.09f + pulse * 0.10f
        drawOval(
            color = Color(0xFF123D5B),
            topLeft = Offset(center.x - radius * 0.17f, mouthY - radius * opening * 0.5f),
            size = Size(radius * 0.34f, radius * opening),
        )
        drawOval(
            color = Color(0xFFFF9D9A),
            topLeft = Offset(center.x - radius * 0.09f, mouthY + radius * 0.01f),
            size = Size(radius * 0.18f, radius * 0.055f),
        )
    } else {
        val smile = Path().apply {
            moveTo(center.x - radius * 0.28f, mouthY)
            quadraticTo(center.x, mouthY + radius * 0.22f, center.x + radius * 0.28f, mouthY)
        }
        drawPath(
            path = smile,
            color = Color(0xFF123D5B),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.055f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawBrows(center: Offset, radius: Float, state: FaceState) {
    val y = center.y - radius * 0.34f
    val half = radius * 0.19f
    val gap = radius * 0.23f
    val color = Color(0xFF143D60).copy(alpha = 0.86f)
    val browLift = when (state) {
        FaceState.SURPRISED -> radius * 0.05f
        FaceState.HAPPY -> -radius * 0.02f
        FaceState.THINKING -> radius * 0.015f
        else -> 0f
    }
    drawLine(color, Offset(center.x - gap - half, y), Offset(center.x - gap + half, y - browLift), radius * 0.035f, cap = StrokeCap.Round)
    drawLine(color, Offset(center.x + gap - half, y - browLift), Offset(center.x + gap + half, y), radius * 0.035f, cap = StrokeCap.Round)
}

private fun DrawScope.drawListeningRings(center: Offset, radius: Float, pulse: Float) {
    for (i in 1..3) {
        drawCircle(
            color = Color(0xFF29D3FF).copy(alpha = 0.09f / i),
            center = center,
            radius = radius * (1.05f + i * 0.09f) * pulse,
        )
    }
}

private fun DrawScope.drawEyes(
    center: Offset,
    radius: Float,
    blinkScale: Float,
    lookPhase: Float,
    state: FaceState,
) {
    val eyeOffsetX = radius * 0.43f
    val eyeY = center.y + radius * 0.01f
    val eyeWidth = radius * 0.29f
    val eyeHeight = radius * 0.38f
    val stateScale = when (state) {
        FaceState.SURPRISED -> 1.12f
        FaceState.THINKING -> 0.96f
        FaceState.HAPPY -> 0.90f
        FaceState.SPEAKING -> 1.04f
        else -> 1f
    }
    val gazeX = when (state) {
        FaceState.THINKING -> lookPhase * eyeWidth * 0.28f
        FaceState.LISTENING -> sin(lookPhase * Math.PI.toFloat()) * eyeWidth * 0.28f
        FaceState.HAPPY -> 0f
        else -> lookPhase * eyeWidth * 0.12f
    }
    val gazeY = when (state) {
        FaceState.THINKING -> -eyeHeight * 0.12f
        FaceState.SURPRISED -> -eyeHeight * 0.02f
        else -> 0f
    }

    drawEye(Offset(center.x - eyeOffsetX, eyeY), eyeWidth, eyeHeight, blinkScale, stateScale, gazeX, gazeY)
    drawEye(Offset(center.x + eyeOffsetX, eyeY), eyeWidth, eyeHeight, blinkScale, stateScale, gazeX, gazeY)
}

private fun DrawScope.drawEye(
    center: Offset,
    eyeWidth: Float,
    eyeHeight: Float,
    blinkScale: Float,
    stateScale: Float,
    gazeX: Float,
    gazeY: Float,
) {
    val w = eyeWidth * stateScale
    val h = eyeHeight * stateScale * blinkScale
    drawOval(brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFDCECF4)), center.y - h, center.y + h), topLeft = Offset(center.x - w, center.y - h), size = Size(w * 2f, h * 2f))
    val pupil = center + Offset(gazeX, gazeY)
    drawCircle(Color(0xFF0B2C52), radius = h * 0.42f, center = pupil)
    drawCircle(Color(0xFF071A32), radius = h * 0.24f, center = pupil + Offset(0f, h * 0.05f))
    drawCircle(Color.White, radius = h * 0.11f, center = pupil + Offset(-h * 0.12f, -h * 0.15f))
}
