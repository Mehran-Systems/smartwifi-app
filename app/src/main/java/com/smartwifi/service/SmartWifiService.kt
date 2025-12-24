
package com.smartwifi.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.content.Context
import android.net.ConnectivityManager // Fix Import
import android.net.Network
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import com.smartwifi.logic.*

// Stub for demo counting
object BrainStub { var demoCounter = 0 }

@AndroidEntryPoint
class SmartWifiService : Service() {

    @Inject lateinit var signalMonitor: SignalMonitor
    @Inject lateinit var internetChecker: InternetLivenessChecker
    // Using simple instantiation for now
    private lateinit var userContextMonitor: UserContextMonitor
    private lateinit var mobileMonitor: MobileNetworkMonitor
    
    @Inject lateinit var repository: com.smartwifi.data.SmartWifiRepository
    @Inject lateinit var brain: com.smartwifi.logic.NetworkDecisionBrain
    @Inject lateinit var actionManager: com.smartwifi.logic.WifiActionManager

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    // Instant Update Callback
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d("SmartWifiService", "NetworkCallback: Available")
            serviceScope.launch {
                delay(500) 
                performSmartChecks() 
            }
        }

        override fun onLost(network: Network) {
             super.onLost(network)
             Log.d("SmartWifiService", "NetworkCallback: Lost")
             serviceScope.launch {
                performSmartChecks()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        userContextMonitor = UserContextMonitor(this)
        mobileMonitor = MobileNetworkMonitor(this)
        
        createNotificationChannel()
        startForeground(1, createNotification())
        startMonitoring()
    }
    
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = "SMART_WIFI_CHANNEL"
            val channel = android.app.NotificationChannel(channelId, "Smart WiFi Service", android.app.NotificationManager.IMPORTANCE_LOW)
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "SMART_WIFI_CHANNEL"
        return android.app.Notification.Builder(this, channelId)
            .setContentTitle("Smart WiFi Running")
            .setContentText("Optimizing your connection...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .build()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SmartWifiService", "Service Started")
        // Foreground started in onCreate
        repository.updateServiceStatus(true)
        return START_STICKY
    }

    private fun startMonitoring() {
        // Traffic Monitoring Loop
        serviceScope.launch {
            var lastRx = android.net.TrafficStats.getTotalRxBytes()
            var lastTx = android.net.TrafficStats.getTotalTxBytes()
            var lastTime = System.currentTimeMillis()

            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                val currentRx = android.net.TrafficStats.getTotalRxBytes()
                val currentTx = android.net.TrafficStats.getTotalTxBytes()
                val deltaBytes = (currentRx - lastRx) + (currentTx - lastTx)
                val deltaTime = now - lastTime
                val speedBps = if (deltaTime > 0) (deltaBytes * 1000) / deltaTime else 0
                val speedStr = if (speedBps > 1024 * 1024) String.format("%.1f MB/s", speedBps / (1024f * 1024f)) else String.format("%d KB/s", speedBps / 1024)

                val rawLinkSpeed = signalMonitor.getLinkSpeed()
                val displayLinkSpeed = if (signalMonitor.isWifiEnabled() && rawLinkSpeed > 0) rawLinkSpeed else 0
                repository.updateTrafficStats(displayLinkSpeed, speedStr)

                lastRx = currentRx
                lastTx = currentTx
                lastTime = now
            }
        }

        // Main Decision Loop
        serviceScope.launch {
            while (true) {
                performSmartChecks()
                delay(5000) 
            }
        }
        
        // Register Callback
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = android.net.NetworkRequest.Builder()
             .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
             .addTransportType(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
             .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch(e: Exception) {
            Log.e("SmartWifiService", "Failed to register callback", e)
        }
    }

    private suspend fun performSmartChecks() {
        if (!repository.uiState.value.isServiceRunning) return

        // 1. Gaming Mode
        val isManualGaming = repository.uiState.value.isGamingMode
        if (isManualGaming || userContextMonitor.isGamingMode()) {
            repository.updateActiveMode("Gaming Mode (Paused)")
            if (!isManualGaming) repository.setGamingMode(true)
            return
        } else {
            if (!isManualGaming) repository.setGamingMode(false) 
        }

        // 2. Network Analysis
        val hasInternet = internetChecker.hasInternetAccess()
        val rssi = signalMonitor.getRssi()
        val freq = signalMonitor.getFrequency()
        val band = if (freq > 4900) "5GHz" else "2.4GHz"
        
        val currentBssid = actionManager.getConnectedBssid()
        val rawSsid = actionManager.getConnectedSsid()

        // 0.5. Check if WiFi is actually enabled
        if (!signalMonitor.isWifiEnabled()) {
             // WiFi is OFF.
             val isManualFallback = repository.uiState.value.isDataFallback
             if (!isManualFallback) {
                 repository.updateNetworkInfo("Disconnected", -127, "-")
                 repository.updateConnectionSource(com.smartwifi.data.model.ConnectionSource.WIFI_ROUTER) 
             }
        } 
        
        // 5GHz Priority Check
        if (repository.uiState.value.is5GhzPriorityEnabled && band == "2.4GHz" && !brain.shouldEnterProbation(rssi, hasInternet)) {
             // Only switch if current signal is usable but we want faster. 
             // Don't switch if we are already in probation/zombie mode (handled elsewhere)
             
             // 1. Trigger Scan (Async)
             actionManager.startScan()
             
             // 2. Check existing results (might be fresh enough)
             val results = actionManager.getScanResults()
             val currentSsidQuoteFree = rawSsid?.replace("\"", "") ?: ""
             
             if (currentSsidQuoteFree.isNotEmpty()) {
                 Log.d("SmartWifiService", "5GHz Check: Scanning... Found ${results.size} networks. Current: $currentSsidQuoteFree")
                 
                 // Find 5GHz version of SAME SSID or "5G" variant
                 val betterNetwork = results.firstOrNull { 
                    val scanSsid = it.SSID.replace("\"", "")
                
                // Smart Match: Normalize both SSIDs to their "Base Name"
                // e.g. "HomeWifi-5G" -> "HomeWifi", "HomeWifi" -> "HomeWifi"
                fun normalize(name: String): String {
                    return name.replace(Regex("(?i)[-_ ]?(5g|5ghz|2\\.4g|2\\.4ghz)"), "")
                }
                
                val currentBase = normalize(currentSsidQuoteFree)
                val scanBase = normalize(scanSsid)
                
                val namesMatch = currentBase.equals(scanBase, ignoreCase = true)
                
                // STRICT ATTRIBUTE CHECK: Trust Hardware Frequency, Not Name
                val isTrue5Ghz = it.frequency > 4900 
                val threshold = repository.uiState.value.fiveGhzThreshold
                val isStrong = it.level > threshold
                
                if (namesMatch && isTrue5Ghz) {
                     Log.v("SmartWifiService", "Candidate found via Attribute Check: $scanSsid (${it.frequency}MHz) Match=$namesMatch. Level: ${it.level} > $threshold?")
                }
                
                namesMatch && isTrue5Ghz && isStrong
                 }
                 
                 if (betterNetwork != null) {
                     Log.i("SmartWifiService", "Found Better 5GHz Network: ${betterNetwork.SSID} (${betterNetwork.BSSID}). Switching...")
                     repository.updateLastAction("Switching to 5GHz: ${betterNetwork.SSID}")
                     actionManager.connectTo5GhzNetwork(currentSsidQuoteFree, betterNetwork.BSSID)
                 } else {
                     Log.d("SmartWifiService", "No better 5GHz network found for $currentSsidQuoteFree (Scanned ${results.size})")
                 }
             } else {
                 Log.w("SmartWifiService", "Skipping 5GHz check: Current SSID unknown")
             }
        }
        
        // Logic: Valid WiFi Connection
        if (signalMonitor.isWifiEnabled() && rawSsid != null && rawSsid != "Unknown" && rawSsid != "<unknown ssid>") {
             if (repository.uiState.value.isDataFallback) {
                 repository.updateConnectionSource(com.smartwifi.data.model.ConnectionSource.WIFI_ROUTER)
             }
             if (rawSsid != repository.uiState.value.currentSsid) {
                  repository.updateNetworkInfo(rawSsid, rssi, band)
             }
        } else if (signalMonitor.isWifiEnabled()) { 
             repository.updateNetworkInfo(repository.uiState.value.currentSsid, rssi, band)
        }

        val internetStatusStr = if (hasInternet) "Connected" else "No Internet"
        repository.updateInternetStatus(internetStatusStr)
        
        // Scenario C: Data Fallback
        if ((!hasInternet || !signalMonitor.isWifiEnabled()) && repository.uiState.value.isDataFallback) {
             repository.updateActiveMode("Mobile Data Fallback")
             repository.updateConnectionSource(com.smartwifi.data.model.ConnectionSource.MOBILE_DATA)
             
             val carrier = mobileMonitor.getCarrierName()
             val networkType = mobileMonitor.getNetworkType()
             val signal = mobileMonitor.getSignalStrength() 
             repository.updateNetworkInfo(carrier, signal, networkType)
             repository.updateTrafficStats(0, repository.uiState.value.currentUsage) 
        } else {
             if (signalMonitor.isWifiEnabled() && hasInternet) {
                  repository.updateActiveMode("Stationary (Home/Office)")
             }
        }
        
        // Brain Logic Execution (Zombie Check)
        val currentSsid = repository.uiState.value.currentSsid
        if (currentBssid != null && brain.isUnderProbation(currentBssid)) {
            repository.updateLastAction("Disconnecting Zombie: $currentSsid")
            repository.setZombieDetected(true)
            actionManager.disconnectNetwork() 
        } else {
            repository.setZombieDetected(false)
        }

        // 3. Scenario B: Travel Mode (Zombie Detection)
        // Note: 'else if' removed to allow independent checks if needed, but logic flow suggests priority
        if (!brain.shouldTriggerDataFallback(rssi, hasInternet) && brain.shouldEnterProbation(rssi, hasInternet)) {
            Log.i("SmartWifiService", "Scenario B: Zombie Hotspot detection. Entering Probation.")
            repository.updateLastAction("Zombie Detected: Entering Probation")
            repository.setZombieDetected(true)
            // If it was a hotspot, we might want to flag it here, but broadly it's a wifi issue
            currentBssid?.let { 
                brain.addToProbation(it) 
                actionManager.disconnectNetwork()
            }
        }

        // --- Update UI Lists ---
        // Probation List
        val probationMap = brain.getProbationList()
        val now = System.currentTimeMillis()
        val probationUiList = probationMap.map { entry ->
            val remaining = (entry.value - now) / 1000
            com.smartwifi.data.model.ProbationItem(entry.key, if (remaining > 0) remaining else 0)
        }
        repository.updateProbationList(probationUiList)

        // Saved Networks (Mock for Demo/UI.txt)
        val mockSaved = listOf(
            com.smartwifi.data.model.SavedNetworkItem("Office_Floor2", -55, true),
            com.smartwifi.data.model.SavedNetworkItem("Home_Wifi_5G", -72, true),
            com.smartwifi.data.model.SavedNetworkItem("Starbucks_Public", -85, false)
        )
        repository.updateSavedNetworks(mockSaved)

        // 4. Scenario A: Stationary Mode (Sticky Client)
        // Demo Logic: Simulate finding a better network occasionally if we are on "Home_Wifi_5G"
        // In real app, this would use ScanResults and minSignalDiff from settings
        if (currentSsid == "Home_Wifi_5G" && rssi < -70 && brain.shouldTriggerDataFallback(rssi, hasInternet) == false) {
             // Auto-Switch Logic (No Dialog)
             Log.i("SmartWifiService", "Auto-Switching to better network: Office_Floor2")
             repository.updateLastAction("Auto-Switching to Office_Floor2")
             // Simulate connection success
             repository.updateNetworkInfo("Office_Floor2", -55, "5GHz")
        } else if (currentSsid == "Office_Floor2" && rssi < -75) {
             // Simulate attempting to switch to a Hotspot (Starbucks_Public)
             if (repository.uiState.value.isHotspotSwitchingEnabled) {
                 Log.i("SmartWifiService", "Switching to Hotspot: Starbucks_Public")
                 repository.updateNetworkInfo("Starbucks_Public", -60, "2.4GHz")
                 repository.updateConnectionSource(com.smartwifi.data.model.ConnectionSource.WIFI_HOTSPOT)
             } else {
                 Log.i("SmartWifiService", "Skipping Hotspot (Starbucks_Public) due to user setting.")
                 repository.updateLastAction("Skipped Hotspot: Starbucks_Public")
             }
        if (currentSsid == "Unknown" && BrainStub.demoCounter == 2) {
             // Force connection for demo
             Log.i("SmartWifiService", "Auto-Connecting to Home_Wifi_5G")
             repository.updateNetworkInfo("Home_Wifi_5G", -65, "5GHz")
        }
        BrainStub.demoCounter++
    }}


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.updateServiceStatus(false)
        serviceJob.cancel()
    }
}
