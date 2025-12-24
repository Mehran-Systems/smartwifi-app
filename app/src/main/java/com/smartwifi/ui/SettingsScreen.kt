package com.smartwifi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        SmallTopAppBar(
            title = { Text("Advanced Settings") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.smallTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsCard(title = "Appearance") {
                val map = mapOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    map.forEach { (mode, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = uiState.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) }
                            )
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showThemeDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Customize Theme Colors")
                }
            }
            
            if (showThemeDialog) {
                com.smartwifi.ui.components.ThemeSelectionDialog(
                    currentBackground = uiState.themeBackground,
                    currentAccent = uiState.themeAccent,
                    onDismiss = { showThemeDialog = false },
                    onConfirm = { bg, accent ->
                        viewModel.setThemeColors(bg, accent)
                        showThemeDialog = false
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("The Brain Configuration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Switching Sensitivity
            // Section 1: Switching Sensitivity (Min RSSI to maintain connection)
            SettingsCard(title = "Connection Threshold (Sensitivity)") {
                // Map 0-100 to -90dBm to -50dBm
                // 0 -> -90 (Very Conservative, holds on to weak signal)
                // 100 -> -50 (Very Aggressive, drops signal easily)
                val currentDbm = -90 + (uiState.sensitivity / 100f * 40).toInt()
                
                Text("Drop connection if weaker than: $currentDbm dBm")
                Slider(
                    value = uiState.sensitivity.toFloat(),
                    onValueChange = { viewModel.setSensitivity(it.toInt()) },
                    valueRange = 0f..100f
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Conservative (-90)", style = MaterialTheme.typography.labelSmall)
                    Text("Aggressive (-50)", style = MaterialTheme.typography.labelSmall)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Signal Difference (Better Network Threshold)
            SettingsCard(title = "Roaming Trigger") {
                Text("Look for new network if better by: ${uiState.minSignalDiff} dB")
                Slider(
                    value = uiState.minSignalDiff.toFloat(),
                    onValueChange = { viewModel.setMinSignalDiff(it.toInt()) },
                    valueRange = 5f..30f,
                    steps = 24 
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("5 dB (Frequent)", style = MaterialTheme.typography.labelSmall)
                    Text("30 dB (Stable)", style = MaterialTheme.typography.labelSmall)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Mobile Data Logic
            SettingsCard(title = "Mobile Data Logic") {
                Text("Switch to data if Wi-Fi speed below: ${uiState.mobileDataThreshold} Mbps")
                Slider(
                    value = uiState.mobileDataThreshold.toFloat(),
                    onValueChange = { viewModel.setMobileDataThreshold(it.toInt()) },
                    valueRange = 1f..20f,
                    steps = 19
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
             
            // Section 3: Network Preferences (NEW)
            SettingsCard(title = "Network Preferences") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prioritize 5GHz Band", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("Prefer faster 5GHz networks over 2.4GHz range.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = uiState.is5GhzPriorityEnabled,
                        onCheckedChange = { viewModel.set5GhzPriorityEnabled(it) }
                    )
                }
                
                if (uiState.is5GhzPriorityEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Min 5GHz Signal: ${uiState.fiveGhzThreshold} dBm", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                            value = uiState.fiveGhzThreshold.toFloat(),
                            onValueChange = { viewModel.setFiveGhzThreshold(it.toInt()) },
                            valueRange = -90f..-50f,
                            steps = 39 
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Weak (-90)", style = MaterialTheme.typography.labelSmall)
                        Text("Strong (-50)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Section 3: Hotspot Handling
            SettingsCard(title = "Network Handling") {
                 Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Switch to Hotspots", fontWeight = FontWeight.Bold)
                        Text(
                            "Allow app to auto-connect to mobile hotspots.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = uiState.isHotspotSwitchingEnabled,
                        onCheckedChange = { viewModel.setHotspotSwitchingEnabled(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Gaming Mode Setup
            SettingsCard(title = "Gaming Mode Setup") {
                Button(
                    onClick = { Toast.makeText(context, "Scanning installed games...", Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Games")
                }
                Text(
                    "Select apps that should pause network scanning.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: Battery Saver
            SettingsCard(title = "Battery Saver") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Geofencing", fontWeight = FontWeight.Bold)
                        Text("Only scan aggressively at Home/Office", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = uiState.isGeofencingEnabled,
                        onCheckedChange = { viewModel.setGeofencing(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Restore Defaults Option
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore Default Settings")
            }
            
            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("Restore Defaults?") },
                    text = { Text("This will reset all sensitivity and threshold settings to their original factory values. This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.resetSettings()
                                showResetDialog = false
                                Toast.makeText(context, "Settings restored to defaults", Toast.LENGTH_SHORT).show()
                            },
                             colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Restore")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
