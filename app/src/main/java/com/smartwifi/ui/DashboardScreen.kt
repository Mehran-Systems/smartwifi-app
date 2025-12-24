package com.smartwifi.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.Stroke
import com.smartwifi.data.model.ConnectionSource
import com.smartwifi.ui.LiquidRadar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit,
    onSpeedTestClick: () -> Unit,
    onMenuClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Animation for Radar
    val infiniteTransition = rememberInfiniteTransition(label = "Radar")
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarScale"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp)) // Reduced 32->16
                
                // --- Radar Visual (Principal Focus) ---
                // Sonar Radar Implementation

                val relativePull = null // Not used for Sonar style currently

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(340.dp) // Large area for ripples
                ) {
                    // 1. Background Ripples (Sonar)
                    val radarColor = getRadarColor(uiState.internetStatus)
                    LiquidRadar(
                        modifier = Modifier.fillMaxSize(),
                        blobColor = radarColor,
                        pulseSpeed = if (uiState.internetStatus == "Connected") 1.0f else 0.5f
                    )
                    
                    // 2. The Orb (Center)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), CircleShape)
                    ) {
                        // 3. Signal Strength Progress Bar (Around Orb)
                        // Map RSSI (-100 to -50) to 0.0 - 1.0
                        val rssi = uiState.signalStrength
                        val signalProgress = ((rssi + 100).toFloat() / 50f).coerceIn(0f, 1f)
                        
                        // Track (Background Ring)
                        CircularProgressIndicator(
                            progress = 1f,
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 6.dp
                        )
                        
                        // Value (Foreground Ring)
                        CircularProgressIndicator(
                            progress = signalProgress,
                            modifier = Modifier.fillMaxSize(),
                            color = radarColor,
                            strokeWidth = 6.dp
                        )
                    
                        // 4. Orb Content
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                             // Dynamic Icon Logic
                            val connectionSource = uiState.connectionSource
                            val signalIcon = when (connectionSource) {
                                ConnectionSource.MOBILE_DATA -> getMobileSignalIcon(uiState.signalStrength)
                                else -> getWifiSignalIcon(uiState.signalStrength)
                            }
                            
                            Icon(
                                imageVector = signalIcon,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = radarColor
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = if (connectionSource == ConnectionSource.MOBILE_DATA) uiState.currentSsid else uiState.currentSsid.replace("\"", ""),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "${uiState.frequencyBand} • ${uiState.signalStrength} dBm", 
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp)) // Reduced 32->16

                // --- Triangle Status Bar (Restored) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusIcon(
                        icon = Icons.Rounded.Router,
                        label = "Router",
                        isActive = uiState.connectionSource == ConnectionSource.WIFI_ROUTER
                    )
                    StatusIcon(
                        icon = Icons.Rounded.WifiTethering,
                        label = "Hotspot",
                        isActive = uiState.connectionSource == ConnectionSource.WIFI_HOTSPOT
                    )
                    StatusIcon(
                        icon = Icons.Rounded.SignalCellularAlt,
                        label = "Mobile",
                        isActive = uiState.connectionSource == ConnectionSource.MOBILE_DATA
                    )
                }

                Spacer(modifier = Modifier.height(16.dp)) // Reduced 24->16
        
                // --- Network Speed (Link Speed / Usage) ---
                Card(
                   modifier = Modifier.fillMaxWidth(),
                   colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(), // Padding 16->12
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Text("Link Speed", style = MaterialTheme.typography.labelMedium)
                             Text(
                                 text = "${uiState.linkSpeed} Mbps", 
                                 style = MaterialTheme.typography.titleMedium, // Reduced TitleLarge->TitleMedium
                                 fontWeight = FontWeight.Bold,
                                 color = MaterialTheme.colorScheme.primary
                             )
                         }
                         
                         Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.2f))) // Height 40->32
        
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Text("Current Usage", style = MaterialTheme.typography.labelMedium)
                             Text(
                                 text = uiState.currentUsage, 
                                 style = MaterialTheme.typography.titleMedium, // TitleLarge->TitleMedium
                                 fontWeight = FontWeight.Bold,
                                 color = MaterialTheme.colorScheme.secondary
                             )
                         }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp)) // Reduced 24->16

                // --- Quick Action Cards (Restored) ---
                // --- Quick Action Grid (2x2) ---
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Row 1: Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Gamepad,
                            label = "Gaming Mode",
                            description = "Pause scanning",
                            isActive = uiState.isGamingMode,
                            onClick = { viewModel.toggleGamingMode() }
                        )
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.SignalCellular4Bar,
                            label = "Data Fallback",
                            description = "Mobile backup",
                            isActive = uiState.isDataFallback,
                            onClick = { viewModel.toggleDataFallback() }
                        )
                    }

                    // Row 2: Navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Speed,
                            label = "Speed Test",
                            description = null,
                            isActive = false, 
                            showStatus = false,
                            onClick = onSpeedTestClick
                        )
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Settings,
                            label = "Settings",
                            description = null,
                            isActive = false, 
                            showStatus = false,
                            onClick = onSettingsClick
                        )
                    }
                }
    }
    // --- Switch Confirmation Dialog Removed (Auto-Switch enabled) ---
    // if (uiState.pendingSwitchNetwork != null) { ... }
        }
}

@Composable
fun StatusIcon(icon: ImageVector, label: String, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionCard(
    modifier: Modifier = Modifier, 
    icon: ImageVector, 
    label: String, 
    description: String? = null,
    isActive: Boolean = false,
    showStatus: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label, 
                style = MaterialTheme.typography.labelMedium, 
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                 Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            if (showStatus) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isActive) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun StatusCard(
    title: String, 
    value: String, 
    icon: ImageVector, 
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}



fun getRadarColor(status: String): Color {
    return when (status) {
        "Connected" -> Color(0xFF4CAF50) // Green
        "No Internet" -> Color(0xFFF44336) // Red
        else -> Color(0xFFFFC107) // Yellow
    }
}

fun getWifiSignalIcon(rssi: Int): ImageVector {
    val level = android.net.wifi.WifiManager.calculateSignalLevel(rssi, 5)
    return when (level) {
        4 -> Icons.Default.SignalWifi4Bar
        3 -> Icons.Default.SignalWifi4Bar // Fallback: 3Bar missing
        2 -> Icons.Default.SignalWifi0Bar // Fallback: 2Bar missing
        1 -> Icons.Default.SignalWifi0Bar // Fallback: 1Bar missing
        else -> Icons.Default.SignalWifi0Bar
    }
}

fun getMobileSignalIcon(dbm: Int): ImageVector {
    // Approximate mapping with available icons
    return when {
         dbm > -90 -> Icons.Default.SignalCellular4Bar
         dbm > -105 -> Icons.Default.SignalCellular4Bar // Fallback
         else -> Icons.Default.SignalCellular0Bar // Fallback
    }
}
