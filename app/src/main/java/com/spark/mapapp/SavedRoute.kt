package com.spark.mapapp

import com.google.android.gms.maps.model.LatLng

data class SavedRoute(
    val waypoints: List<String>, // List of waypoint strings
    val polylinePoints: List<LatLng> // List of LatLng points for the polyline
)
