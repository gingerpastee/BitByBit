package com.example.cradet

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class LocationHelper(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun fetchLastLocation(onResult: (Location?) -> Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onResult(location)
            } else {
                // If last location is null, try to get a fresh one
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener { freshLocation ->
                    onResult(freshLocation)
                }
            }
        }.addOnFailureListener {
            onResult(null)
        }
    }

    fun getGoogleMapsLink(location: Location): String {
        return "https://maps.google.com/?q=${location.latitude},${location.longitude}"
    }
}
