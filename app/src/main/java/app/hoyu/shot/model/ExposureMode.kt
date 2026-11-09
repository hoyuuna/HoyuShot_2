package app.hoyu.shot.model

enum class ExposureMode(val label: String, val fullName: String, val description: String) {
    AUTO("A", "Auto", "Camera automatically calculates ISO, Shutter, and White balance."),
    PROGRAM("P", "Program", "Camera sets ISO and Shutter. You can adjust EV compensation."),
    SHUTTER_PRIORITY("S", "Shutter Priority", "Manual Shutter speed with automatic ISO compensation."),
    ISO_PRIORITY("ISO", "ISO Priority", "Manual ISO sensitivity with automatic Shutter compensation."),
    MANUAL("M", "Manual", "Full manual control over ISO, Shutter, Focus, and White balance.")
}

enum class AspectRatio(val ratioName: String, val widthFactor: Int, val heightFactor: Int) {
    RATIO_4_3("4:3", 4, 3),
    RATIO_16_9("16:9", 16, 9),
    RATIO_1_1("1:1", 1, 1),
    RATIO_3_2("3:2", 3, 2)
}

enum class WhiteBalancePreset(val label: String) {
    AUTO("AWB"),
    DAYLIGHT("DAY"),
    CLOUDY("CLD"),
    SHADE("SHD"),
    FLUORESCENT("FLR"),
    INCANDESCENT("INC"),
    CUSTOM_KELVIN("K")
}

enum class GridMode(val label: String) {
    NONE("OFF"),
    THIRDS("3x3"),
    GOLDEN("PHI")
}

enum class FlashMode(val label: String) {
    OFF("OFF"),
    AUTO("AUTO"),
    ON("ON"),
    TORCH("TORCH")
}

enum class TimerMode(val seconds: Int, val label: String) {
    OFF(0, "OFF"),
    SEC_3(3, "3S"),
    SEC_5(5, "5S"),
    SEC_10(10, "10S")
}

enum class NoiseReductionMode(val label: String, val tag: String) {
    OFF("OFF", "NR OFF"),
    FAST("MIN", "NR MIN"),
    HIGH_QUALITY("HQ", "NR HQ")
}

enum class SharpnessMode(val label: String, val tag: String) {
    OFF("-1", "SHARP -1"),
    NORMAL("0", "SHARP 0"),
    HIGH("+1", "SHARP +1")
}

data class CapturedPhotoInfo(
    val uriString: String,
    val isRaw: Boolean,
    val width: Int,
    val height: Int,
    val iso: Int,
    val shutterSpeed: String,
    val focalLength: String,
    val timestamp: Long
)
