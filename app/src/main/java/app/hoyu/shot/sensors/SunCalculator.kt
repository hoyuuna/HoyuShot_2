package app.hoyu.shot.sensors

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class SunPosition(
    val azimuthDeg: Double,   // Clockwise from True North: 0=N, 90=E, 180=S, 270=W
    val altitudeDeg: Double   // Elevation above horizon (-90..+90)
)

/**
 * Offline solar ephemeris calculator based on Jean Meeus / SunCalc astronomical formulas.
 * Computes exact sun Azimuth and Altitude for any UTC timestamp and Earth coordinate (including polar latitudes).
 */
object SunCalculator {

    private const val RAD = Math.PI / 180.0
    private const val DAY_MS = 86_400_000.0
    private const val J1970 = 2440587.5
    private const val J2000 = 2451545.0
    private const val OBLIQUITY = RAD * 23.4397

    fun calculate(timeMillisUtc: Long, latitudeDeg: Double, longitudeDeg: Double): SunPosition {
        val julian = (timeMillisUtc / DAY_MS) + J1970
        val d = julian - J2000

        // Solar mean anomaly and equation of center
        val m = RAD * (357.5291 + 0.98560028 * d)
        val c = RAD * (1.9148 * sin(m) + 0.0200 * sin(2.0 * m) + 0.0003 * sin(3.0 * m))
        val p = RAD * 102.9372 // perihelion of the Earth
        val eclipticLon = m + c + p + Math.PI

        // Sun declination and right ascension (ecliptic latitude ~= 0)
        val sinDec = sin(OBLIQUITY) * sin(eclipticLon)
        val dec = asin(sinDec.coerceIn(-1.0, 1.0))
        val ra = atan2(sin(eclipticLon) * cos(OBLIQUITY), cos(eclipticLon))

        // Sidereal time & hour angle (observer longitude east-positive)
        val gmst = RAD * (280.1600 + 360.9856235 * d)
        val lst = gmst + longitudeDeg * RAD
        val hourAngle = lst - ra

        val phi = latitudeDeg.coerceIn(-89.999, 89.999) * RAD

        val sinAlt = (sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(hourAngle)).coerceIn(-1.0, 1.0)
        val altDeg = Math.toDegrees(asin(sinAlt))

        // Azimuth measured from South westward, then shifted +180° to clockwise from True North
        val azFromSouth = atan2(
            sin(hourAngle),
            cos(hourAngle) * sin(phi) - tan(dec) * cos(phi)
        )
        val azFromNorthDeg = (((Math.toDegrees(azFromSouth) + 180.0) % 360.0) + 360.0) % 360.0

        return SunPosition(
            azimuthDeg = azFromNorthDeg,
            altitudeDeg = altDeg
        )
    }
}
