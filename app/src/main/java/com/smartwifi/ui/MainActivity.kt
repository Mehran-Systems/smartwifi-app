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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Permissions
        val permissionLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Permissions granted or denied. Service will handle missing permissions gracefully (logs errors).
        }

        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.READ_PHONE_STATE
            )
        )
        
        // Start the background service
        val serviceIntent = Intent(this, SmartWifiService::class.java)
        startForegroundService(serviceIntent) // Should handle version check for startForegroundService vs startService

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            DashboardScreen(
                                onSettingsClick = { navController.navigate("settings") },
                                onSpeedTestClick = { navController.navigate("speed_test") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onNetworkManagerClick = { navController.navigate("network_list") }
                            )
                        }
                        composable("speed_test") {
                             SpeedTestScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("network_list") {
                            NetworkListScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
