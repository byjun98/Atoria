package com.ssafy.culture.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface KakaoDirectionsApi {
    @GET("v1/directions")
    suspend fun getDirections(
        @Header("Authorization") authorization: String,
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("waypoints") waypoints: String? = null,
        @Query("priority") priority: String = "RECOMMEND",
        @Query("car_fuel") carFuel: String = "GASOLINE",
        @Query("car_hipass") carHipass: Boolean = false,
        @Query("alternatives") alternatives: Boolean = false,
        @Query("road_details") roadDetails: Boolean = false,
        @Query("summary") summary: Boolean = false,
    ): KakaoDirectionsResponse
}
