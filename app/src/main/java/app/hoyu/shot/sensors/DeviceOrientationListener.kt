package app.hoyu.shot.sensors

import android.content.Context
import android.view.OrientationEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceOrientationListener(context: Context) {

    private val _rotationDegrees = MutableStateFlow(0f)
    val rotationDegrees: StateFlow<Float> = _rotationDegrees.asStateFlow()

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return

            // Snap to 0, 90, 180, 270 degrees
            val target = when (orientation) {
                in 45..134 -> 270f
                in 135..224 -> 180f
                in 225..314 -> 90f
                else -> 0f
            }

            if (_rotationDegrees.value != target) {
                _rotationDegrees.value = target
            }
        }
    }

    fun start() {
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
    }

    fun stop() {
        orientationListener.disable()
    }
}
