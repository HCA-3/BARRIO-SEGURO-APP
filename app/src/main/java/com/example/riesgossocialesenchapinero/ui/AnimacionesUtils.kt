package com.example.riesgossocialesenchapinero.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Efecto de rebote interactivo con resorte físico (Spring physics) al presionar cualquier recuadro.
 */
@Composable
fun Modifier.bounceClick(
    scaleDown: Float = 0.97f,
    onClick: (() -> Unit)? = null
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )

    val baseModifier = this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

    return if (onClick != null) {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = null, // Scale animation provides clear feedback
            onClick = onClick
        )
    } else {
        baseModifier
    }
}

/**
 * Animación de entrada escalonada suave por opacidad (sin desplazar ni sobreponer recuadros).
 */
@Composable
fun Modifier.staggeredEntrance(
    index: Int = 0,
    baseDelayMs: Long = 25L
): Modifier {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val delayTime = (index.coerceAtMost(12) * baseDelayMs).coerceAtLeast(0L)
        delay(delayTime)
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "staggeredAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.98f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "staggeredScale"
    )

    return this.graphicsLayer {
        this.alpha = alpha
        this.scaleX = scale
        this.scaleY = scale
    }
}

/**
 * Pulso de luz/resplandor perimetral para alertas o tarjetas de alto riesgo.
 */
@Composable
fun rememberPulsingBorder(
    color: Color = Color.Red,
    minAlpha: Float = 0.25f,
    maxAlpha: Float = 0.85f,
    durationMs: Int = 1200
): BorderStroke {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    return BorderStroke(1.5.dp, color.copy(alpha = alpha))
}

/**
 * Pulso de respiración suave (Scale pulsing) para elementos de alerta activos.
 */
@Composable
fun Modifier.breathingPulse(
    minScale: Float = 0.98f,
    maxScale: Float = 1.02f,
    durationMs: Int = 1500
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "breathingPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
