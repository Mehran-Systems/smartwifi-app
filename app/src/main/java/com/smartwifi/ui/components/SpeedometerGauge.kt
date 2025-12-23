package com.smartwifi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerGauge(
    currentValue: Double,
    maxValue: Double = 100.0,
    isConnected: Boolean = true,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = currentValue.toFloat(),
        animationSpec = tween(durationMillis = 500)
    )
    
    val progress = (animatedValue / maxValue.toFloat()).coerceIn(0f, 1f)
    
    // Aesthetic Colors
    val activeColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336) // Green / Red
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().aspectRatio(1f)) {
            val size = this.size.minDimension
            val strokeWidth = 20.dp.toPx()
            val radius = (size - strokeWidth) / 2
            val center = Offset(this.size.width / 2, this.size.height / 2)
            
            // Arc Params (Start from 135 degrees, sweep 270 degrees)
            val startAngle = 135f
            val sweepAngle = 270f
            
            // 1. Draw Track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )
            
            // 2. Draw Progress
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to activeColor.copy(alpha=0.5f),
                    0.5f to activeColor,
                    1.0f to activeColor,
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = sweepAngle * progress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )
            
            // 3. Draw Needle / Indicator (Simple Circle at tip) or just stick to the bar
            // For this design, let's keep it clean with just the bar and maybe a glow
        }
    }
}
