package app.hoyu.shot.model

import android.hardware.camera2.CameraCharacteristics

data class CameraDeviceInfo(
    val cameraId: String,
    val lensFacing: Int,
    val focalLength: Float,
    val label: String, // e.g. "0.5x", "1x", "3x", "FRONT"
    val capabilities: HardwareCapabilities
) {
    val isBack: Boolean
        get() = lensFacing == CameraCharacteristics.LENS_FACING_BACK
}
