package com.smartwifi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
            }
        )

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            // -- DEBUG LOGS SECTION --
            var showLogDialog by remember { mutableStateOf(false) }
            var logContent by remember { mutableStateOf("") }
            
            SettingsCard(title = "Developer Tools") {
                OutlinedButton(
                    onClick = { 
                        logContent = viewModel.getLogs()
                        showLogDialog = true 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Offline Logs")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { 
                        viewModel.clearLogs()
                        Toast.makeText(context, "Logs Cleared", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Logs")
                }
            }
            
            if (showLogDialog) {
                // Poll for updates every 1s
                LaunchedEffect(Unit) {
                    while(true) {
                        logContent = viewModel.getLogs()
                        kotlinx.coroutines.delay(1000)
                    }
                }
                
                AlertDialog(
                    onDismissRequest = { showLogDialog = false },
                    title = { Text("Debug Logs (Live)") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(text = logContent, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLogDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }
            // -- END DEBUG LOGS SECTION --
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsCard(title = "Theme Mode") {
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
            
            Text("Switching Configuration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Sensitivity (Kept at -40dBm range as requested for 2.4GHz logic)
            SettingsCard(title = "Connection Threshold") {
                val currentDbm = -90 + (uiState.sensitivity / 100f * 50).toInt()
                
                Text("Drop connection if weaker than: $currentDbm dBm")
                Slider(
                    value = uiState.sensitivity.toFloat(),
                    onValueChange = { viewModel.setSensitivity(it.toInt()) },
                    valueRange = 0f..100f
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("-90 dBm", style = MaterialTheme.typography.labelSmall)
                    Text("-40 dBm", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1.5: Badge Trigger
            SettingsCard(title = "Badge Notification Settings") {
                val badgeDbm = -90 + (uiState.badgeSensitivity / 100f * 50).toInt()
                Text("Show 'Poor Signal' Badge if: < $badgeDbm dBm")
                Slider(
                    value = uiState.badgeSensitivity.toFloat(),
                    onValueChange = { viewModel.setBadgeSensitivity(it.toInt()) },
                    valueRange = 0f..100f
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Only Extremely Weak", style = MaterialTheme.typography.labelSmall)
                    Text("Even Slightly Weak", style = MaterialTheme.typography.labelSmall)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Roaming Trigger
            SettingsCard(title = "Roaming Trigger") {
                Text("Look for new network if better by: ${uiState.minSignalDiff} dB")
                Slider(
                    value = uiState.minSignalDiff.toFloat(),
                    onValueChange = { viewModel.setMinSignalDiff(it.toInt()) },
                    valueRange = 2f..30f,
                    steps = 28
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("2 dB (Aggressive)", style = MaterialTheme.typography.labelSmall)
                    Text("30 dB (Stable)", style = MaterialTheme.typography.labelSmall)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: 5GHz Priority (Reverted to standard -50dBm limit as requested)
            SettingsCard(title = "Network Preferences") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prioritize 5GHz Band", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("Switch to 5GHz if available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            valueRange = -90f..-50f, // Reverted max to -50
                            steps = 40 
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("-90 dBm", style = MaterialTheme.typography.labelSmall)
                        Text("-50 dBm", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                    text = { Text("Reset all sensitivity and threshold settings to factory values?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.resetSettings()
                                showResetDialog = false
                                Toast.makeText(context, "Settings restored", Toast.LENGTH_SHORT).show()
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
