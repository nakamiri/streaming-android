package com.reaream.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class LocationProvider(private val context: Context) {

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address.asStateFlow()

    private val _speedKmh = MutableStateFlow(0f)
    val speedKmh: StateFlow<Float> = _speedKmh.asStateFlow()

    private val _permissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = _permissionDenied.asStateFlow()

    private var locationManager: LocationManager? = null
    private var isRunning = false
    private var lastLocation: Location? = null
    private var lastLocationTime: Long = 0L

    private val locationListener = LocationListener { location ->
        Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")

        // Calculate speed
        if (location.hasSpeed() && location.speed > 0f) {
            _speedKmh.value = location.speed * 3.6f // m/s → km/h
        } else {
            val prev = lastLocation
            val prevTime = lastLocationTime
            val now = System.currentTimeMillis()
            if (prev != null && now > prevTime) {
                val distMeters = prev.distanceTo(location)
                val timeSec = (now - prevTime) / 1000f
                if (timeSec > 0f) {
                    _speedKmh.value = (distMeters / timeSec) * 3.6f
                }
            }
        }
        lastLocation = location
        lastLocationTime = System.currentTimeMillis()

        _location.value = location
        reverseGeocode(location)
    }

    fun startUpdates() {
        Log.d(TAG, "startUpdates called, isRunning=$isRunning")
        if (isRunning) return
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Location permission granted: $hasPerm")
        if (!hasPerm) {
            _permissionDenied.value = true
            return
        }
        _permissionDenied.value = false

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = manager

        // Listen on both GPS and FUSED for best coverage
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER)
        var started = false
        for (provider in providers) {
            try {
                if (!manager.isProviderEnabled(provider)) continue
                manager.requestLocationUpdates(
                    provider, 10_000L, 5f, locationListener, Looper.getMainLooper(),
                )
                Log.i(TAG, "Location updates started ($provider)")
                started = true

                manager.getLastKnownLocation(provider)?.let {
                    Log.d(TAG, "Last known ($provider): ${it.latitude}, ${it.longitude}")
                    _location.value = it
                    reverseGeocode(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start $provider", e)
            }
        }
        isRunning = started
        if (!started) Log.e(TAG, "No location providers available")
    }

    fun recheckPermission() {
        if (_permissionDenied.value) {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                _permissionDenied.value = false
                startUpdates()
            }
        }
    }

    fun stopUpdates() {
        if (!isRunning) return
        locationManager?.removeUpdates(locationListener)
        isRunning = false
    }

    private fun reverseGeocode(location: Location) {
        try {
            if (!Geocoder.isPresent()) return
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                    _address.value = addresses.firstOrNull()?.let { addr ->
                        listOfNotNull(addr.locality, addr.adminArea).joinToString(", ")
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                _address.value = addresses?.firstOrNull()?.let { addr ->
                    listOfNotNull(addr.locality, addr.adminArea).joinToString(", ")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed", e)
        }
    }

    fun release() {
        stopUpdates()
    }

    companion object {
        private const val TAG = "LocationProvider"
    }
}
