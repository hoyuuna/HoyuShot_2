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
    // Use direct hardware fused gravity sensor for jitter-free, compass-independent horizon leveling
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _orientationFlow = MutableStateFlow(HorizonOrientation())
    val orientationFlow: StateFlow<HorizonOrientation> = _orientationFlow.asStateFlow()

    // Current UI rotation degrees: 0 (portrait), 90 (landscape right), 180 (inverted), 270 (landscape left)
    var currentUiRotationDegrees: Float = 0f

    fun start() {
        gravitySensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val gx = event.values[0]
        val gy = event.values[1]
        val gz = event.values[2]

        val norm = sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()
        if (norm <= 0.001f) return

        val normGx = gx / norm
        val normGy = gy / norm
        val normGz = gz / norm

        // 1. Eye-Level Pitch (Thẳng tầm mắt):
        // gz is 0 when phone is held vertical at eye level. Tilted up -> gz > 0, down -> gz < 0.
        val pitchRad = asin(normGz.coerceIn(-1f, 1f).toDouble())
        val pitchDegrees = Math.toDegrees(pitchRad).toFloat()

        // 2. Pure physical Roll for each of the 4 cardinal phone orientations (0, 90, 180, 270)
        val rollDegrees = when (currentUiRotationDegrees) {
            90f -> {
                // Landscape right (top speaker on left, charging port on right):
                Math.toDegrees(atan2(normGy.toDouble(), normGx.toDouble())).toFloat()
            }
            180f -> {
                // Portrait inverted:
                Math.toDegrees(atan2(normGx.toDouble(), -normGy.toDouble())).toFloat()
            }
            270f -> {
                // Landscape left (top speaker on right, charging port on left):
                -Math.toDegrees(atan2(normGy.toDouble(), -normGx.toDouble())).toFloat()
            }
            else -> {
                // Portrait 0° (normal):
                -Math.toDegrees(atan2(normGx.toDouble(), normGy.toDouble())).toFloat()
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
