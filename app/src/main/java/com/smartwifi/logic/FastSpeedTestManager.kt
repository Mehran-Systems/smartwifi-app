package com.smartwifi.logic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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
        val clientIp: String? = null,
        val serverHost: String? = null
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
            
        } catch (e: Exception) {
            Log.e("FastSpeedTest", "Error", e)
            _testState.value = TestState.Error(e.message ?: "Unknown Error")
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
            
            val jsonObject = JSONObject(jsonStr)
            
            // Parse Client Info
            val clientIp = jsonObject.optJSONObject("client")?.optString("ip") ?: "Unknown"
            
            val targetsArray = jsonObject.getJSONArray("targets")
            val list = mutableListOf<String>()
            var firstHost: String? = null
            
            for (i in 0 until targetsArray.length()) {
                val u = targetsArray.getJSONObject(i).getString("url")
                list.add(u)
                if (firstHost == null) {
                    val host = URL(u).host
                    val match = Regex("-([a-z]{3}[0-9]{3})-").find(host)
                    firstHost = match?.groupValues?.get(1)?.uppercase() ?: host
                }
            }
            
            // Update Metadata
            _metricData.value = _metricData.value.copy(
                idlePing = latency,
                clientIp = clientIp,
                serverHost = firstHost
            )
            
            list
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun runDownloadTest(urls: List<String>): Double = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 10_000 // 10s test
        
        var totalBytes = 0L
        val updateInterval = 200L
        var lastUpdate = startTime
        var urlIndex = 0
        var currentSpeed = 0.0
        
        try {
            while (System.currentTimeMillis() < endTime) {
                val targetUrl = urls[urlIndex % urls.size]
                try {
                    val url = URL(targetUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    
                    val stream = conn.inputStream
                    val buffer = ByteArray(65536)
                    
                    while (System.currentTimeMillis() < endTime) {
                        val read = stream.read(buffer)
                        if (read == -1) break 
                        
                        totalBytes += read
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > updateInterval) {
                            val durationSeconds = (now - startTime) / 1000.0
                            if (durationSeconds > 0) {
                                val bits = totalBytes * 8.0
                                currentSpeed = (bits / durationSeconds) / 1_000_000.0
                                val progress = (now - startTime) / 10_000f
                                _testState.value = TestState.Running(currentSpeed, progress.coerceIn(0f, 1f), TestPhase.DOWNLOAD)
                                lastUpdate = now
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
        }
        
        val totalDuration = (System.currentTimeMillis() - startTime) / 1000.0
        if (totalDuration > 0) ((totalBytes * 8.0) / totalDuration) / 1_000_000.0 else 0.0
    }

    private suspend fun runUploadTest(urls: List<String>): Double = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 10_000 // 10s test
        
        var totalBytes = 0L
        val updateInterval = 200L
        var lastUpdate = startTime
        var urlIndex = 0
        var currentSpeed = 0.0
        
        // Random buffer to upload
        val buffer = ByteArray(65536)
        
        try {
            while (System.currentTimeMillis() < endTime) {
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
                    
                    while (System.currentTimeMillis() < endTime) {
                        stream.write(buffer)
                        totalBytes += buffer.size
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > updateInterval) {
                            val durationSeconds = (now - startTime) / 1000.0
                            if (durationSeconds > 0) {
                                val bits = totalBytes * 8.0
                                currentSpeed = (bits / durationSeconds) / 1_000_000.0
                                val progress = (now - startTime) / 10_000f
                                _testState.value = TestState.Running(currentSpeed, progress.coerceIn(0f, 1f), TestPhase.UPLOAD)
                                lastUpdate = now
                            }
                        }
                    }
                    stream.close()
                    // Read response? usually ignore for speed test, just need to push bits
                    // conn.inputStream.close() 
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w("FastSpeedTest", "UL fail $targetUrl", e)
                }
                urlIndex++
            }
        } catch (e: Exception) {
             throw e
        }
        
        val totalDuration = (System.currentTimeMillis() - startTime) / 1000.0
        if (totalDuration > 0) ((totalBytes * 8.0) / totalDuration) / 1_000_000.0 else 0.0
    }
    
    fun reset() {
        _testState.value = TestState.Idle
        _metricData.value = MetricData() 
    }
}
