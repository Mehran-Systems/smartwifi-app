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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Smart WiFi", 
                    modifier = Modifier.padding(16.dp), 
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Divider()
                NavigationDrawerItem(
                    label = { Text(text = "Speed Test") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onSpeedTestClick()
                    },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                 NavigationDrawerItem(
                    label = { Text(text = "Settings") },
                    selected = false,
                    onClick = { 
                        scope.launch { drawerState.close() }
                        onSettingsClick()
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                // Simple Top Bar for Menu
                CenterAlignedTopAppBar(
                    title = { Text("Dashboard") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
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
                val compass by viewModel.compassHeading.collectAsState()
                val target by viewModel.targetBearing.collectAsState()
                
                // Calculate Relative Bearing for Pull
                // If Target is 90 (East) and Compass is 0 (North), Pull is 90 (Right).
                // If Target is 90 (East) and Compass is 90 (East), Pull is 0 (Up/Forward).!!
                // Wait. Liquid Radar draws relative to screen "Top".
                // If phone points North (0), and Target is East (90).
                // We want the pull to be to the Right relative to the phone screen.
                // Relative = Target - Compass. 
                // Ex: 90 - 0 = 90 (Right). Correct.
                // Ex: 90 - 90 = 0 (Up). Correct.
                
                val relativePull = target?.let { 
                    var diff = it - compass // e.g. Target 90 - Compass 0 = 90
                    if (diff < 0) diff += 360
                    diff
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(260.dp) // Reduced 320->260
                ) {
                    // Replaced Outer Ripple with Liquid Radar
                    LiquidRadar(
                        modifier = Modifier.fillMaxSize(),
                        blobColor = getRadarColor(uiState.internetStatus),
                        pullBearing = relativePull,
                        pullStrength = 1.0f // Full strength if target exists
                    )
                    
                    // Main Circle (Inner Content)
                    Box(
                        modifier = Modifier
                            .size(170.dp) // Reduced 200->170
                            .clip(CircleShape)
                            // Transparent background to see liquid? Or semi-opaque
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            // .border(4.dp, getRadarColor(uiState.internetStatus).copy(alpha=0.5f), CircleShape) // Optional border
                            ,
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                             
                            Spacer(modifier = Modifier.height(4.dp)) // Reduced 8->4
                            
                            // Dynamic Icon Logic
                            val connectionSource = uiState.connectionSource
                            when (connectionSource) {
                                ConnectionSource.MOBILE_DATA -> {
                                    Icon(
                                        imageVector = getMobileSignalIcon(uiState.signalStrength),
                                        contentDescription = "Mobile Data",
                                        modifier = Modifier.size(48.dp), // Reduced 64->48
                                        tint = getRadarColor(uiState.internetStatus)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = getWifiSignalIcon(uiState.signalStrength),
                                        contentDescription = "WiFi",
                                        modifier = Modifier.size(48.dp), // Reduced 64->48
                                        tint = getRadarColor(uiState.internetStatus)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp)) // Reduced 16->8
                            
                            Text(
                                text = if (connectionSource == ConnectionSource.MOBILE_DATA) uiState.currentSsid else uiState.currentSsid.replace("\"", ""),
                                style = MaterialTheme.typography.headlineSmall, // Reduced HeadlineMedium->HeadlineSmall
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = uiState.internetStatus, 
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.internetStatus == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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

                Spacer(modifier = Modifier.height(16.dp)) // Reduced 24->16
                
                OutlinedButton(onClick = onSettingsClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Advanced Settings")
                }
    }
    // --- Switch Confirmation Dialog Removed (Auto-Switch enabled) ---
    // if (uiState.pendingSwitchNetwork != null) { ... }
        }
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
    description: String,
    isActive: Boolean,
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
            Spacer(modifier = Modifier.height(4.dp))
             Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
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
