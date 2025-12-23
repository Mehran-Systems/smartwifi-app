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
    isScanning: Boolean = true,
    pullBearing: Float? = null,
    pullStrength: Float = 0f 
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidWobble")
    
    // Increased points for smoother circle
    val pointCount = 12 
    val angleStep = 360f / pointCount
    
    // Lazy Animation: Slower duration (3000-4000ms)
    val pointOffsets = (0 until pointCount).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0.95f, // Less aggressive wobble range (was 0.9)
            targetValue = 1.05f,  // (was 1.1)
            animationSpec = infiniteRepeatable(
                animation = tween(3000 + (i * 200), easing = LinearEasing), // Linear or Sine for continuous feel
                repeatMode = RepeatMode.Reverse
            ),
            label = "Point$i"
        )
    }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        // Use more available space but keep it circular
        val baseRadius = size.minDimension / 2 * 0.9f 

        val path = Path()
        val points = mutableListOf<Offset>()
        
        for (i in 0 until pointCount) {
            val angleDeg = i * angleStep
            val angleRad = Math.toRadians(angleDeg.toDouble())
            
            var r = baseRadius
            
            // Calculate Directional Factors
            var proximityFactor = 0f
            
            if (pullBearing != null) {
                var diff = Math.abs(angleDeg - pullBearing)
                if (diff > 180) diff = 360 - diff
                
                // Focus angle: 90 degrees around the target (45 deg each side)
                if (diff < 90) {
                    // 1.0 at center, 0.0 at 90 degrees away
                    proximityFactor = 1f - (diff / 90f)
                }
            } else {
                // If no direction (e.g. stationary), gentle global breathe (0.3 intensity)
                proximityFactor = 0.3f
            }

            // apply animation ONLY based on proximity
            // The 'value' oscillates roughly 0.95 to 1.05. We map that to a delta.
            val wobbleDelta = pointOffsets[i].value - 1f // -0.05 to +0.05
            
            // Apply wobble scaled by proximity. 
            // The side facing signal wobbles. The back side is nearly static.
            r += (baseRadius * wobbleDelta * proximityFactor)

            // Add the "Pull" extension on top of the wobble
            if (pullStrength > 0 && proximityFactor > 0) {
                 r += (baseRadius * 0.15f * pullStrength * proximityFactor)
            }

            val x = centerX + (r * cos(angleRad - PI/2)).toFloat()
            val y = centerY + (r * sin(angleRad - PI/2)).toFloat()
            points.add(Offset(x, y))
        }
        // Draw smooth path through points using Midpoint technique for seamless loop
        if (points.isNotEmpty()) {
            val p0 = points[0]
            val p1 = points[1]
            val startMidX = (p0.x + p1.x) / 2
            val startMidY = (p0.y + p1.y) / 2
            
            path.moveTo(startMidX, startMidY)
            
            for (i in 1 until points.size) {
                val p = points[i]
                val nextP = points[(i + 1) % points.size]
                val destX = (p.x + nextP.x) / 2
                val destY = (p.y + nextP.y) / 2
                path.quadraticBezierTo(p.x, p.y, destX, destY)
            }
            
            // Close the loop using point[0] as control back to startMid
            path.quadraticBezierTo(points[0].x, points[0].y, startMidX, startMidY)
        }
        
        path.close()

        // Draw Border Only (Clean Circle Look)
        drawPath(
            path = path,
            color = blobColor,
            style = Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        // Optional: Very subtle fill
        drawPath(
            path = path,
            color = blobColor.copy(alpha = 0.05f),
            style = Fill
        )
    }
}
