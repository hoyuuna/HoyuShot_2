package app.hoyu.shot.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

class LocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var cachedLocation: Location? = null
    private var isListening = false
    var onLocationChangedCallback: ((Location) -> Unit)? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val current = cachedLocation
            if (current == null || location.time >= current.time - 5000L) {
                cachedLocation = location
                onLocationChangedCallback?.invoke(location)
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
            // Listen on all active providers (Fused, GPS, Network)
            val providers = lm.allProviders.filter { it != LocationManager.PASSIVE_PROVIDER }
            for (provider in providers) {
                if (lm.isProviderEnabled(provider)) {
                    try {
                        lm.requestLocationUpdates(
                            provider,
                            5000L,
                            5f,
                            locationListener,
                            Looper.getMainLooper()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            isListening = true
            // Prime cache with freshest known location immediately
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
        var bestLocation: Location? = null

        val candidateProviders = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            candidateProviders.add(LocationManager.FUSED_PROVIDER)
        }
        candidateProviders.add(LocationManager.GPS_PROVIDER)
        candidateProviders.add(LocationManager.NETWORK_PROVIDER)
        candidateProviders.add(LocationManager.PASSIVE_PROVIDER)

        for (provider in candidateProviders) {
            try {
                if (lm.isProviderEnabled(provider)) {
                    val loc = lm.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
                    }
                }
            } catch (e: Exception) {
                // Ignore provider error
            }
        }

        // Check any remaining providers from allProviders
        for (provider in lm.allProviders) {
            if (candidateProviders.contains(provider)) continue
            try {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.time > bestLocation.time) {
                    bestLocation = loc
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        return bestLocation
    }

    @SuppressLint("MissingPermission")
    fun getBestLocation(): Location? {
        if (!hasLocationPermission()) return null
        val loc = cachedLocation ?: getLastKnownLocationInternal()
        if (loc != null) {
            cachedLocation = loc
        }
        return loc
    }

    // Retain legacy method signature for compatibility
    fun getLastKnownLocation(): Location? = getBestLocation()
}
