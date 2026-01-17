
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
    // Use a dedicated thread for the service to avoid thread pool suspension issues during screen off
    private val serviceScope = CoroutineScope(newSingleThreadContext("SmartWifiServiceThread") + serviceJob)
    
    private var currentWifiInfo: WifiInfo? = null
    private val wifiManager by lazy { getSystemService(Context.WIFI_SERVICE) as WifiManager }
    
    private val wakeLock: android.os.PowerManager.WakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "SmartWifi::ServiceWakeLock"
        ).apply { setReferenceCounted(false) }
    }

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

    // AudioTrack for "Silent Music" trick to prevent Doze
    private var silentAudioTrack: android.media.AudioTrack? = null
    
    private var lastNotificationTime: Long = 0

    override fun onCreate() {
        super.onCreate()
        
        // Create notification channels and start foreground immediately
        createNotificationChannel()
        startForeground(1, createNotification())

        userContextMonitor = UserContextMonitor(this)
        mobileMonitor = MobileNetworkMonitor(this)
        
        // Initialize AudioTrack for silence
        startSilentAudio()
        
        // Acquire WakeLock to keep CPU running during screen off
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire()
                Log.d("SmartWifiService", "Partial WakeLock Acquired")
            }
        } catch (e: Exception) {
            Log.e("SmartWifiService", "Failed to acquire WakeLock", e)
        }
        
        Log.d("SmartWifiService", "Service Created. Scheduling restart watchdog...")
        
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
                    showBadge(isWarning = false) 
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
        
        // AUTO-START LOGIC: Ensure optimization is active
        repository.updateServiceStatus(true)
        startMonitoring()
    }

    private fun startSilentAudio() {
        try {
            val sampleRate = 44100
            val buffSize = android.media.AudioTrack.getMinBufferSize(
                sampleRate, 
                android.media.AudioFormat.CHANNEL_OUT_MONO, 
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )
            
            silentAudioTrack = android.media.AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffSize)
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .build()

            val silence = ByteArray(buffSize)
            silentAudioTrack?.write(silence, 0, silence.size)
            silentAudioTrack?.setLoopPoints(0, buffSize / 2, -1)
            silentAudioTrack?.play()
            Log.d("SmartWifiService", "Silent AudioTrack started for persistence.")
        } catch (e: Exception) {
            Log.e("SmartWifiService", "Failed to start Silent Audio", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "KEEP_ALIVE") {
            // Just a dummy wake-up call to ensure loop runs
            Log.d("SmartWifiService", "Keep-Alive Alarm Triggered")
        } else {
            Log.d("SmartWifiService", "onStartCommand Executed. Mode: START_STICKY")
            
            // Ensure notification is up immediately if system killed and restarted us
            startForeground(1, createNotification())
            
            // If the service was killed/restarted, ensure we resume monitoring
            if (!serviceScope.isActive) {
                 Log.w("SmartWifiService", "Service restarted by system.")
            }
        }
        
        return START_STICKY
    }

    private fun scheduleHeartbeat() {
        val intent = Intent(this, SmartWifiService::class.java).apply { action = "KEEP_ALIVE" }
        val pendingIntent = android.app.PendingIntent.getService(
            this, 999, intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        
        // Schedule for 5 seconds in future to forcefully wake up Doze
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                 if (alarmManager.canScheduleExactAlarms()) {
                     alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 5000,
                        pendingIntent
                    )
                 } else {
                     Log.w("SmartWifiService", "Permission 'SCHEDULE_EXACT_ALARM' denied. Falling back to Inexact Alarm.")
                     alarmManager.setAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 5000,
                        pendingIntent
                    )
                 }
            } else {
                 alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 5000,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e("SmartWifiService", "SecurityException: Failed to schedule alarm. Permission missing.", e)
        } catch (e: Exception) {
            Log.e("SmartWifiService", "Failed to schedule heartbeat alarm", e)
        }
    }
        
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w("SmartWifiService", "onTaskRemoved: Application removed from Recents. Scheduling Restart...")

        val restartServiceIntent = Intent(applicationContext, SmartWifiService::class.java).also {
            it.setPackage(packageName)
        }
        
        val restartServicePendingIntent = android.app.PendingIntent.getService(
            this, 1, restartServiceIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmService.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
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
                .map { StateTuple(it.sensitivity, it.fiveGhzThreshold, it.minSignalDiff, it.badgeSensitivity) }
                .distinctUntilChanged()
                .collect { (sens, fiveG, roam, badgeSens) ->
                    val badgeThresholdDbm = -90 + (badgeSens / 100f * 50).toInt()
                    val sensitivityDbm = -90 + (sens / 100f * 50).toInt()
                    
                    debugger.logSettingsChange("Sensitivity: $sens ($sensitivityDbm dBm), BadgeSens: $badgeSens ($badgeThresholdDbm dBm), 5GHz Threshold: $fiveG, Roaming Diff: $roam")
                    Log.i("SmartWifiService", "Settings Applied: SwitchThreshold=$sensitivityDbm dBm (Slider: $sens), BadgeThreshold=$badgeThresholdDbm dBm (Slider: $badgeSens), 5G_Min=$fiveG, RoamTrigger=$roam")
                }
        }
    }
    
    // Helper data class for observing multiple state fields
    private data class StateTuple(val sens: Int, val fiveG: Int, val roam: Int, val badgeSens: Int)

    // --- Floating Badge Logic ---
    private var badgeView: android.view.View? = null
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager }

    private fun showBadge(isWarning: Boolean = false) {
        val canDraw = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) 
            android.provider.Settings.canDrawOverlays(this) 
        else true

        Log.d("SmartWifiService", "showBadge() called. isWarning=$isWarning, canDrawOverlays=$canDraw, alreadyShowing=${badgeView != null}")

        if (!canDraw) {
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
                // Use our new WiFi Vector Icon
                setImageResource(com.smartwifi.R.drawable.ic_badge_wifi)
                
                // Using a color filter to make it look "Active"
                val color = if (isWarning) android.graphics.Color.parseColor("#FF9800") else android.graphics.Color.parseColor("#4CAF50")
                setColorFilter(color) 
                setBackgroundColor(android.graphics.Color.parseColor("#CCFFFFFF")) // Semi-transparent white bg
                setPadding(20, 20, 20, 20)
                
                // Circular Shape
                background = android.graphics.drawable.GradientDrawable().apply {
                     shape = android.graphics.drawable.GradientDrawable.OVAL
                     setColor(android.graphics.Color.parseColor("#E0FFFFFF"))
                     setStroke(2, color)
                }
                
                // USER REQUEST: Increase Badge Size
                setPadding(35, 35, 35, 35) // Increased from 20 to 35 for larger touch area
                
                setOnClickListener {
                     // Launch Panel Intent
                     try {
                         // USER REQUEST: Open Internet Panel DIRECTLY (Skip App Main Activity)
                         val intent = Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        if (intent.resolveActivity(packageManager) == null) {
                            intent.action = android.provider.Settings.ACTION_WIFI_SETTINGS
                        }
                        
                        val pendingIntent = android.app.PendingIntent.getActivity(
                            context, 0, intent, 
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        
                        val options = if (android.os.Build.VERSION.SDK_INT >= 34) { 
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
                         android.widget.Toast.makeText(context, "Tap Notification to switch", android.widget.Toast.LENGTH_SHORT).show()
                     }
                }
            }
            
            badgeView = icon
            windowManager.addView(badgeView, params)
            
            // SLIDE IN ANIMATION
            icon.translationX = 300f // Start off-screen (right)
            icon.animate()
                .translationX(0f)
                .setDuration(400)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            
            // Auto-hide after 15 seconds (with slide out)
            serviceScope.launch {
                delay(15000)
                withContext(Dispatchers.Main) { hideBadge() }
            }
            
        } catch (e: Exception) {
            Log.e("SmartWifiService", "Failed to show badge", e)
        }
    }

    private fun hideBadge() {
        try {
            val view = badgeView
            if (view != null) {
                // SLIDE OUT ANIMATION
                view.animate()
                    .translationX(300f) // Slide out to right
                    .setDuration(300)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        try {
                            windowManager.removeView(view)
                        } catch(e: Exception) {}
                    }
                    .start()
                    
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

                val loopDelay = 3000L // Fast 3-second heartbeat for continuous scanning
                
                // Aggressive Monitoring: Check often to beat the OS switcher
                // Did not use % 3 check anymore to scan continuously.
                
                     // Log details every loop (3 seconds)
                     val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                     val isWhitelisted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) 
                        pm.isIgnoringBatteryOptimizations(packageName) 
                     else true
                     
                     if (info != null) {
                        val scanResults = actionManager.getScanResults()
                        val topNetworks = scanResults.take(3).joinToString { "${it.SSID}(${it.level})" }
                        val log1 = "Current: SSID='${info.ssid}', RSSI=${info.rssi}dBm. (WakeLock: ${wakeLock.isHeld})"
                        val log2 = "Visible: Found ${scanResults.size}. Top: $topNetworks"
                        
                        Log.i("SmartWifiService", log1)
                        Log.i("SmartWifiService", log2)
                        debugger.logDecision(log1)
                        debugger.logDecision(log2)
                     } else {
                        val scanResults = actionManager.getScanResults()
                        val topNetworks = scanResults.take(3).joinToString { "${it.SSID}(${it.level})" }
                        val log1 = "Current: Disconnected. (WakeLock: ${wakeLock.isHeld})"
                        val log2 = "Visible: Found ${scanResults.size}. Top: $topNetworks"
                        
                        Log.i("SmartWifiService", log1)
                        Log.i("SmartWifiService", log2)
                        debugger.logDecision(log1)
                        debugger.logDecision(log2)
                     }

                     if (signalMonitor.isWifiEnabled()) {
                        val accepted = actionManager.startScan()
                        debugger.logDecision("Heartbeat: Requesting Scan... Accepted=$accepted")
                        if (!accepted) {
                            Log.w("SmartWifiService", "WARNING: Scan Throttled by OS. (Background: ${!isWhitelisted})")
                        }
                     }

                lastRx = currentRx
                lastTx = currentTx
                loopCount++
                
                // Nuclear Option: Schedule an alarm to wake us up in 5s if we die
                scheduleHeartbeat()
                
                try {
                    Thread.sleep(loopDelay) 
                } catch (e: InterruptedException) {
                    Log.d("SmartWifiService", "Loop sleep interrupted")
                }
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
             debugger.logDecision("Optimization Skipped: Service is paused") 
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
        val badgeThresholdDbm = -90 + (settings.badgeSensitivity / 100f * 50).toInt()
        
        var bestCandidate: android.net.wifi.ScanResult? = null
        var statusMsg = "Monitoring..."
        // Restore variable definitions used in else block
        val isPoorSignal = currentRssi < thresholdDbm
        val isBadgeWarning = currentRssi < badgeThresholdDbm
        val candidates = mutableListOf<android.net.wifi.ScanResult>()

        for (scan in results) {
            if (scan.BSSID.equals(info.bssid, ignoreCase = true)) continue
            
            val is5Ghz = scan.frequency > 4900
            val isCurrent24 = info.frequency < 4900
            val isCurrent5G = info.frequency > 4900
            
            // 5GHz Strategy: Prefer 5GHz unless it's very weak
            var requiredDiff = settings.minSignalDiff
            
            if (isCurrent5G && !is5Ghz) {
                // Downgrade Penalty: Harder to switch from 5G -> 2.4G
                requiredDiff += 15 
            } else if (isCurrent24 && is5Ghz) {
                // Upgrade Bonus: Always prefer 5G if it's usable
                // USER FIX: Aggressively suggest 5G. Even if it's 5dB WEAKER than current 2.4G, it's likely better.
                requiredDiff = -5 
            }

            // Evaluation
            if (scan.level >= (currentRssi + requiredDiff)) {
                 // Check if this is the "Best" so far in this loop
                 if (bestCandidate == null || scan.level > bestCandidate.level) {
                     bestCandidate = scan
                     statusMsg = if (is5Ghz) "Better 5G Found: ${scan.SSID}" else "Stronger Signal Found: ${scan.SSID}"
                 }
            }
            
            // Collect better networks for OS Batch Suggestion
            if (scan.level > currentRssi || (is5Ghz && scan.level > -80)) {
                 candidates.add(scan)
            }
        }

        if (candidates.isNotEmpty()) {
            actionManager.submitNetworkSuggestions(candidates)
        }

        if (bestCandidate != null) {
            repository.updateLastAction(statusMsg)
            debugger.logUserSuggestion(bestCandidate.SSID, statusMsg)
            Log.i("SmartWifiService", "USER PROMPT: Suggesting switch to ${bestCandidate.SSID} (${bestCandidate.BSSID}) because: $statusMsg")
            
            // Show Floating Badge (System-Wide Overlay) - ALWAYS show/update visual
            serviceScope.launch(Dispatchers.Main) {
                 showBadge(isWarning = false)
            }

            // Notification Throttling (Avoid Noise Spam)
            val now = System.currentTimeMillis()
            if (now - lastNotificationTime > 15000) { // Max 1 beep every 15s
                lastNotificationTime = now
                serviceScope.launch {
                     showSwitchNotification(bestCandidate.SSID)
                }
            } else {
                debugger.logDecision("Notification sound throttled (too frequent)")
            }
                 
                 // Trigger In-App Snackbar
                 serviceScope.launch {
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
            
            // If signal is poor (based on badge threshold), show the badge as a warning
            
            Log.d("SmartWifiService", "Badge Logic Check: Current RSSI=$currentRssi, Badge Threshold=$badgeThresholdDbm (Sens: ${settings.badgeSensitivity}), isWarning=$isBadgeWarning")
            
            if (isBadgeWarning) {
                 serviceScope.launch(Dispatchers.Main) {
                     // Check if not already showing to avoid spamming logic (though showBadge handles null check)
                     Log.i("SmartWifiService", "Signal Degraded for Badge ($currentRssi < $badgeThresholdDbm). Triggering showBadge(true).")
                     showBadge(isWarning = true)
                 }
            } else {
                 // Optional: Hide badge if signal improves (auto-hide logic is currently timeout-based, but we could add explicit hide here)
                 // hideBadge() 
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        
        try {
            silentAudioTrack?.stop()
            silentAudioTrack?.release()
        } catch (e: Exception) {
            Log.e("SmartWifiService", "Failed to release AudioTrack", e)
        }

        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d("SmartWifiService", "Partial WakeLock Released")
            }
        } catch (e: Exception) {
            Log.e("SmartWifiService", "Failed to release WakeLock", e)
        }
    }
}
