package com.smartwifi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwifi.logic.FastSpeedTestManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    onBackClick: () -> Unit,
    onHistoryClick: () -> Unit,
    viewModel: SpeedTestViewModel = hiltViewModel()
) {
    val testState by viewModel.testManager.testState.collectAsState()
    val metricData by viewModel.testManager.metricData.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Speed Test", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Powered by Fast.com", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                     IconButton(onClick = onBackClick) {
                         Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                     }
                },
                actions = {
                    IconButton(onClick = onHistoryClick) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                }
            )
        },
        bottomBar = {
            Box(
                 modifier = Modifier
                     .fillMaxWidth()
                     .padding(24.dp)
                     .height(56.dp)
                     .clip(MaterialTheme.shapes.extraLarge)
                     .background(
                          if (testState is FastSpeedTestManager.TestState.Running || testState is FastSpeedTestManager.TestState.Preparing) 
                              MaterialTheme.colorScheme.surfaceVariant 
                          else MaterialTheme.colorScheme.primary
                     )
                     .clickable(enabled = testState !is FastSpeedTestManager.TestState.Running && testState !is FastSpeedTestManager.TestState.Preparing) {
                         viewModel.startTest()
                     },
                 contentAlignment = Alignment.Center
            ) {
                if (testState is FastSpeedTestManager.TestState.Running) {
                     val state = testState as FastSpeedTestManager.TestState.Running
                     Box(
                         modifier = Modifier
                             .fillMaxHeight()
                             .fillMaxWidth(state.progress)
                             .align(Alignment.CenterStart)
                             .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) // Revert to primary tint
                     )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when(testState) {
                         is FastSpeedTestManager.TestState.Running -> {
                             if ((testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.DOWNLOAD) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward
                         }
                         is FastSpeedTestManager.TestState.Preparing -> null // No icon for connecting
                         else -> Icons.Rounded.PlayArrow
                    }
                    val text = when(testState) {
                        is FastSpeedTestManager.TestState.Preparing -> "Connecting..."
                        is FastSpeedTestManager.TestState.Running -> {
                            if ((testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.DOWNLOAD) "Downloading..." else "Uploading..."
                        }
                        is FastSpeedTestManager.TestState.Finished -> "Test Again"
                        else -> "Start Speed Test"
                    }
                    
                    val contentColor = if (testState is FastSpeedTestManager.TestState.Running || testState is FastSpeedTestManager.TestState.Preparing) 
                        MaterialTheme.colorScheme.onSurfaceVariant 
                    else MaterialTheme.colorScheme.onPrimary
                    
                    if (icon != null) {
                        Icon(
                            imageVector = icon, 
                            contentDescription = null, 
                            tint = contentColor
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Grid Boxes (Equal Height)
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Download Box
                Card(
                     modifier = Modifier.weight(1f).fillMaxHeight(),
                     colors = CardDefaults.cardColors(
                         containerColor = if (testState is FastSpeedTestManager.TestState.Running && (testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.DOWNLOAD) 
                             MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                     )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth().fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Download", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val dlVal = when {
                            testState is FastSpeedTestManager.TestState.Finished -> (testState as FastSpeedTestManager.TestState.Finished).downloadSpeed
                            testState is FastSpeedTestManager.TestState.Running && (testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.UPLOAD -> metricData.downloadSpeed ?: 0.0
                            testState is FastSpeedTestManager.TestState.Running && (testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.DOWNLOAD -> (testState as FastSpeedTestManager.TestState.Running).speedMbps
                            else -> null
                        }
                        
                        val dlText = if (dlVal != null) "%.0f".format(dlVal) else "-"
                        Text(dlText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Mbps", style = MaterialTheme.typography.labelSmall)
                        
                        if (metricData.downloadJitter != null || metricData.jitter != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.GraphicEq, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${metricData.downloadJitter ?: metricData.jitter} ms", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // Upload Box
                Card(
                     modifier = Modifier.weight(1f).fillMaxHeight(),
                     colors = CardDefaults.cardColors(
                         containerColor = if (testState is FastSpeedTestManager.TestState.Running && (testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.UPLOAD) 
                             MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                     )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth().fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Upload", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val ulVal = when {
                            testState is FastSpeedTestManager.TestState.Finished -> (testState as FastSpeedTestManager.TestState.Finished).uploadSpeed
                            testState is FastSpeedTestManager.TestState.Running && (testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.UPLOAD -> (testState as FastSpeedTestManager.TestState.Running).speedMbps
                            else -> null
                        }
                        
                        val ulText = if (ulVal != null) "%.0f".format(ulVal) else "-"
                        Text(ulText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Mbps", style = MaterialTheme.typography.labelSmall)

                        if (metricData.uploadJitter != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.GraphicEq, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${metricData.uploadJitter} ms", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                 val currentSpeed = when(testState) {
                    is FastSpeedTestManager.TestState.Running -> (testState as FastSpeedTestManager.TestState.Running).speedMbps
                    is FastSpeedTestManager.TestState.Finished -> (testState as FastSpeedTestManager.TestState.Finished).downloadSpeed // Default to DL on finish
                    else -> 0.0
                 }
                 
                 val isUpload = testState is FastSpeedTestManager.TestState.Running && 
                               (testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.UPLOAD
                               
                 val gaugeColor = if (isUpload) Color(0xFFFF9800) else Color(0xFF4CAF50)
                 
                 // Compact Gauge Size
                 Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) { // Slightly larger box to fit text below
                     com.smartwifi.ui.components.SpeedometerGauge(
                         currentValue = currentSpeed,
                         maxValue = 100.0,
                         gaugeColor = gaugeColor,
                         modifier = Modifier.fillMaxSize().padding(bottom = 20.dp) // Lift gauge slightly
                     )
                     
                     Column(
                         horizontalAlignment = Alignment.CenterHorizontally,
                         modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                     ) {
                         Text(
                            text = "%.0f".format(currentSpeed),
                            fontSize = 40.sp, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                         )
                         Text(
                             text = "Mbps", 
                             style = MaterialTheme.typography.titleMedium, 
                             color = MaterialTheme.colorScheme.onSurfaceVariant
                         )
                     }
                 }
            }
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // 4. Detailed Metrics Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Row 1: Client & Server
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Client Info
                    MetricCell(
                        modifier = Modifier.weight(1f), 
                        label = "Client", 
                        value = buildString {
                            append(metricData.userLocation ?: "-")
                            if (metricData.clientIp != null) append("\n${metricData.clientIp}")
                            if (metricData.clientIsp != null) append("\n${metricData.clientIsp}")
                        }, 
                        icon = Icons.Rounded.Person
                    )
                    
                    // Server Info
                    MetricCell(
                        modifier = Modifier.weight(1f), 
                        label = "Server", 
                        value = buildString {
                            if (metricData.serverLocation != null) append("${metricData.serverLocation}\n")
                            append(metricData.serverName ?: "-")
                        }, 
                        icon = Icons.Rounded.Dns
                    )
                }

                // Row 2: Internal IP & Packet Loss
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     MetricCell(
                        modifier = Modifier.weight(1f),
                        label = "Internal IP",
                        value = metricData.internalIp ?: "-",
                        icon = Icons.Rounded.Router
                    )
                     MetricCell(Modifier.weight(1f), "Packet Loss", if (metricData.packetLoss != null) "%.1f%%".format(metricData.packetLoss) else "-", Icons.Rounded.Warning)
                }
                
                // Row 3: Pings (Idle Only)
                 Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     MetricCell(Modifier.weight(1f), "Idle Ping", "${metricData.idlePing ?: "-"} ms", Icons.Rounded.HourglassEmpty)
                }
            }
            
            // Spacer to ensure content doesn't get hidden behind bottom bar if scroll is at very bottom
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun MetricCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), MaterialTheme.shapes.small)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
             Text(
                 text = value,
                 style = MaterialTheme.typography.labelLarge, 
                 fontWeight = FontWeight.Bold,
                 maxLines = 3,
                 overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
             )
             Text(
                 text = label, 
                 style = MaterialTheme.typography.labelSmall, // Reduced size
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 maxLines = 1
             )
        }
    }
}
