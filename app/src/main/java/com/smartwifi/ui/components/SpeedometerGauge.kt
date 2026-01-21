package com.smartwifi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpeedometerGauge(
    currentValue: Double,
    maxValue: Double = 100.0,
    gaugeColor: Color = Color(0xFF4CAF50),
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = currentValue.coerceAtMost(maxValue).toFloat(),
        animationSpec = tween(durationMillis = 500)
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().aspectRatio(1f).padding(16.dp)) {
            val size = this.size.minDimension
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val radius = size / 2
            
            val startAngle = 135f
            val sweepAngle = 270f
            
            // 0. Draw Progress Bar / Track
            // Background Track
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius + 10f, center.y - radius + 10f), // Slight inset
                size = Size((radius - 10f) * 2, (radius - 10f) * 2)
            )
            
            // Foreground Progress Fill
            val currentProgress = (animatedValue / maxValue.toFloat()).coerceIn(0f, 1f)
            
            // Sweep Gradient for progress fill
            val progressBrush = Brush.sweepGradient(
                 0.0f to gaugeColor.copy(alpha = 0.5f),
                 0.5f to gaugeColor,
                 1.0f to gaugeColor,
                 center = center
            )
            // Rotate the gradient to align with start angle (sweep gradients start at 0 deg, we start at 135)
            // But actually standard drawArc handles the shape, the gradient colors map to the circle.
            // 0.0 is 3 o'clock. 135 deg is ~7 o'clock. 
            // We might need to adjust gradient offsets or just use solid color if it looks weird.
            // Let's stick to solid color OR a rotated brush if needed. For now solid color is safest visual.
            // BUT user said "gets filled... as shown in picture". Usually implies visual fill.
            // I'll stick to simple Solid Color gaugeColor for now to be safe, or retry the brush later if user complains.
            // Actually, the previous step's Brush was fine but it requires proper color stop mapping if start angle is 135.
            // Let's use simple solid color to GUARANTEE it looks like a "filled bar".
            
            drawArc(
                color = gaugeColor, // Solid, simple, effective
                startAngle = startAngle,
                sweepAngle = sweepAngle * currentProgress,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius + 10f, center.y - radius + 10f),
                size = Size((radius - 10f) * 2, (radius - 10f) * 2)
            )

            // 1. Draw Ticks & Numbers
            val majorTicks = 11 // 0, 10, 20... 100
            val minorTicksPerMajor = 4
            
            for (i in 0 until majorTicks) {
                val progress = i.toFloat() / (majorTicks - 1)
                val angle = startAngle + (sweepAngle * progress)
                val angleRad = Math.toRadians(angle.toDouble())
                
                // Draw Major Tick
                val startOffset = Offset(
                    center.x + (radius - 30f) * cos(angleRad).toFloat(),
                    center.y + (radius - 30f) * sin(angleRad).toFloat()
                )
                val endOffset = Offset(
                    center.x + (radius - 5f) * cos(angleRad).toFloat(),
                    center.y + (radius - 5f) * sin(angleRad).toFloat()
                )
                
                drawLine(
                    color = Color.Gray,
                    start = startOffset,
                    end = endOffset,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                
                // Draw Number
                val numberValue = (maxValue * progress).toInt()
                val textRadius = radius - 55f
                val textOffset = Offset(
                    center.x + textRadius * cos(angleRad).toFloat(),
                    center.y + textRadius * sin(angleRad).toFloat()
                )
                
                drawContext.canvas.nativeCanvas.apply {
                    drawText(
                        numberValue.toString(),
                        textOffset.x,
                        textOffset.y + 15f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 32f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    )
                }
                
                // Draw Minor Ticks
                if (i < majorTicks - 1) {
                    val segmentAngle = sweepAngle / (majorTicks - 1)
                    val subSegmentAngle = segmentAngle / (minorTicksPerMajor + 1)
                    
                    for (j in 1..minorTicksPerMajor) {
                        val minorAngle = angle + (subSegmentAngle * j)
                        val minorRad = Math.toRadians(minorAngle.toDouble())
                        
                        val minorStart = Offset(
                            center.x + (radius - 15f) * cos(minorRad).toFloat(),
                            center.y + (radius - 15f) * sin(minorRad).toFloat()
                        )
                        val minorEnd = Offset(
                            center.x + (radius - 5f) * cos(minorRad).toFloat(),
                            center.y + (radius - 5f) * sin(minorRad).toFloat()
                        )
                        
                        drawLine(
                            color = Color.LightGray,
                            start = minorStart,
                            end = minorEnd,
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            
            // 2. Draw Gradient Needle
            // currentProgress defined above at line 60
            val needleAngle = startAngle + (sweepAngle * currentProgress)
            
            rotate(degrees = needleAngle + 90f, pivot = center) {
                // Gradient from Center (Transparent) to Tip (Solid Color)
                val needleLength = radius - 30f // Stop just before ticks
                val brush = Brush.verticalGradient(
                    0.0f to gaugeColor.copy(alpha = 0f), // Center (Transparent)
                    1.0f to gaugeColor,                   // Tip (Solid)
                    startY = center.y,
                    endY = center.y - needleLength
                )
                
                drawLine(
                    brush = brush,
                    start = center,
                    end = Offset(center.x, center.y - needleLength),
                    strokeWidth = 6.dp.toPx(), // Slightly thicker
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
