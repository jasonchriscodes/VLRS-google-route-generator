package com.spark.mapapp

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

data class DirectionsResponse(
//    val routes: List<Route>
    val routes: List<Route>
)

data class Route(
    @SerializedName("overview_polyline")
    val overviewPolyline: OverviewPolyline?
)


data class OverviewPolyline(
    val points: String
)



interface DirectionsApiService {
    @GET("directions/json")
    fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("waypoints") waypoints: String // New waypoints parameter

    ): Call<DirectionsResponse>
}

