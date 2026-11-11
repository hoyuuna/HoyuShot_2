package app.hoyu.shot.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Computes True Geographic Heading (clockwise from True North, 0..360) of the active camera lens axis
 * using the fused 3D rotation vector + GeomagneticField magnetic declination.
 */
class SunCompassManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    private val _headingDegrees = MutableStateFlow(0f)
    val headingDegrees: StateFlow<Float> = _headingDegrees.asStateFlow()

    @Volatile var isFrontCamera: Boolean = false
    @Volatile var currentUiRotationDegrees: Float = 0f

    private var declinationDeg: Float = 0f
    private var hasSmoothHeading = false
    private var smoothedHeading = 0f
    private val rotMatrix = FloatArray(9)
    private var isListening = false

    fun updateLocation(location: Location?) {
        if (location == null) return
        declinationDeg = try {
            GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                location.altitude.toFloat(),
                System.currentTimeMillis()
            ).declination
        } catch (_: Exception) {
            0f
        }
    }

    fun start() {
        if (isListening) return
        rotationSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            isListening = true
        }
    }

    fun stop() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
        hasSmoothHeading = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)

        // Device optical axis in world East/North/Up:
        // Rear camera points along device -Z -> (-R[2], -R[5]); Front camera along +Z -> (R[2], R[5]).
        val sign = if (isFrontCamera) 1f else -1f
        var east = sign * rotMatrix[2]
        var north = sign * rotMatrix[5]

        // If camera is tilted almost straight up/down (> ~75° pitch), project screen top vector instead
        if (hypot(east, north) < 0.25f) {
            val normRot = (((currentUiRotationDegrees.toInt() % 360) + 360) % 360)
            when (normRot) {
                90 -> {
                    east = -rotMatrix[0]
                    north = -rotMatrix[3]
                }
                180 -> {
                    east = -rotMatrix[1]
                    north = -rotMatrix[4]
                }
                270 -> {
                    east = rotMatrix[0]
                    north = rotMatrix[3]
                }
                else -> {
                    east = rotMatrix[1]
                    north = rotMatrix[4]
                }
            }
        }

        val magHeading = Math.toDegrees(atan2(east.toDouble(), north.toDouble())).toFloat()
        val trueHeading = (((magHeading + declinationDeg) % 360f) + 360f) % 360f

        if (!hasSmoothHeading) {
            smoothedHeading = trueHeading
            hasSmoothHeading = true
        } else {
            val delta = ((trueHeading - smoothedHeading + 540f) % 360f) - 180f
            smoothedHeading = (((smoothedHeading + delta * 0.22f) % 360f) + 360f) % 360f
        }
        _headingDegrees.value = smoothedHeading
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
