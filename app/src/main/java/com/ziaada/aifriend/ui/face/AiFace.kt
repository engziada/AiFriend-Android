package com.ziaada.aifriend.ui.face

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ziaada.aifriend.domain.FaceState
import kotlin.math.sin

@Composable
fun AiFace(state: FaceState, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "face")
    val blink by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(180, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
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
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF021733), Color(0xFF06366A), Color(0xFF03152C)),
            ),
        )

        val center = Offset(size.width / 2f, size.height / 2f - size.height * 0.02f)
        val base = minOf(size.width, size.height) * 0.34f * breathe
        val radius = when (state) {
            FaceState.SURPRISED -> base * 1.06f
            FaceState.THINKING -> base * 0.99f
            else -> base
        }

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
    }
}

private fun DrawScope.drawFace(center: Offset, radius: Float, state: FaceState, pulse: Float) {
    val highlight = when (state) {
        FaceState.HAPPY -> 0xFF78E2FF
        FaceState.SURPRISED -> 0xFF66D7FF
        else -> 0xFF55D5FF
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(highlight), Color(0xFF007FC0), Color(0xFF034A79)),
            center = Offset(center.x - radius * 0.26f, center.y - radius * 0.32f),
            radius = radius * 1.28f,
        ),
        center = center,
        radius = radius,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.08f),
        center = Offset(center.x - radius * 0.30f, center.y - radius * 0.38f),
        radius = radius * 0.16f * pulse,
    )
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
    val eyeY = center.y - radius * 0.02f
    val eyeWidth = radius * 0.34f
    val eyeHeight = radius * 0.50f
    val stateScale = when (state) {
        FaceState.SURPRISED -> 1.18f
        FaceState.THINKING -> 0.93f
        FaceState.SPEAKING -> 1.04f
        else -> 1f
    }
    val gazeX = when (state) {
        FaceState.THINKING -> lookPhase * eyeWidth * 0.75f
        FaceState.LISTENING -> sin(lookPhase * Math.PI.toFloat()) * eyeWidth * 0.85f
        FaceState.HAPPY -> eyeWidth * 0.15f
        else -> lookPhase * eyeWidth * 0.34f
    }
    val gazeY = when (state) {
        FaceState.THINKING -> -eyeHeight * 0.18f
        FaceState.SURPRISED -> -eyeHeight * 0.05f
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
    drawOval(
        color = Color.White,
        topLeft = Offset(center.x - w, center.y - h),
        size = Size(w * 2f, h * 2f),
    )
    val pupil = center + Offset(gazeX, gazeY)
    drawCircle(Color(0xFF0B2C52), radius = h * 0.48f, center = pupil)
    drawCircle(Color(0xFF071A32), radius = h * 0.27f, center = pupil + Offset(0f, h * 0.05f))
    drawCircle(Color.White, radius = h * 0.12f, center = pupil + Offset(-h * 0.13f, -h * 0.16f))
}
