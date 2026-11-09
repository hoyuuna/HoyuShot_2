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

    // Current UI rotation degrees: 0 (portrait), 90 (landscape right), 180 (inverted), 270 (landscape left)
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

        // 2. Relative Roll adapting to user's current holding orientation:
        // For each of the 4 cardinal phone orientations (0, 90, 180, 270):
        // Compute the roll angle relative to the horizon in user coordinates.
        val rollDegrees = when (currentUiRotationDegrees) {
            90f -> {
                // Landscape right (charging port on the right, top points left):
                // User down is -gx, user right is -gy.
                Math.toDegrees(atan2(-normGy.toDouble(), -normGx.toDouble())).toFloat()
            }
            180f -> {
                // Portrait inverted:
                // User down is +gy, user right is -gx.
                Math.toDegrees(atan2(-normGx.toDouble(), normGy.toDouble())).toFloat()
            }
            270f -> {
                // Landscape left (charging port on the left, top points right):
                // User down is +gx, user right is +gy.
                Math.toDegrees(atan2(normGy.toDouble(), normGx.toDouble())).toFloat()
            }
            else -> {
                // Portrait 0° (normal):
                // User down is -gy, user right is +gx.
                Math.toDegrees(atan2(normGx.toDouble(), -normGy.toDouble())).toFloat()
            }
        }

        val isRollLevel = abs(rollDegrees) <= 1.0f
        val isPitchLevel = abs(pitchDegrees) <= 1.5f
        val isLevel = isRollLevel && isPitchLevel

        _orientationFlow.value = HorizonOrientation(
            pitchDegrees = pitchDegrees,
            rollDegrees = rollDegrees,
            isRollLevel = isRollLevel,
            isPitchLevel = isPitchLevel,
            isLevel = isLevel
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
