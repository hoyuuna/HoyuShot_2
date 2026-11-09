package app.hoyu.shot.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.hoyu.shot.model.HorizonOrientation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

class HorizonSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val _orientationFlow = MutableStateFlow(HorizonOrientation())
    val orientationFlow: StateFlow<HorizonOrientation> = _orientationFlow.asStateFlow()

    private val rotationMatrix = FloatArray(9)

    // Current UI rotation degrees (0, 90, 180, 270)
    var currentUiRotationDegrees: Float = 0f

    fun start() {
        rotationSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        var gx = 0f
        var gy = 0f
        var gz = 0f

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            // In Android coordinate system, world gravity down [0, 0, -1] mapped to device coords:
            // gx = -R[2], gy = -R[5], gz = -R[8]
            gx = -rotationMatrix[2]
            gy = -rotationMatrix[5]
            gz = -rotationMatrix[8]
        } else if (event.sensor.type == Sensor.TYPE_GRAVITY || event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            gx = event.values[0]
            gy = event.values[1]
            gz = event.values[2]
        }

        val norm = sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()
        if (norm <= 0.001f) return

        val normGx = gx / norm
        val normGy = gy / norm
        val normGz = gz / norm

        // 1. Eye-Level Pitch (Thẳng tầm mắt):
        // When holding the phone upright facing forward at eye level, gz is 0.
        // Tilted up to sky -> gz > 0, tilted down to ground -> gz < 0.
        val pitchRad = asin(normGz.coerceIn(-1f, 1f).toDouble())
        val pitchDegrees = Math.toDegrees(pitchRad).toFloat()

        // 2. Relative Roll adapting to phone orientation (Portrait vs Landscape):
        // Screen orientation angle theta in degrees (-180 to 180)
        val theta = Math.toDegrees(atan2(normGx.toDouble(), (-normGy).toDouble())).toFloat()
        var rollDiff = (theta - currentUiRotationDegrees) % 360f
        if (rollDiff > 180f) rollDiff -= 360f
        if (rollDiff < -180f) rollDiff += 360f

        val isRollLevel = abs(rollDiff) <= 1.0f
        val isPitchLevel = abs(pitchDegrees) <= 1.5f
        val isLevel = isRollLevel && isPitchLevel

        _orientationFlow.value = HorizonOrientation(
            pitchDegrees = pitchDegrees,
            rollDegrees = rollDiff,
            isRollLevel = isRollLevel,
            isPitchLevel = isPitchLevel,
            isLevel = isLevel
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
