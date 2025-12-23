package com.smartwifi.logic

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.sqrt

@Singleton
class SignalDirectionSensor @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // State
    private val _azimuth = MutableStateFlow(0f) // Compass heading (0-360)
    val azimuth = _azimuth.asStateFlow()

    private val _movementState = MutableStateFlow(MovementState.STATIONARY)
    val movementState = _movementState.asStateFlow()

    // Sensor Data
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null
    
    // Heuristic State
    private var lastRssi = -100
    private var lastRssiTime = 0L
    private var lastHeadingAtRssiCheck = 0f
    
    // Target "Ghost" Direction (where we think the signal is)
    // 0f = North, null = Unknown
    private val _targetBearing = MutableStateFlow<Float?>(null)
    val targetBearing = _targetBearing.asStateFlow()

    enum class MovementState {
        STATIONARY, MOVING_FORWARD, MOVING_BACKWARD // Simplified for heuristic
    }

    fun startListening() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    fun onRssiUpdate(newRssi: Int) {
        val now = System.currentTimeMillis()
        if (now - lastRssiTime < 2000) return // Too fast logic check
        
        // Simple Heuristic:
        // If RSSI improved significantly (> 3dB) since last check, 
        // AND we are currently roughly facing the same way we were? 
        // Then Target is roughly CURRENT Heading.
        
        val diff = newRssi - lastRssi
        val currentHeading = _azimuth.value
        
        if (diff > 3) {
            // Signal got stronger! We are walking towards it (assumed).
            // Set target to current heading.
            _targetBearing.value = currentHeading
            Log.d("SignalSensor", "Generic: Hotter! Target set to $currentHeading")
        } else if (diff < -3) {
            // Signal got weaker! We are walking away.
            // Set target to behind us.
            _targetBearing.value = (currentHeading + 180) % 360
            Log.d("SignalSensor", "Generic: Colder! Target set behind to ${_targetBearing.value}")
        }
        // If diff is small, keep old target (hysteresis)
        
        lastRssi = newRssi
        lastRssiTime = now
        lastHeadingAtRssiCheck = currentHeading
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values
            
            // Detect movement (simple jitter check, can be improved)
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            val accel = sqrt(ax*ax + ay*ay + az*az)
            // Normal gravity is ~9.8. If deviates significantly, user is moving/walking.
            if (accel > 11 || accel < 9) {
                _movementState.value = MovementState.MOVING_FORWARD // Assume forward for now
            } else {
                 _movementState.value = MovementState.STATIONARY
            }
        }
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values
        }
        
        if (gravity != null && geomagnetic != null) {
            val R = FloatArray(9)
            val I = FloatArray(9)
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)
                // orientation[0] is azimuth in radians
                var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (az < 0) az += 360f
                _azimuth.value = az
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
}
