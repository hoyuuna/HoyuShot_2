package app.hoyu.shot.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

class LocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var cachedLocation: Location? = null
    private var isListening = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val current = cachedLocation
            if (current == null || !current.hasAccuracy() || (location.hasAccuracy() && location.accuracy <= current.accuracy)) {
                cachedLocation = location
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (!hasLocationPermission() || isListening) return
        val lm = locationManager ?: return
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1500L,
                    1f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1500L,
                    3f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            isListening = true
            // Prime cache with last known immediately while waiting for fresh fix
            if (cachedLocation == null) {
                cachedLocation = getLastKnownLocationInternal()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopListening() {
        if (!isListening) return
        val lm = locationManager ?: return
        try {
            lm.removeUpdates(locationListener)
            isListening = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocationInternal(): Location? {
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
            } catch (e: Exception) {
                // Ignore provider error
            }
        }
        return bestLocation
    }

    @SuppressLint("MissingPermission")
    fun getBestLocation(): Location? {
        if (!hasLocationPermission()) return null
        val loc = cachedLocation ?: getLastKnownLocationInternal()
        cachedLocation = loc
        return loc
    }

    // Retain legacy method signature for compatibility
    fun getLastKnownLocation(): Location? = getBestLocation()
}
