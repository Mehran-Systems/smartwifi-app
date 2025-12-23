package com.smartwifi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
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
                        Text("SpeedTest", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Powered by Fast.com", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                     IconButton(onClick = onBackClick) {
                         Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                     }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp) // Spaced evenly
        ) {
            
            // 1. Grid Boxes (Download / Upload)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Download Box
                Card(
                     modifier = Modifier.weight(1f),
                     colors = CardDefaults.cardColors(
                         containerColor = if (testState is FastSpeedTestManager.TestState.Running && (testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.DOWNLOAD) 
                             MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                     )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Download", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Logic: 
                        // If Finished -> Show Final
                        // If Uploading -> Show Final (from metricData)
                        // If Downloading -> Show Live
                        val dlVal = when {
                            testState is FastSpeedTestManager.TestState.Finished -> (testState as FastSpeedTestManager.TestState.Finished).downloadSpeed
                            testState is FastSpeedTestManager.TestState.Running && (testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.UPLOAD -> metricData.downloadSpeed ?: 0.0
                            testState is FastSpeedTestManager.TestState.Running && (testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.DOWNLOAD -> (testState as FastSpeedTestManager.TestState.Running).speedMbps
                            else -> null
                        }
                        
                        val dlText = if (dlVal != null) "%.0f".format(dlVal) else "-"
                        Text(dlText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Mbps", style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                // Upload Box
                Card(
                     modifier = Modifier.weight(1f),
                     colors = CardDefaults.cardColors(
                         containerColor = if (testState is FastSpeedTestManager.TestState.Running && (testState as FastSpeedTestManager.TestState.Running).phase == FastSpeedTestManager.TestPhase.UPLOAD) 
                             MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                     )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
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
                    }
                }
            }

            // 2. Large Live Number & Progress
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 32.dp)
            ) {
                 val currentSpeed = when(testState) {
                    is FastSpeedTestManager.TestState.Running -> (testState as FastSpeedTestManager.TestState.Running).speedMbps
                    is FastSpeedTestManager.TestState.Finished -> (testState as FastSpeedTestManager.TestState.Finished).downloadSpeed // Default to DL on finish
                    else -> 0.0
                 }
                 
                 Text(
                    text = "%.0f".format(currentSpeed),
                    fontSize = 112.sp, // Very Large
                    lineHeight = 112.sp,
                    fontWeight = FontWeight.Thin, // Slim Font
                    color = MaterialTheme.colorScheme.primary
                 )
                 Text(
                     text = "Mbps", 
                     style = MaterialTheme.typography.headlineSmall, 
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     fontWeight = FontWeight.Light
                 )
                 
                 Spacer(modifier = Modifier.height(24.dp))
                 
                 // Progress Bar
                 if (testState is FastSpeedTestManager.TestState.Running) {
                     val state = testState as FastSpeedTestManager.TestState.Running
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         LinearProgressIndicator(
                             progress = state.progress,
                             modifier = Modifier.width(200.dp).height(4.dp),
                             trackColor = MaterialTheme.colorScheme.surfaceVariant,
                             color = MaterialTheme.colorScheme.primary
                         )
                         Spacer(modifier = Modifier.height(8.dp))
                         Text(
                             text = if (state.phase == FastSpeedTestManager.TestPhase.DOWNLOAD) "Downloading..." else "Uploading...",
                             style = MaterialTheme.typography.labelSmall,
                             color = MaterialTheme.colorScheme.primary
                         )
                     }
                 }
            }
            
            // 3. Ping Row (Icons + Values)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 PingItem("Idle", metricData.idlePing?.toString() ?: "-", Icons.Rounded.HourglassEmpty)
                 PingItem("Down", "-", Icons.Rounded.ArrowDownward) // Placeholder
                 PingItem("Up", "-", Icons.Rounded.ArrowUpward) // Placeholder
            }
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // 4. Client / Server Info
            Column(
                modifier = Modifier.fillMaxWidth(), 
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoRow("Client", metricData.clientIp ?: "Locating...")
                InfoRow("Server", metricData.serverHost ?: "Selecting...")
            }
            
            Spacer(modifier = Modifier.weight(1f))

            // 5. Action Button
            Button(
                onClick = { viewModel.startTest() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = testState !is FastSpeedTestManager.TestState.Running
            ) {
                Icon(if (testState is FastSpeedTestManager.TestState.Running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (testState is FastSpeedTestManager.TestState.Running) "Testing..." else "Start Speed Test")
            }
        }
    }
}

@Composable
fun PingItem(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column {
             Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
             Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
