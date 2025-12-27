package com.smartwifi.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.smartwifi.service.SmartWifiService
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... (Permissions code unchanged) ...
        
        // Start the background service
        val serviceIntent = Intent(this, SmartWifiService::class.java)
        startForegroundService(serviceIntent) 

        setContent {
            val viewModel: DashboardViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val isDark = when (uiState.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            MaterialTheme(
                colorScheme = if (isDark) {
                    androidx.compose.material3.darkColorScheme(
                        background = androidx.compose.ui.graphics.Color(uiState.themeBackground),
                        surface = androidx.compose.ui.graphics.Color(uiState.themeBackground),
                        onSurface = androidx.compose.ui.graphics.Color.White,
                        primary = androidx.compose.ui.graphics.Color(uiState.themeAccent),
                        secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
                        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C2C2C) // Dark Grey for boxes
                    )
                } else {
                    androidx.compose.material3.lightColorScheme(
                        primary = androidx.compose.ui.graphics.Color(uiState.themeAccent),
                        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE0E0E0) // Silver for boxes
                    )
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
                    val scope = androidx.compose.runtime.rememberCoroutineScope()

                    androidx.compose.material3.ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            androidx.compose.material3.ModalDrawerSheet {
                                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                                androidx.compose.material3.Text("SmartWifi", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                                androidx.compose.material3.Divider()
                                androidx.compose.material3.NavigationDrawerItem(
                                    label = { androidx.compose.material3.Text("Network Manager") },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate("network_list")
                                    },
                                    icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.List, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                androidx.compose.material3.NavigationDrawerItem(
                                    label = { androidx.compose.material3.Text("Wifi Channels") },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate("wifi_analyzer")
                                    },
                                    icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ShowChart, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                androidx.compose.material3.NavigationDrawerItem(
                                    label = { androidx.compose.material3.Text("Speed Test") },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate("speed_test")
                                    },
                                    icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Speed, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                androidx.compose.material3.Divider(modifier = Modifier.padding(vertical = 8.dp))
                                androidx.compose.material3.NavigationDrawerItem(
                                    label = { androidx.compose.material3.Text("Settings") },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate("settings")
                                    },
                                    icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Settings, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    ) {
                        NavHost(navController = navController, startDestination = "dashboard") {
                            composable("dashboard") {
                                DashboardScreen(
                                    onSettingsClick = { navController.navigate("settings") },
                                    onSpeedTestClick = { navController.navigate("speed_test") },
                                    onMenuClick = { scope.launch { drawerState.open() } }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                            composable("speed_test") {
                                SpeedTestScreen(
                                    onBackClick = { navController.popBackStack() },
                                    onHistoryClick = { navController.navigate("history") }
                                )
                            }
                            composable("history") {
                                SpeedTestHistoryScreen(
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                            composable("network_list") {
                                NetworkListScreen(
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                            composable("wifi_analyzer") {
                                WifiAnalyzerScreen(
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
