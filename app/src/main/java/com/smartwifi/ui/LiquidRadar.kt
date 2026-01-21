package com.smartwifi.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun LiquidRadar(
    modifier: Modifier = Modifier,
    blobColor: Color = Color(0xFF4CAF50),
    pulseSpeed: Float = 1.0f 
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    
    // Pulse animation for the "live data" circle
    val rippleCount = 2
    val ripples = (0 until rippleCount).map { index ->
        val delay = index * (1500 / rippleCount)
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = (1500 / pulseSpeed).toInt(),
                    delayMillis = delay,
                    easing = LinearOutSlowInEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "Pulse$index"
        )
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val startRadius = 110.dp.toPx()
        val maxRadius = size.minDimension / 2
        
        // 1. "Stable Link" - Highly visible continuous fading color
        // Further increased alpha values for maximum visibility
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to blobColor.copy(alpha = 0.7f),  // Higher initial density
                0.6f to blobColor.copy(alpha = 0.4f),  // Stronger mid-tone
                0.9f to blobColor.copy(alpha = 0.1f),  // Softer transition to edge
                1.0f to Color.Transparent,           
                center = Offset(centerX, centerY),
                radius = maxRadius
            ),
            radius = maxRadius,
            center = Offset(centerX, centerY)
        )

        // 2. "Live Data" - Stronger pulsing circles
        ripples.forEach { progress ->
            val p = progress.value
            val currentRadius = startRadius + (maxRadius - startRadius) * p
            
            // Fade out as it reaches the end
            val alpha = (1f - p).coerceIn(0f, 1f)
            
            drawCircle(
                color = blobColor.copy(alpha = alpha * 0.9f), 
                radius = currentRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 4.dp.toPx()) // Thicker stroke for more impact
            )
        }
    }
}
