
package com.smartwifi.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import com.smartwifi.logic.*
import com.smartwifi.data.model.ConnectionSource
import com.smartwifi.data.model.AvailableNetworkItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class SmartWifiService : Service() {

    @Inject lateinit var signalMonitor: SignalMonitor
    @Inject lateinit var internetChecker: InternetLivenessChecker
    private lateinit var userContextMonitor: UserContextMonitor
    private lateinit var mobileMonitor: MobileNetworkMonitor
    
    @Inject lateinit var repository: com.smartwifi.data.SmartWifiRepository
    @Inject lateinit var brain: com.smartwifi.logic.NetworkDecisionBrain
    @Inject lateinit var actionManager: com.smartwifi.logic.WifiActionManager
    @Inject lateinit var debugger: com.smartwifi.logic.SmartWifiDebugger

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    private var currentWifiInfo: WifiInfo? = null
    private val wifiManager by lazy { getSystemService(Context.WIFI_SERVICE) as WifiManager }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val info = caps.transportInfo as? WifiInfo
            if (info != null) {
                currentWifiInfo = info
                Log.d("SmartWifiService", "NetworkCallback: WifiInfo Updated. RSSI: ${info.rssi}")
            }
        }
        override fun onLost(network: Network) { 
            currentWifiInfo = null 
            Log.d("SmartWifiService", "NetworkCallback: WiFi Lost")
        }
    }

    override fun onCreate() {
        super.onCreate()
        userContextMonitor = UserContextMonitor(this)
        mobileMonitor = MobileNetworkMonitor(this)
        
        createNotificationChannel()
        startForeground(1, createNotification())
        
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(networkCallback)
        
        // Receiver 1: System Broadcast (Scan Results)
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                serviceScope.launch { performSmartOptimization() }
            }
        }, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        // Receiver 2: Internal Debug Broadcast (Needs NOT_EXPORTED flag on Android 14+)
        val debugFilter = IntentFilter("DEBUG_SHOW_BADGE")
        val debugReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                serviceScope.launch(Dispatchers.Main) { 
                    Log.i("SmartWifiService", "DEBUG: Forcing Badge Display")
                    showBadge() 
                }
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13/14 requires explicit export flag for non-system broadcasts
            registerReceiver(debugReceiver, debugFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(debugReceiver, debugFilter)
        }
        
        monitorSettingsChanges()
        startMonitoring()
    }
    
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelId = "SMART_WIFI_SUGGESTIONS" // Changed ID to force update
            // Upgrade to HIGH for Heads-up notifications
            val channel = android.app.NotificationChannel(channelId, "Smart WiFi Suggestions", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for network switching suggestions"
                enableVibration(true)
                setShowBadge(true)
            }
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val channelId = "SMART_WIFI_CHANNEL" // Keep foreground service on silent channel if needed, or use same.
        // Actually, let's keep the foreground service separate so it doesn't annoy usage
        val serviceChannelId = "SMART_WIFI_SERVICE"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
             val channel = android.app.NotificationChannel(serviceChannelId, "Smart WiFi Service", android.app.NotificationManager.IMPORTANCE_LOW)
             getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        return android.app.Notification.Builder(this, serviceChannelId)
            .setContentTitle("Smart WiFi Monitor")
            .setContentText("Scanning for better networks...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .build()
    }

    private fun showSwitchNotification(ssid: String) {
        val channelId = "SMART_WIFI_SUGGESTIONS"
        
        // Create intent to open Dashboard with Dialog
        val intent = Intent(this, com.smartwifi.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            data = android.net.Uri.parse("smartwifi://dashboard?open_dialog=true") // Deep link style or just handle extras
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notif = android.app.Notification.Builder(this, channelId)
            .setContentTitle("Better WiFi Found!")
            .setContentText("Tap to connect to $ssid")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // Actionable
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setDefaults(android.app.Notification.DEFAULT_ALL)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .build()
        getSystemService(android.app.NotificationManager::class.java).notify(2, notif)
    }

    private fun monitorSettingsChanges() {
        serviceScope.launch {
            repository.uiState
                .map { Triple(it.sensitivity, it.fiveGhzThreshold, it.minSignalDiff) }
                .distinctUntilChanged()
                .collect { (sens, fiveG, roam) ->
                    debugger.logSettingsChange("Sensitivity: $sens, 5GHz Threshold: $fiveG, Roaming Diff: $roam")
                    Log.i("SmartWifiService", "Settings Applied: Threshold=$sens, 5G_Min=$fiveG, RoamTrigger=$roam")
                }
        }
    }

    // --- Floating Badge Logic ---
    private var badgeView: android.view.View? = null
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager }

    private fun showBadge() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Log.w("SmartWifiService", "Cannot show badge: Overlay permission missing")
            return
        }

        if (badgeView != null) return // Already showing

        try {
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    android.view.WindowManager.LayoutParams.TYPE_PHONE,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                y = 200 // Offset from top
                x = 0 // sticky to right edge
            }

            // Create Programmatic View (Simple semi-transparent icon)
            val icon = android.widget.ImageView(this).apply {
                setImageResource(android.R.drawable.ic_dialog_info) // Fallback icon, acts as indicator
                // We'd ideally use a wifi vector, but system drawable is safe for now.
                // Or we can load R.drawable.ic_stat_name if available or create a shape.
                // Let's use a standard system icon for WiFi if possible, or generic.
                // Using a color filter to make it look "Active"
                setColorFilter(android.graphics.Color.parseColor("#4CAF50")) 
                setBackgroundColor(android.graphics.Color.parseColor("#CCFFFFFF")) // Semi-transparent white bg
                setPadding(20, 20, 20, 20)
                
                // Circular Shape
                background = android.graphics.drawable.GradientDrawable().apply {
                     shape = android.graphics.drawable.GradientDrawable.OVAL
                     setColor(android.graphics.Color.parseColor("#E0FFFFFF"))
                     setStroke(2, android.graphics.Color.parseColor("#4CAF50"))
                }
                
                setOnClickListener {
                     // Launch Panel Intent using PendingIntent to bypass background start restrictions
                     try {
                         val intent = Intent(context, com.smartwifi.ui.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("OPEN_PANEL", true)
                        }
                        
                        val pendingIntent = android.app.PendingIntent.getActivity(
                            context, 0, intent, 
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        
                        val options = if (android.os.Build.VERSION.SDK_INT >= 34) { // Android 14 (UpsideDownCake)
                            android.app.ActivityOptions.makeBasic().apply {
                                setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                            }.toBundle()
                        } else {
                            null
                        }
                        
                        pendingIntent.send(options)
                        hideBadge() // Auto hide on click
                     } catch (e: Exception) {
                         Log.e("SmartWifiService", "Failed to launch intent from badge", e)
                         // Fail gracefully with a toast
                         android.widget.Toast.makeText(context, "Tap Notification to switch", android.widget.Toast.LENGTH_SHORT).show()
                     }
                }
            }
            
            badgeView = icon
            windowManager.addView(badgeView, params)
            
            // Auto-hide after 15 seconds
            serviceScope.launch {
                delay(15000)
                hideBadge()
            }
            
        } catch (e: Exception) {
            Log.e("SmartWifiService", "Failed to show badge", e)
        }
    }

    private fun hideBadge() {
        try {
            if (badgeView != null) {
                windowManager.removeView(badgeView)
                badgeView = null
            }
        } catch (e: Exception) {
            badgeView = null 
        }
    }

    private fun getBestWifiInfo(): WifiInfo? {
        val cbInfo = currentWifiInfo
        // If callback info seems valid (has SSID and BSSID), use it
        if (cbInfo != null && cbInfo.ssid != "<unknown ssid>" && cbInfo.bssid != "02:00:00:00:00:00" && cbInfo.bssid != null) {
            return cbInfo
        }
        // Fallback to legacy API which sometimes has better permission access on older Androids
        try {
            val legacy = wifiManager.connectionInfo
            if (legacy != null && legacy.networkId != -1 && legacy.ssid != "<unknown ssid>") {
                Log.d("SmartWifiService", "DEBUG: Using Legacy WifiInfo: ${legacy.ssid}")
                return legacy
            }
        } catch (e: Exception) {
            Log.e("SmartWifiService", "Error getting legacy wifi info", e)
        }
        return cbInfo
    }

    private fun startMonitoring() {
        serviceScope.launch {
            var lastRx = android.net.TrafficStats.getTotalRxBytes()
            var lastTx = android.net.TrafficStats.getTotalTxBytes()
            var loopCount = 0
            
            while (true) {
                val now = System.currentTimeMillis()
                val currentRx = android.net.TrafficStats.getTotalRxBytes()
                val currentTx = android.net.TrafficStats.getTotalTxBytes()
                val delta = (currentRx - lastRx) + (currentTx - lastTx)
                
                val speedKbs = if (delta > 0) (delta / 1024.0 / 2.0) else 0.0
                val speedStr = if (speedKbs > 1024) String.format("%.1f MB/s", speedKbs / 1024.0) else String.format("%.1f KB/s", speedKbs)

                // Use the BEST available info
                val info = getBestWifiInfo()
                
                // Retrieve SSID with robust fallback
                var displaySsid = "Scanning..."
                if (info != null) {
                    var rawSsid = info.ssid.replace("\"", "")
                    if (rawSsid == "<unknown ssid>" || rawSsid.isEmpty() || rawSsid == "0x") {
                         // Fallback: Match BSSID against latest scan results using robust logic
                         val rawCurrentBssid = info.bssid ?: ""
                         val normalizedCurrent = rawCurrentBssid.replace(":", "").uppercase().trim()
                         
                         val match = actionManager.getScanResults().find { 
                             val scanBssid = (it.BSSID ?: "").replace(":", "").uppercase().trim()
                             scanBssid.isNotEmpty() && scanBssid == normalizedCurrent
                         }
                         
                         if (match != null) {
                             rawSsid = match.SSID.replace("\"", "")
                             Log.i("SmartWifiService", "DEBUG: Recovered SSID '${rawSsid}' from scan results using BSSID match.")
                         } else {
                             // Last Resort: Show BSSID to User/Debug
                             rawSsid = if (rawCurrentBssid.isNotEmpty()) "Connected (${rawCurrentBssid.takeLast(8)})" else "Connected WiFi"
                             Log.w("SmartWifiService", "DEBUG: Failed to recover SSID. Check Location Permissions. Info: $info")
                         }
                    }
                    displaySsid = rawSsid
                }
                
                updateCoreUI(info, displaySsid) 
                
                val ui = repository.uiState.value
                val linkSpeed = if (ui.connectionSource == ConnectionSource.MOBILE_DATA) mobileMonitor.getLinkSpeed() else (info?.linkSpeed ?: 0)
                repository.updateTrafficStats(if (linkSpeed > 0) linkSpeed else 10, speedStr)

                // FORCED HEARTBEAT: Call optimization every loop
                performSmartOptimization()

                val loopDelay = 2000L
                
                // Aggressive Monitoring: Check often to beat the OS switcher
                // Android throttles scans to 4 times per 2 mins (approx every 30s) for foreground apps.
                // However, we can try to be as frequent as allowed.
                // Reducing gap to 6 seconds (loopCount % 3).
                if (loopCount % 3 == 0 && signalMonitor.isWifiEnabled()) {
                    actionManager.startScan()
                    debugger.logDecision("Heartbeat: Requesting Scan...")
                }

                lastRx = currentRx
                lastTx = currentTx
                loopCount++
                delay(loopDelay)
            }
        }
    }

    private suspend fun updateCoreUI(info: WifiInfo?, resolvedSsid: String) {
        val hasInternet = internetChecker.hasInternetAccess()
        val isWifiEnabled = signalMonitor.isWifiEnabled()

        if (!isWifiEnabled || info == null) {
            repository.updateConnectionSource(ConnectionSource.MOBILE_DATA)
        } else {
            val rssi = info.rssi
            val band = if (info.frequency > 4900) "5GHz" else "2.4GHz"
            repository.updateNetworkInfo(resolvedSsid, rssi, band)
            repository.updateConnectionSource(if (signalMonitor.isMeteredNetwork()) ConnectionSource.WIFI_HOTSPOT else ConnectionSource.WIFI_ROUTER)
        }
        repository.updateInternetStatus(if (hasInternet) "Connected" else "No Internet")
    }

    private fun performSmartOptimization() {
        // use BEST info for optimization checks too
        val info = getBestWifiInfo()
        
        // ALWAYS update the UI with the latest scan results first
        // Sort results by level descending to prioritize strong signals
        val results = actionManager.getScanResults().sortedByDescending { it.level }
        
        // Robust Normalization
        // Some devices return BSSIDs with invisible chars or different casing
        val rawCurrentBssid = info?.bssid ?: ""
        val normalizedCurrentBssid = rawCurrentBssid.replace(":", "").uppercase().trim()
        
        // Debugging Logs for Troubleshooting
        Log.d("SmartWifiService", "DEBUG: Raw Current BSSID: '$rawCurrentBssid'")
        Log.d("SmartWifiService", "DEBUG: Normalized Current: '$normalizedCurrentBssid'")
        Log.d("SmartWifiService", "DEBUG: Current SSID: '${info?.ssid}'")

        val uiList = results.map { scan ->
            val scanBssidRaw = scan.BSSID ?: ""
            val normalizedScanBssid = scanBssidRaw.replace(":", "").uppercase().trim()
            val isMatch = normalizedScanBssid.isNotEmpty() && normalizedCurrentBssid == normalizedScanBssid
            
            if (isMatch) {
                Log.d("SmartWifiService", "DEBUG: MATCH FOUND! Scan SSID: ${scan.SSID}, Scan BSSID: ${scan.BSSID}")
            }

            AvailableNetworkItem(
                ssid = scan.SSID.replace("\"", ""),
                bssid = scan.BSSID,
                level = scan.level,
                frequency = scan.frequency,
                capabilities = scan.capabilities,
                channelWidth = if (scan.frequency > 4900) 80 else 20,
                isConnected = isMatch
            )
        }
        repository.updateAvailableNetworks(uiList)

        // Then proceed with optimization checks
        val settings = repository.uiState.value
        if (!settings.isServiceRunning) {
             // Throttled logging could be better, but for debugging we want to know
             // debugger.logDecision("Optimization Skipped: Service is paused") 
             return
        }
        if (settings.isGamingMode) {
             debugger.logDecision("Optimization Skipped: Gaming Mode Active")
             return
        }

        if (info == null || info.bssid == null) {
            debugger.logDecision("Optimization Skipped: No WiFi Connection Info")
            return
        }

        debugger.logDecision("Heartbeat: Evaluating ${results.size} scan results. Current RSSI: ${info.rssi}")
        
        if (results.isEmpty()) {
             debugger.logDecision("No scan results found. Skipping optimization.")
             return
        }
        
        val currentRssi = info.rssi
        val thresholdDbm = -90 + (settings.sensitivity / 100f * 50).toInt()
        
        var bestCandidate: android.net.wifi.ScanResult? = null
        var statusMsg = "Monitoring..."
        val isPoorSignal = currentRssi < thresholdDbm
        val candidates = mutableListOf<android.net.wifi.ScanResult>()

        for (scan in results) {
            if (scan.BSSID.equals(info.bssid, ignoreCase = true)) continue
            
            val is5Ghz = scan.frequency > 4900
            val isCurrent24 = info.frequency < 4900
            
            val scanSsid = scan.SSID.replace("\"", "")
            val currentSsid = info.ssid.replace("\"", "")
            
            // Lenient Matching (Allow switch even if names are masked)
            val isSameNetwork = scanSsid.equals(currentSsid, ignoreCase = true) || 
                               scan.BSSID.take(8).equals(info.bssid.take(8), ignoreCase = true) ||
                               currentSsid == "<unknown ssid>"

            if (!isSameNetwork) continue

            // Collect better networks for OS Batch Suggestion
            if (scan.level > currentRssi || (scan.frequency > 4900 && scan.level > -80)) {
                 candidates.add(scan)
                 debugger.logDecision("Candidate added for batch: ${scan.SSID} (Level: ${scan.level})")
            }

            // 1. 5G Priority check
            if (settings.is5GhzPriorityEnabled && isCurrent24 && is5Ghz && scan.level >= settings.fiveGhzThreshold) {
                bestCandidate = scan
                statusMsg = "Better 5G Found: ${scan.SSID}"
                break 
            }

            // 2. Roaming trigger
            if (isPoorSignal && scan.level >= (currentRssi + settings.minSignalDiff)) {
                if (bestCandidate == null || scan.level > bestCandidate.level) {
                    bestCandidate = scan
                    statusMsg = "Stronger Signal Found: ${scan.SSID}"
                }
            }
        }

        if (candidates.isNotEmpty()) {
            actionManager.submitNetworkSuggestions(candidates)
        }

        if (bestCandidate != null) {
            repository.updateLastAction(statusMsg)
            debugger.logUserSuggestion(bestCandidate.SSID, statusMsg)
            Log.i("SmartWifiService", "USER PROMPT: Suggesting switch to ${bestCandidate.SSID} (${bestCandidate.BSSID}) because: $statusMsg")
            
            // IMMEDIATE USER PROMPT
            // Show notification immediately so user sees it before or while OS is deciding
            serviceScope.launch {
                 debugger.logDecision("Found better network. Prompting User for ${bestCandidate.SSID} immediately.")
                 showSwitchNotification(bestCandidate.SSID)
                 
                 // Show Floating Badge (System-Wide Overlay)
                 withContext(Dispatchers.Main) {
                     showBadge()
                 }
                 
                 // Trigger In-App Snackbar
                 repository.sendNotificationEvent("Better Network Detected: ${bestCandidate.SSID}. Tap to Switch!")

                 repository.setPendingSwitch(AvailableNetworkItem(
                    ssid = bestCandidate.SSID.replace("\"", ""),
                    bssid = bestCandidate.BSSID,
                    level = bestCandidate.level,
                    frequency = bestCandidate.frequency,
                    capabilities = bestCandidate.capabilities,
                    channelWidth = if (bestCandidate.frequency > 4900) 80 else 20,
                    isConnected = false
                ))
            }
        } else {
            val label = if (isPoorSignal) "Signal Weak ($currentRssi < $thresholdDbm). Searching..." else "Signal Optimal ($currentRssi dBm)"
            repository.updateLastAction(label)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
