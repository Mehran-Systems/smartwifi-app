package com.smartwifi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartwifi.service.SmartWifiService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // After permission handling, start the background service
        startSmartWifiService()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initial permission check on startup
        checkAndRequestPermissions()

        // Handle Badge/Notification Launch Intent
        if (intent.getBooleanExtra("OPEN_PANEL", false)) {
            // Delay slightly to ensure Activity is ready to handle the transition
            // Using a Handler here as we are in standard Activity context, not Compose
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                launchInternetPanel()
            }, 500)
        }

        setContent {
            val viewModel: DashboardViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val isDark = when (uiState.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            // Fix Status Bar Color
            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                androidx.compose.runtime.SideEffect {
                    val window = (view.context as android.app.Activity).window
                    val statusBarColor = if (isDark) {
                         uiState.themeBackground.toInt() 
                    } else {
                         android.graphics.Color.WHITE
                    }
                    window.statusBarColor = statusBarColor
                    androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                }
            }
            
            MaterialTheme(
                colorScheme = if (isDark) {
                    darkColorScheme(
                        background = androidx.compose.ui.graphics.Color(uiState.themeBackground),
                        surface = androidx.compose.ui.graphics.Color(uiState.themeBackground),
                        onSurface = androidx.compose.ui.graphics.Color.White,
                        primary = androidx.compose.ui.graphics.Color(uiState.themeAccent),
                        secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
                        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C2C2C)
                    )
                } else {
                    lightColorScheme(
                        primary = androidx.compose.ui.graphics.Color(uiState.themeAccent),
                        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE0E0E0)
                    )
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet {
                                Spacer(Modifier.height(12.dp))
                                Text("SmartWifi", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                                Divider()
                                // Available Networks option moved to Radar Tap

                                NavigationDrawerItem(
                                    label = { Text("Network Manager") },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate("network_list")
                                    },
                                    icon = { Icon(Icons.Default.Settings, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    label = { Text("Wifi Channels") },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate("wifi_analyzer")
                                    },
                                    icon = { Icon(Icons.Default.ShowChart, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                NavigationDrawerItem(
                                    label = { Text("Speed Test") },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate("speed_test")
                                    },
                                    icon = { Icon(Icons.Default.Speed, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                NavigationDrawerItem(
                                    label = { Text("Settings") },
                                    selected = false,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navController.navigate("settings")
                                    },
                                    icon = { Icon(Icons.Default.Settings, null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }
                        }
                    ) {
                        NavHost(navController = navController, startDestination = "dashboard") {
                            composable(
                                route = "dashboard?open_dialog={open_dialog}",
                                arguments = listOf(
                                    androidx.navigation.navArgument("open_dialog") {
                                        defaultValue = false
                                        type = androidx.navigation.NavType.BoolType
                                    }
                                ),
                                deepLinks = listOf(
                                    androidx.navigation.navDeepLink {
                                        uriPattern = "smartwifi://dashboard?open_dialog={open_dialog}"
                                    }
                                )
                            ) { backStackEntry ->
                                val openDialog = backStackEntry.arguments?.getBoolean("open_dialog") ?: false
                                DashboardScreen(
                                    onSettingsClick = { navController.navigate("settings") },
                                    onSpeedTestClick = { navController.navigate("speed_test") },
                                    onMenuClick = { scope.launch { drawerState.open() } },
                                    shouldOpenDialog = openDialog
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

    /**
     * Checks all required runtime permissions and requests any that are missing.
     * This ensures that any new features added to the app will have their
     * permissions handled correctly on the first run.
     */
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )

        // API 33+ Permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        // Logic for any future additions: add them to this list.
        // permissions.add(Manifest.permission.NEW_PERMISSION_NAME)

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startSmartWifiService()
        }
    }

    private fun startSmartWifiService() {
        val serviceIntent = Intent(this, SmartWifiService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun launchInternetPanel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(panelIntent)
            } else {
                val wifiIntent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(wifiIntent)
            }
        } catch (e: Exception) {
            // Fallback safest
            val wifiIntent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(wifiIntent)
        }
    }
}
