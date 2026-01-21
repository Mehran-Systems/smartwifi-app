package com.smartwifi.logic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.math.abs
import kotlin.math.sqrt

import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class FastSpeedTestManager @Inject constructor() {

    data class MetricData(
        val downloadSpeed: Double? = null,
        val uploadSpeed: Double? = null,
        val idlePing: Int? = null,
        val downloadPing: Int? = null,
        val uploadPing: Int? = null,
        val downloadJitter: Int? = null,
        val uploadJitter: Int? = null,
        val jitter: Int? = null,
        val packetLoss: Double? = null,
        val clientIp: String? = null,
        val clientIsp: String? = null,
        val internalIp: String? = null,
        val serverHost: String? = null, // Kept for legacy or fallback
        val serverName: String? = null,
        val serverLocation: String? = null,
        val userLocation: String? = null,
        val latitude: String? = null,
        val longitude: String? = null
    )

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState = _testState.asStateFlow()

    private val _metricData = MutableStateFlow(MetricData())
    val metricData = _metricData.asStateFlow()

    private val token = "YXNkZmFzZGxmbnNkYWZoYXNkZmhrYWxm"
    private val apiUrl = "https://api.fast.com/netflix/speedtest/v2?https=true&token=$token&urlCount=3"

    enum class TestPhase { DOWNLOAD, UPLOAD }

    sealed class TestState {
        object Idle : TestState()
        object Preparing : TestState()
        data class Running(val speedMbps: Double, val progress: Float, val phase: TestPhase) : TestState() 
        data class Finished(val downloadSpeed: Double, val uploadSpeed: Double) : TestState()
        data class Error(val message: String) : TestState()
    }

    suspend fun startSpeedTest() {
        _testState.value = TestState.Preparing
        try {
            val targets = fetchTargets()
            if (targets.isEmpty()) {
                _testState.value = TestState.Error("No servers found")
                return
            }
            
            // 1. Download Test
            val downloadSpeed = runDownloadTest(targets)
            // Update metrics with final download before upload starts
            _metricData.value = _metricData.value.copy(downloadSpeed = downloadSpeed)
            
            // 2. Upload Test (reuse targets)
            val uploadSpeed = runUploadTest(targets)
             _metricData.value = _metricData.value.copy(uploadSpeed = uploadSpeed)
            
            _testState.value = TestState.Finished(downloadSpeed, uploadSpeed)
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            _testState.value = TestState.Idle
            throw e
        } catch (e: Exception) {
            Log.e("FastSpeedTest", "Error", e)
            _testState.value = TestState.Error(e.message ?: "Unknown Error")
        }
    }

    suspend fun fetchMetadata() = withContext(Dispatchers.IO) {
        // Run silently without changing testState to avoid UI disruption
        try {
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            try {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val start = System.currentTimeMillis()
                // Latency here might not be accurate idle ping since we just want metadata, but we can capture it
                
                val jsonObject = JSONObject(jsonStr)
                
                // Parse Client Info
                val clientObj = jsonObject.optJSONObject("client")
                val clientIp = clientObj?.optString("ip") ?: "Unknown"
                val clientIsp = clientObj?.optString("isp")
                
                val clientLocationObj = clientObj?.optJSONObject("location")
                val clientCity = clientLocationObj?.optString("city")
                val clientCountry = clientLocationObj?.optString("country")
                
                val currentMetrics = _metricData.value
                // Update Client IP & Location if not already set or override
                _metricData.value = currentMetrics.copy(
                    clientIp = clientIp,
                    clientIsp = clientIsp,
                    internalIp = getInternalIpAddress(),
                    userLocation = if (!clientCity.isNullOrEmpty()) "$clientCity, $clientCountry" else "Unknown",
                    latitude = clientObj?.optString("latitude"),
                    longitude = clientObj?.optString("longitude")
                )
                
                val targetsArray = jsonObject.getJSONArray("targets")
                val serverLocations = mutableListOf<String>()
                
                val maxServers = minOf(3, targetsArray.length())
                for (i in 0 until maxServers) {
                     val targetObj = targetsArray.getJSONObject(i)
                     val locationObj = targetObj.optJSONObject("location")
                     
                     val city = locationObj?.optString("city")
                     val country = locationObj?.optString("country")
                     
                     if (!city.isNullOrEmpty() && !country.isNullOrEmpty()) {
                         val truncatedCity = if (city!!.length > 11) city.take(11) else city
                         serverLocations.add("$truncatedCity, $country")
                     }
                }
                
                val finalServerLocation = if (serverLocations.isNotEmpty()) {
                    serverLocations.joinToString("\n")
                } else {
                    null
                }
                
                _metricData.value = _metricData.value.copy(
                    serverName = finalServerLocation ?: "Unknown Server", 
                    serverLocation = finalServerLocation
                )
                
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e("FastSpeedTest", "Metadata fetch failed", e)
        }
    }

    private suspend fun fetchTargets(): List<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        try {
            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            val latency = (System.currentTimeMillis() - start).toInt() 
            
            // Process metadata again to ensure fresh latency and targets
            val jsonObject = JSONObject(jsonStr)
            
            // ... (Reuse logic or call fetchMetadata? Better to parse here to get fresh targets list)
            // Ideally we'd reuse fetchMetadata BUT we need the list of targets explicitly 
            // and we need to update idlePing which fetchMetadata doesn't strictly guarantee as 'fresh'.
            // For now, let's duplicate the parsing logic slightly or share a private parse method. 
            // Given the constraint, I will execute the full logic here but also update metadata.
            
             // Parse Client Info
            val clientObj = jsonObject.optJSONObject("client")
            val clientIp = clientObj?.optString("ip") ?: "Unknown"
            val clientIsp = clientObj?.optString("isp")
            
            val clientLocationObj = clientObj?.optJSONObject("location")
            val clientCity = clientLocationObj?.optString("city")
            val clientCountry = clientLocationObj?.optString("country")
            
            _metricData.value = _metricData.value.copy(
                idlePing = latency,
                clientIp = clientIp,
                clientIsp = clientIsp,
                internalIp = getInternalIpAddress(),
                userLocation = if (!clientCity.isNullOrEmpty()) "$clientCity, $clientCountry" else "Unknown",
                latitude = clientObj?.optString("latitude"),
                longitude = clientObj?.optString("longitude")
            )
            
            val targetsArray = jsonObject.getJSONArray("targets")
            val list = mutableListOf<String>()
            val serverLocations = mutableListOf<String>()
            
            val maxServers = minOf(3, targetsArray.length())
            for (i in 0 until maxServers) {
                 val targetObj = targetsArray.getJSONObject(i)
                 val locationObj = targetObj.optJSONObject("location")
                 
                 val city = locationObj?.optString("city")
                 val country = locationObj?.optString("country")
                 
                 if (!city.isNullOrEmpty() && !country.isNullOrEmpty()) {
                     val truncatedCity = if (city!!.length > 11) city.take(11) else city
                     serverLocations.add("$truncatedCity, $country")
                 }
            }
            
            val finalServerLocation = if (serverLocations.isNotEmpty()) {
                serverLocations.joinToString("\n")
            } else {
                null
            }
            
             _metricData.value = _metricData.value.copy(
                serverName = finalServerLocation ?: "Unknown Server", 
                serverLocation = finalServerLocation 
            )
            
            for (i in 0 until targetsArray.length()) {
                val u = targetsArray.getJSONObject(i).getString("url")
                list.add(u)
            }
            
            if (list.isNotEmpty()) {
                measureJitterAndLoss(list)
            }
            
            list
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun runDownloadTest(urls: List<String>): Double = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 10_000 // 10s test

        // Launch Loaded Latency Monitor
        val monitorJob = launch {
             monitorLoadedLatency(urls.first(), TestPhase.DOWNLOAD, endTime)
        }
        
        var totalBytes = 0L
        val updateInterval = 200L
        var lastUpdate = startTime
        var urlIndex = 0
        
        val speedSamples = mutableListOf<Double>()
        
        try {
            while (System.currentTimeMillis() < endTime && _testState.value !is TestState.Error) {
                val targetUrl = urls[urlIndex % urls.size]
                try {
                    val url = URL(targetUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    
                    val stream = conn.inputStream
                    val buffer = ByteArray(65536)
                    
                    var loopBytes = 0L
                    
                    while (System.currentTimeMillis() < endTime && _testState.value !is TestState.Error) {
                        val read = stream.read(buffer)
                        if (read == -1) break 
                        
                        totalBytes += read
                        loopBytes += read
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > updateInterval) {
                            val durationSeconds = (now - lastUpdate) / 1000.0
                            if (durationSeconds > 0) {
                                val bits = loopBytes * 8.0
                                val instantSpeed = (bits / durationSeconds) / 1_000_000.0
                                
                                // Only record samples that are valid (non-zero)
                                if (instantSpeed > 0) speedSamples.add(instantSpeed)
                                
                                val progress = (now - startTime) / 10_000f
                                
                                // Show instant speed to user (or maybe an EMA smoothed version)
                                _testState.value = TestState.Running(instantSpeed, progress.coerceIn(0f, 1f), TestPhase.DOWNLOAD)
                                
                                lastUpdate = now
                                loopBytes = 0L // Reset loop bytes for next instant interval
                            }
                        }
                    }
                    stream.close()
                    conn.disconnect()
                } catch (e: Exception) { 
                    Log.w("FastSpeedTest", "DL fail $targetUrl", e)
                }
                urlIndex++
            }
        } catch (e: Exception) {
             throw e
        } finally {
            monitorJob.cancel()
        }
        
        // Calculate Final Speed: Average of top 70% samples to exclude slow start/end
        if (speedSamples.isNotEmpty()) {
            speedSamples.sortedDescending().take((speedSamples.size * 0.7).toInt().coerceAtLeast(1)).average()
        } else {
            0.0
        }
    }

    private suspend fun runUploadTest(urls: List<String>): Double = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 10_000 // 10s test

        // Launch Loaded Latency Monitor
        val monitorJob = launch {
             monitorLoadedLatency(urls.first(), TestPhase.UPLOAD, endTime)
        }
        
        var totalBytes = 0L
        val updateInterval = 200L
        var lastUpdate = startTime
        var urlIndex = 0
        
        // Random buffer to upload
        val buffer = ByteArray(65536)
        val speedSamples = mutableListOf<Double>()
        
        try {
            while (System.currentTimeMillis() < endTime && _testState.value !is TestState.Error) {
                val targetUrl = urls[urlIndex % urls.size]
                try {
                    val url = URL(targetUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.doOutput = true
                    conn.requestMethod = "POST"
                    conn.setChunkedStreamingMode(65536)
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    
                    val stream = conn.outputStream
                    var loopBytes = 0L
                    
                    while (System.currentTimeMillis() < endTime && _testState.value !is TestState.Error) {
                        stream.write(buffer)
                        val written = buffer.size
                        totalBytes += written
                        loopBytes += written
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > updateInterval) {
                            val durationSeconds = (now - lastUpdate) / 1000.0
                            if (durationSeconds > 0) {
                                val bits = loopBytes * 8.0
                                val instantSpeed = (bits / durationSeconds) / 1_000_000.0
                                
                                if (instantSpeed > 0) speedSamples.add(instantSpeed)
                                
                                val progress = (now - startTime) / 10_000f
                                _testState.value = TestState.Running(instantSpeed, progress.coerceIn(0f, 1f), TestPhase.UPLOAD)
                                lastUpdate = now
                                loopBytes = 0L
                            }
                        }
                    }
                    stream.close()
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FastSpeedTest", "UL fail $targetUrl", e)
                }
                urlIndex++
            }
        } catch (e: Exception) {
             throw e
        } finally {
            monitorJob.cancel()
        }
        
        if (speedSamples.isNotEmpty()) {
            speedSamples.sortedDescending().take((speedSamples.size * 0.7).toInt().coerceAtLeast(1)).average()
        } else {
            0.0
        }
    }
    
    private fun getInternalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address) {
                        return addr.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unknown"
    }

    private suspend fun measureJitterAndLoss(targets: List<String>) {
        // Run 5 quick pings (Optimized for speed)
        val pingTimes = mutableListOf<Long>()
        var failed = 0
        val totalTests = 5
        val targetUrl = targets.firstOrNull() ?: return
        
        withContext(Dispatchers.IO) {
            for (i in 1..totalTests) {
                val start = System.currentTimeMillis()
                try {
                    val url = URL(targetUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 500
                    conn.readTimeout = 500
                    conn.connect()
                    conn.responseCode // Trigger request
                    val end = System.currentTimeMillis()
                    pingTimes.add(end - start)
                    conn.disconnect()
                } catch (e: Exception) {
                    failed++
                }
            }
        }
        
        val packetLoss = (failed.toDouble() / totalTests.toDouble()) * 100.0
        
        val avgPing = if (pingTimes.isNotEmpty()) pingTimes.average() else 0.0
        val jitter = calculateJitter(pingTimes)

        _metricData.value = _metricData.value.copy(
            packetLoss = packetLoss,
            jitter = jitter,
            // Update Idle Ping with accurate average from burst
            idlePing = avgPing.toInt()
        )
    }

    private suspend fun monitorLoadedLatency(urlStr: String, phase: TestPhase, endTime: Long) = withContext(Dispatchers.IO) {
        val pingTimes = mutableListOf<Long>()
        while (System.currentTimeMillis() < endTime && _testState.value is TestState.Running) {
            val start = System.currentTimeMillis()
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                conn.connect()
                conn.responseCode
                val end = System.currentTimeMillis()
                val rtt = end - start
                pingTimes.add(rtt)
                
                // Calculate metrics
                val avgPing = pingTimes.average().toInt()
                val jitter = calculateJitter(pingTimes)
                
                _metricData.value = when(phase) {
                    TestPhase.DOWNLOAD -> _metricData.value.copy(downloadPing = avgPing, downloadJitter = jitter)
                    TestPhase.UPLOAD -> _metricData.value.copy(uploadPing = avgPing, uploadJitter = jitter)
                }
                
                conn.disconnect()
            } catch (e: Exception) {
                // Ignore failures during load
            }
            // Wait a bit between pings
            kotlinx.coroutines.delay(500)
        }
    }

    private fun calculateJitter(times: List<Long>): Int {
        if (times.size < 2) return 0
        var sumDiff = 0.0
        for (i in 0 until times.size - 1) {
            sumDiff += abs(times[i] - times[i+1])
        }
        return (sumDiff / (times.size - 1)).toInt()
    }

    fun reset() {
        _testState.value = TestState.Idle
        _metricData.value = MetricData() 
    }
}
