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

class HorizonSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val _orientationFlow = MutableStateFlow(HorizonOrientation())
    val orientationFlow: StateFlow<HorizonOrientation> = _orientationFlow.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

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

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        } else if (event.sensor.type == Sensor.TYPE_GRAVITY) {
            val norm = Math.sqrt(
                (event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]).toDouble()
            ).toFloat()
            if (norm > 0) {
                // Simplified orientation estimation
                val ax = event.values[0] / norm
                val ay = event.values[1] / norm
                val roll = Math.toDegrees(Math.atan2(ax.toDouble(), ay.toDouble())).toFloat()
                _orientationFlow.value = HorizonOrientation(
                    pitchDegrees = 0f,
                    rollDegrees = roll,
                    isLevel = abs(roll) < 1.0f
                )
                return
            }
        }

        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Pitch and Roll converted to degrees
        val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        // Within 1 degree is considered perfectly level (like professional cameras)
        val isLevel = abs(roll) <= 1.0f && abs(pitch) <= 1.0f

        _orientationFlow.value = HorizonOrientation(
            pitchDegrees = pitch,
            rollDegrees = roll,
            isLevel = isLevel
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

