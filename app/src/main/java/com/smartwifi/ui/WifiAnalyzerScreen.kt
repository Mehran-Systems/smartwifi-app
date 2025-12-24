package com.smartwifi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwifi.data.model.AvailableNetworkItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiAnalyzerScreen(
    onBackClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedBand by remember { mutableStateOf(0) } // 0 = 2.4GHz, 1 = 5GHz

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Signal Strength") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Band Tabs
            TabRow(
                selectedTabIndex = selectedBand,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = selectedBand == 0, onClick = { selectedBand = 0 }, text = { Text("2.4G") })
                Tab(selected = selectedBand == 1, onClick = { selectedBand = 1 }, text = { Text("5G") })
            }

            // Graph Area
            // Use Surface color for background. Theme controls this.
            val backgroundColor = MaterialTheme.colorScheme.surface
            val onBackgroundColor = MaterialTheme.colorScheme.onSurface
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
            ) {
                 val networks = uiState.availableNetworks.filter {
                     if (selectedBand == 0) it.frequency < 3000 else it.frequency > 5000
                 }
                 
                 ChannelGraph(
                     networks = networks,
                     is5Ghz = selectedBand == 1,
                     backgroundColor = backgroundColor,
                     contentColor = onBackgroundColor,
                     modifier = Modifier.fillMaxSize().padding(16.dp)
                 )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun ChannelGraph(
    networks: List<AvailableNetworkItem>,
    is5Ghz: Boolean,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val isDarkTheme = backgroundColor.luminance() < 0.5f

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = width * 0.05f // 5% padding on each side
        val graphWidth = width - (2 * padding)
        
        // Configuration
        val minDbm = -100f
        val maxDbm = -20f
        val dbmRange = maxDbm - minDbm
        
        // Frequency Map (MHz)
        val freqStart = if (is5Ghz) 5150f else 2390f // Start slightly earlier for 2.4G padding
        val freqEnd = if (is5Ghz) 5850f else 2500f // End slightly later
        val freqRange = freqEnd - freqStart
        
        fun freqToX(freq: Int): Float {
            return padding + (((freq - freqStart) / freqRange) * graphWidth)
        }

        // Draw Grid Lines (Y-Axis dBm)
        for (dbm in -90..-30 step 10) {
            val y = height - ((dbm - minDbm) / dbmRange * height)
            drawLine(
                color = contentColor.copy(alpha=0.1f),
                start = Offset(padding, y),
                end = Offset(width - padding, y)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "${dbm}dBm",
                topLeft = Offset(5f, y - 20f),
                style = androidx.compose.ui.text.TextStyle(color = contentColor.copy(alpha=0.5f), fontSize = 10.sp)
            )
        }

        // Draw Channels (X-Axis)
        val channels = if (is5Ghz) {
             (36..165 step 4).map { ch -> ch to (5000 + ch * 5) }
        } else {
             (1..14).map { ch -> 
                 val freq = if (ch == 14) 2484 else 2407 + (ch * 5)
                 ch to freq
             }
        }

        channels.forEachIndexed { index, (ch, freq) ->
            if (freq >= freqStart && freq <= freqEnd) {
                // Filter labels to prevent overcrowding
                val shouldShow = if (is5Ghz) {
                    (index % 4 == 0) // Show every 4th channel in the list (Space out considerably)
                } else {
                    true // Show all for 2.4G
                }

                if (shouldShow) {
                    val x = freqToX(freq)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = ch.toString(),
                        topLeft = Offset(x - 10f, height - 30f),
                        style = androidx.compose.ui.text.TextStyle(color = contentColor.copy(alpha=0.7f), fontSize = 12.sp)
                    )
                    // Small tick marks
                     drawLine(
                        color = contentColor.copy(alpha=0.3f),
                        start = Offset(x, height),
                        end = Offset(x, height - 10f)
                    )
                }
            }
        }

        // Draw Networks (Parabolas)
        networks.forEach { network ->
            // Skip networks out of range
            if (network.frequency < freqStart || network.frequency > freqEnd) return@forEach

            val color = generateColorFromString(network.ssid, isDarkTheme)
            val centerX = freqToX(network.frequency)
            
            // Visual width normalization
            val widthPx = (network.channelWidth / freqRange) * graphWidth
            val halfWidthPx = widthPx / 2f
            
            val peakY = height - ((network.level - minDbm) / dbmRange * height)
            val controlY = 2 * peakY - height
            
            val path = Path().apply {
                moveTo(centerX - halfWidthPx, height)
                quadraticBezierTo(
                    centerX, controlY,
                    centerX + halfWidthPx, height
                )
            }
            
            // Gradient Fill
            val fillBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0.05f)),
                startY = peakY,
                endY = height
            )

            // Fill
            drawPath(
                path = path,
                brush = fillBrush
            )
            // Stroke
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 5f)
            )
            
            // Label
            val labelY = (peakY - 30f).coerceAtLeast(0f)
            drawText(
                textMeasurer = textMeasurer,
                text = "${network.ssid}",
                topLeft = Offset(centerX - 20f, labelY - 15f),
                style = androidx.compose.ui.text.TextStyle(color = color, fontSize = 12.sp)
            )
        }
    }
}

fun generateColorFromString(s: String, isDarkTheme: Boolean): Color {
    val hash = s.hashCode()
    val r = (hash and 0xFF0000) shr 16
    val g = (hash and 0x00FF00) shr 8
    val b = (hash and 0x0000FF)
    
    // Adjust luminance based on background
    return if (isDarkTheme) {
        // Boost brightness for dark background
        Color(
             red = (r + 100).coerceAtMost(255) / 255f,
             green = (g + 100).coerceAtMost(255) / 255f,
             blue = (b + 100).coerceAtMost(255) / 255f,
             alpha = 1f
        )
    } else {
        // Darken for light background
         Color(
             red = (r * 0.7f).toInt() / 255f,
             green = (g * 0.7f).toInt() / 255f,
             blue = (b * 0.7f).toInt() / 255f,
             alpha = 1f
        )
    }
}
