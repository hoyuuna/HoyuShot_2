package app.hoyu.shot.model

data class LensItem(
    val id: String,
    val label: String,      // e.g. "0.6x", "1x", "3.2x", "FRONT"
    val cameraId: String,   // Camera device ID to open ("0", "1", etc.)
    val zoomRatio: Float,   // Zoom ratio to apply (0.6f, 1.0f, 3.2f)
    val isFront: Boolean
)
