package com.smartwifi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.draw.clip
import com.smartwifi.data.model.ProbationItem
import com.smartwifi.data.model.AvailableNetworkItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkListScreen(
    onBackClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Available Networks", "Probation (Zombies)")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Top Bar
        SmallTopAppBar(
            title = { Text("Network Manager") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Tabs
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        // Content
        when (selectedTabIndex) {
            0 -> AvailableNetworksList(uiState.availableNetworks)
            1 -> ProbationList(uiState.probationList)
        }
    }
}

@Composable
fun AvailableNetworksList(networks: List<com.smartwifi.data.model.AvailableNetworkItem>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(networks) { network ->
            NetworkAnalyzerCard(network)
        }
    }
}

@Composable
fun NetworkAnalyzerCard(network: com.smartwifi.data.model.AvailableNetworkItem) {
    val barColor = when {
        network.level > -60 -> Color(0xFF66BB6A) // Green
        network.level > -80 -> Color(0xFFFFCA28) // Yellow
        else -> Color(0xFFEF5350) // Red
    }
    
    // Channel Calc
    val channel = if (network.frequency > 4900) (network.frequency - 5000) / 5 else (network.frequency - 2407) / 5
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: SSID + BSSID
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (network.isConnected) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        contentDescription = "Connected", 
                        tint = Color(0xFF66BB6A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (network.isConnected) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "(${network.bssid})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Row 2: Stats + Signal Bar
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CH $channel", fontWeight = FontWeight.Bold, color = Color(0xFF66BB6A))
                        Spacer(modifier = Modifier.width(8.dp))
                       // Security (Stripped)
                        val sec = network.capabilities.split("][").firstOrNull()?.removePrefix("[")?.removeSuffix("]") ?: "OPEN"
                        Text(sec, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${network.frequency} MHz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Width: ${network.channelWidth} MHz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                // Signal Bar Column
                Column(modifier = Modifier.width(140.dp), horizontalAlignment = Alignment.End) {
                     Box(contentAlignment = Alignment.Center) {
                         LinearProgressIndicator(
                             progress = ((network.level + 100) / 60f).coerceIn(0f, 1f),
                             modifier = Modifier
                                .height(20.dp)
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small),
                             color = barColor,
                             trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f)
                         )
                         Text(
                             "${network.level} dBm", 
                             style = MaterialTheme.typography.labelSmall,
                             fontWeight = FontWeight.Bold,
                             color = Color.Black // Always Black on Bar for contrast?
                         )
                     }
                }
            }
        }
    }
}


@Composable
fun ProbationList(probationList: List<ProbationItem>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (probationList.isEmpty()) {
            item {
                Text(
                    text = "No Zombie Hotspots detected.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(probationList) { item ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = item.bssid, // Using BSSID as name for zombie
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Paused for: ${item.secondsRemaining}s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = { /* Force Retry */ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Retry Now")
                        }
                    }
                }
            }
        }
    }
}
