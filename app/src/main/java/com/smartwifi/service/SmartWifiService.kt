
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
    private var dataFallbackJob: Job? = null // For 10s Persistence Timer
    
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
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: android.net.NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            // Trigger check for Metered/Unmetered changes or ZOMBIE validation
            val isValidated = networkCapabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!isValidated) {
                 Log.w("SmartWifiService", "Network Capability: Not Validated (Potential Zombie)")
                 serviceScope.launch { performSmartChecks() }
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
        val isWifiMetered = signalMonitor.isMeteredNetwork()
        val isWifiEnabled = signalMonitor.isWifiEnabled()
        
        val currentBssid = actionManager.getConnectedBssid()
        val rawSsid = actionManager.getConnectedSsid()
        val quoteFreeSsid = rawSsid?.replace("\"", "") ?: "Unknown"

        // Update UI
        if (isWifiEnabled && rawSsid != null && rawSsid != "Unknown" && rawSsid != "<unknown ssid>") {
             if (rawSsid != repository.uiState.value.currentSsid) {
                  repository.updateNetworkInfo(rawSsid, rssi, band)
             }
        } else if (isWifiEnabled) { 
             repository.updateNetworkInfo(repository.uiState.value.currentSsid, rssi, band)
        }

        val internetStatusStr = if (hasInternet) "Connected" else "No Internet"
        repository.updateInternetStatus(internetStatusStr)
        
        // --- LOGIC 1: ZOMBIE & PROBATION ---
        
        // Check if current network is a Zombie
        if (currentBssid != null && brain.isZombieConnection(hasInternet) && isWifiEnabled) {
             Log.i("SmartWifiService", "Zombie Detected on $quoteFreeSsid. Disconnecting.")
             repository.updateLastAction("Zombie Detected: $quoteFreeSsid")
             repository.setZombieDetected(true)
             brain.addToProbation(currentBssid)
             actionManager.disconnectNetwork()
             return // Acted, return to loop
        } else {
            repository.setZombieDetected(false)
        }

        // --- LOGIC 2: MOBILE DATA FALLBACK (PERSISTENCE) ---
        
        // Condition: No Internet OR (WifiEnabled but Weak/Unusable)
        // Condition: No Internet OR (WifiEnabled but Weak/Unusable)
        // Sensitivity Mapping: 0 -> -90 (Keep), 100 -> -50 (Drop)
        val sensitivityDbm = -90 + (repository.uiState.value.sensitivity * 0.4).toInt()
        
        val linkSpeed = signalMonitor.getLinkSpeed()
        val speedThreshold = repository.uiState.value.mobileDataThreshold
        val isSlow = isWifiEnabled && linkSpeed < speedThreshold && linkSpeed > 0
        
        val shouldFallback = !hasInternet || (isWifiEnabled && rssi < sensitivityDbm) || isSlow
        
        if (shouldFallback && repository.uiState.value.isDataFallback) {
             if (dataFallbackJob == null) {
                 Log.i("SmartWifiService", "Instability Detected. Starting 10s Persistence Timer...")
                 repository.updateLastAction("Unstable WiFi. Timer Started...")
                 
                 dataFallbackJob = serviceScope.launch {
                     delay(10000) // 10 seconds persistence
                     // Check again
                     if (!internetChecker.hasInternetAccess()) {
                         Log.i("SmartWifiService", "Persistence Failed. Triggering Data Fallback.")
                         repository.updateActiveMode("Mobile Data Fallback")
                         repository.updateConnectionSource(com.smartwifi.data.model.ConnectionSource.MOBILE_DATA)
                         
                         val carrier = mobileMonitor.getCarrierName()
                         val type = mobileMonitor.getNetworkType()
                         repository.updateNetworkInfo(carrier, mobileMonitor.getSignalStrength(), type)
                         // Potentially disconnect WiFi to force cellular if needed, or just update UI and let OS handle request
                         // Note: Android usually auto-switches if we validate "No Internet".
                         // Explicit disconnect:
                         if (signalMonitor.isWifiEnabled()) actionManager.disconnectNetwork()
                     } else {
                         Log.i("SmartWifiService", "Signal Recovered during timer.")
                         repository.updateLastAction("Signal Recovered.")
                     }
                     dataFallbackJob = null
                 }
             }
        } else {
             // Recovered or Stable
             if (dataFallbackJob != null) {
                 Log.i("SmartWifiService", "Stabilized. Cancelling Timer.")
                 dataFallbackJob?.cancel()
                 dataFallbackJob = null
             }
             
             if (isWifiEnabled && hasInternet && currentBssid != null) {
                  repository.updateActiveMode(if (isWifiMetered) "WiFi (Hotspot)" else "Stationary (Fixed WiFi)")
                  repository.updateConnectionSource(com.smartwifi.data.model.ConnectionSource.WIFI_ROUTER)
             }
        }
        
        // --- LOGIC 3: INTELLIGENT SCAN & 5GHz SWITCH ---
        
        // Trigger only if we have a connection to optimize
        if (isWifiEnabled && hasInternet && currentBssid != null && dataFallbackJob == null) {
        
            val shouldScanFor5G = repository.uiState.value.is5GhzPriorityEnabled && band == "2.4GHz"
            val shouldScanForBetterOverall = true // Always look for better fixed wifi
            
            if (shouldScanFor5G || shouldScanForBetterOverall) {
                actionManager.startScan()
                val results = actionManager.getScanResults()
                
                val currentBase = brain.normalizeSsid(quoteFreeSsid)
                val currentScore = brain.calculateDesirabilityScore(rssi, isWifiMetered, band == "5GHz")
                
                // Find candidates
                var bestCandidate: android.net.wifi.ScanResult? = null
                var bestScore = -1
                
                for (scan in results) {
                     val scanSsid = scan.SSID.replace("\"", "")
                     if (scanSsid.isEmpty()) continue
                     
                     // Skip if same BSSID (already connected)
                     if (scan.BSSID == currentBssid) continue
                     
                     // Skip if under probation
                     if (brain.isUnderProbation(scan.BSSID)) continue
                     
                     // 1. Normalization Match (for 5GHz upgrade)
                     val scanBase = brain.normalizeSsid(scanSsid)
                     val isSameFamily = currentBase.equals(scanBase, ignoreCase = true)
                     
                     // 2. Identify Metadata
                     val isTrue5Ghz = scan.frequency > 4900
                     // Heuristic: If same family, inherit metered status. Else assume Fixed (optimistic) or check OUI (too complex).
                     // Requirement implies "Intelligent Candidate Scoring" - let's assume Unmetered unless we know otherwise?
                     // Or strictly penalize unknown hotspots? 
                     // For now: If Same Family, use current status. If different, assume Fixed (Standard WiFi).
                     val candidateIsMetered = if (isSameFamily) isWifiMetered else false 
                     
                     // 5GHz Auto-Switching Logic (Specific)
                     if (shouldScanFor5G && isSameFamily && isTrue5Ghz) {
                          // Check Threshold
                          if (scan.level > repository.uiState.value.fiveGhzThreshold) {
                              // Found 5GHz upgrade!
                              Log.i("SmartWifiService", "5GHz Upgrade Found: $scanSsid")
                              bestCandidate = scan
                              // Boost score to ensure switch
                              bestScore = 999 
                              break // Take it immediately
                          }
                     }
                     
                     // General "Better Network" Scoring
                     val candidateScore = brain.calculateDesirabilityScore(scan.level, candidateIsMetered, isTrue5Ghz)
                     
                     if (brain.shouldSwitchNetwork(rssi, isWifiMetered, scan.level, candidateIsMetered)) {
                          if (candidateScore > bestScore && candidateScore > currentScore) {
                              bestCandidate = scan
                              bestScore = candidateScore
                          }
                     }
                }
                
                if (bestCandidate != null) {
                     Log.i("SmartWifiService", "Switching to Better Network: ${bestCandidate.SSID}")
                     repository.updateLastAction("Switching to: ${bestCandidate.SSID}")
                     
                     // Use suggestion API
                     actionManager.connectTo5GhzNetwork(bestCandidate.SSID.replace("\"", ""), bestCandidate.BSSID)
                }
            }
        }

        // --- Update UI Lists ---
        val probationMap = brain.getProbationList()
        val now = System.currentTimeMillis()
        val probationUiList = probationMap.map { entry ->
            val remaining = (entry.value - now) / 1000
            com.smartwifi.data.model.ProbationItem(entry.key, if (remaining > 0) remaining else 0)
        }
        repository.updateProbationList(probationUiList)

        // Real Available Networks (Scan Results)
        // Detailed "Wifi Analyzer" style list showing individual BSSIDs
        val scanResults = actionManager.getScanResults()
        
        val availableUiList = scanResults
            .filter { it.SSID != null && it.SSID.isNotEmpty() }
            // We want ALL BSSIDs (Physical APs), not just unique SSIDs
            .map { scan -> 
                val isConnected = scan.BSSID == currentBssid
                
                // Map Channel Width
                val width = when (scan.channelWidth) {
                    android.net.wifi.ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                    android.net.wifi.ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                    android.net.wifi.ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                    android.net.wifi.ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                    else -> 20
                }
                
                com.smartwifi.data.model.AvailableNetworkItem(
                    ssid = scan.SSID.replace("\"", ""),
                    bssid = scan.BSSID,
                    level = scan.level,
                    frequency = scan.frequency,
                    capabilities = scan.capabilities,
                    channelWidth = width,
                    isConnected = isConnected
                )
            }
            .sortedByDescending { it.level }
            .take(50)
        
        repository.updateAvailableNetworks(availableUiList)
        

    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        repository.updateServiceStatus(false)
        serviceJob.cancel()
    }
}
