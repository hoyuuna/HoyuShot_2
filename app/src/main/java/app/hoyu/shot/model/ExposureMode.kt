package app.hoyu.shot.model

enum class ExposureMode(val label: String, val description: String) {
    AUTO("AUTO", "Fully automated exposure and focus"),
    PROGRAM("P", "Program Auto: Camera sets ISO/Shutter, user adjusts EV"),
    SHUTTER_PRIORITY("S", "Shutter Priority: User sets Shutter, camera adjusts ISO"),
    ISO_PRIORITY("ISO", "ISO Priority: User sets ISO, camera adjusts Shutter"),
    MANUAL("M", "Full Manual: User sets ISO, Shutter, Focus, and WB")
}

enum class AspectRatio(val ratioName: String, val widthFactor: Int, val heightFactor: Int) {
    RATIO_4_3("4:3", 4, 3),
    RATIO_16_9("16:9", 16, 9),
    RATIO_1_1("1:1", 1, 1),
    RATIO_3_2("3:2", 3, 2)
}

enum class WhiteBalancePreset(val label: String) {
    AUTO("AWB"),
    DAYLIGHT("Daylight"),
    CLOUDY("Cloudy"),
    SHADE("Shade"),
    FLUORESCENT("Fluorescent"),
    INCANDESCENT("Incandescent"),
    CUSTOM_KELVIN("Kelvin")
}

enum class GridMode(val label: String) {
    NONE("Off"),
    THIRDS("3x3"),
    GOLDEN("Phi")
}

enum class FlashMode(val label: String) {
    OFF("Off"),
    AUTO("Auto"),
    ON("On"),
    TORCH("Torch")
}

enum class TimerMode(val seconds: Int, val label: String) {
    OFF(0, "Off"),
    SEC_3(3, "3s"),
    SEC_5(5, "5s"),
    SEC_10(10, "10s")
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
