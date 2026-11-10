package app.hoyu.shot.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager

class LocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Location? {
        val lm = locationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    val loc = lm.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || (loc.hasAccuracy() && loc.accuracy < (bestLocation.accuracy.takeIf { it > 0 } ?: Float.MAX_VALUE))) {
                        bestLocation = loc
                    }
                }
            } catch (e: SecurityException) {
                // Permission not granted
            } catch (e: Exception) {
                // Provider error
            }
        }
        return bestLocation
    }
}
