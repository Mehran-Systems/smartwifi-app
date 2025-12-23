package com.smartwifi.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LiquidRadar(
    modifier: Modifier = Modifier,
    blobColor: Color = Color(0xFF4CAF50),
    pulseSpeed: Float = 1.0f // 1.0f = Normal, 2.0f = Fast, 0.0f = Static
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SonarRipple")
    
    // Create multiple ripples with staggered delays
    val rippleCount = 3
    val ripples = (0 until rippleCount).map { index ->
        val initialStart = index * (1000 / rippleCount) // Stagger start
        
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, delayMillis = initialStart, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Ripple$index"
        )
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxRadius = size.minDimension / 2
        
        ripples.forEach { progress ->
            val r = maxRadius * progress.value
            val alpha = (1f - progress.value).coerceIn(0f, 1f) * 0.5f // Fade out
            
            drawCircle(
                color = blobColor.copy(alpha = alpha),
                radius = r,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx())
            )
            
            // Optional: Filled faint trail
            drawCircle(
                color = blobColor.copy(alpha = alpha * 0.1f),
                radius = r,
                center = Offset(centerX, centerY),
                style = Fill
            )
        }
    }
}
