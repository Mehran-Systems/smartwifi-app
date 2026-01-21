package com.smartwifi.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartwifi.data.model.ConnectionSource
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit,
    onSpeedTestClick: () -> Unit,
    onMenuClick: () -> Unit,
    shouldOpenDialog: Boolean = false,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showStartupDialog by viewModel.showStartupDialog.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Trigger from navigation
    LaunchedEffect(shouldOpenDialog) {
        if (shouldOpenDialog) {
            // Wait for UI to settle before launching secondary activity (Panel)
            // This prevents "flash and vanish" issues where the OS blocks immediate redirects
            delay(1000) 
            viewModel.showAvailableNetworks()
        }
    }
    
    var popupState by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }

    // Listen for Panel Open Requests
    LaunchedEffect(Unit) {
        viewModel.openPanelEvent.collect {
             try {
                 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                     // Reverting to the "Internet Panel" as requested by user.
                     // usage: Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                     val panelIntent = android.content.Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                         addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                     }
                     context.startActivity(panelIntent)
                 } else {
                     val wifiIntent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                         addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                     }
                     context.startActivity(wifiIntent)
                 }
             } catch (e: Exception) {
                 // Prevent crash if activity launch fails
             }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier.wrapContentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { 
                            viewModel.toggleGamingMode() 
                            popupState = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Gamepad,
                                contentDescription = "Gaming Mode",
                                tint = if (uiState.isGamingMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (popupState) {
                            Popup(
                                alignment = Alignment.BottomCenter,
                                offset = with(density) { IntOffset(0, 56.dp.roundToPx()) },
                                properties = PopupProperties(focusable = false)
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = contentVisible,
                                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.inverseSurface,
                                        shadowElevation = 8.dp,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .widthIn(max = 260.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.isGamingMode) Icons.Default.SportsEsports else Icons.Default.AutoMode,
                                                contentDescription = null,
                                                tint = if (uiState.isGamingMode) Color(0xFF4CAF50) else Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = if (uiState.isGamingMode) 
                                                    "Gaming Mode: ON\nNetwork switching paused" 
                                                else 
                                                    "Gaming Mode: OFF\nAuto-optimization active",
                                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                                style = MaterialTheme.typography.labelMedium,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
            Spacer(modifier = Modifier.height(16.dp))
            
            // --- SWITCH PROMPT ---
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.pendingSwitchNetwork != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                uiState.pendingSwitchNetwork?.let { network ->
                    // THEME FIX: Check APP SETTING first, then System Theme
                    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val isDark = when (uiState.themeMode) {
                        "LIGHT" -> false
                        "DARK" -> true
                        else -> isSystemDark
                    }
                    
                    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color.White
                    val titleColor = if (isDark) Color.White else Color.Black
                    val bodyColor = if (isDark) Color.Gray else Color.DarkGray
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg), 
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Green Icon
                            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Better Network Found", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                                Text("Switch to: ${network.ssid} (${if (network.frequency > 4900) "5GHz" else "2.4GHz"})", style = MaterialTheme.typography.bodySmall, color = bodyColor)
                            }
                            Button(
                                onClick = { viewModel.performPendingSwitch() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50), // Hardcoded Green
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Switch")
                            }
                            IconButton(onClick = { viewModel.clearPendingSwitch() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = bodyColor)
                            }
                        }
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(340.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { viewModel.showAvailableNetworks() } // Tap Radar to Open Dialog
            ) {
                val radarColor = getRadarColor(uiState.internetStatus, uiState.signalStrength, uiState.connectionSource)
                LiquidRadar(
                    modifier = Modifier.fillMaxSize(),
                    blobColor = radarColor,
                    pulseSpeed = if (uiState.internetStatus == "Connected" || uiState.signalStrength > -75) 1.0f else 0.5f
                )
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), CircleShape)
                ) {
                    val rssi = uiState.signalStrength
                    val signalProgress = ((rssi + 140).toFloat() / 100f).coerceIn(0f, 1f)
                    
                    CircularProgressIndicator(
                        progress = 1f,
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 6.dp
                    )
                    
                    CircularProgressIndicator(
                        progress = signalProgress,
                        modifier = Modifier.fillMaxSize(),
                        color = radarColor,
                        strokeWidth = 6.dp
                    )
                
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        val connectionSource = uiState.connectionSource
                        val signalIcon = when (connectionSource) {
                            ConnectionSource.MOBILE_DATA -> Icons.Default.SignalCellular4Bar
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


                        val infoText = if (uiState.connectionSource == ConnectionSource.MOBILE_DATA) 
                                            "${uiState.signalStrength} dBm" 
                                       else 
                                            "${uiState.frequencyBand} • ${uiState.signalStrength} dBm"
                                            
                        Text(
                            text = infoText, 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        

                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // --- OVERLAY PERMISSION PROMPT ---
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Enable Switch Badge", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Get a floating icon when a better network is found.", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { 
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Enable")
                        }
                    }
                }
            }

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

            Spacer(modifier = Modifier.height(16.dp))
    
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Link Speed", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${uiState.linkSpeed} Mbps", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50) // Green Theme
                        )
                    }
                     
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.2f)))
    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Current Usage", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = uiState.currentUsage, 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50) // Green Theme
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Status: ${uiState.lastAction}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusIcon(icon: ImageVector, label: String, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}

fun getRadarColor(status: String, rssi: Int, source: ConnectionSource): Color {
    if (status == "No Internet") return Color(0xFFF44336) // Red (Priority Warning)
    if (source == ConnectionSource.MOBILE_DATA) return Color(0xFF2196F3) // Blue
    
    // WiFi Signal Thresholds
    return when {
         rssi >= -65 -> Color(0xFF4CAF50) // Strong Green
         rssi >= -80 -> Color(0xFFFFC107) // Moderate Yellow
         else -> Color(0xFFFF5722) // Weak Orange/Red
    }
}

fun getWifiSignalIcon(rssi: Int): ImageVector {
    val level = android.net.wifi.WifiManager.calculateSignalLevel(rssi, 5)
    return if (level >= 4) Icons.Rounded.SignalWifi4Bar else Icons.Rounded.SignalWifi0Bar
}
