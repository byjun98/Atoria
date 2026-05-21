package com.ssafy.culture.data.notification

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Streaming

interface NotificationApi {
    @GET("notifications")
    suspend fun getNotifications(
        @Header("Authorization") authorization: String? = null,
    ): NotificationApiResponse<List<NotificationDto>>

    @PUT("notifications/{notificationId}/read")
    suspend fun markAsRead(
        @Path("notificationId") notificationId: String,
        @Header("Authorization") authorization: String? = null,
    ): NotificationApiResponse<Any?>

    @PUT("notifications/read-all")
    suspend fun markAllAsRead(
        @Header("Authorization") authorization: String? = null,
    ): NotificationApiResponse<Any?>

    @DELETE("notifications/{notificationId}")
    suspend fun deleteNotification(
        @Path("notificationId") notificationId: String,
        @Header("Authorization") authorization: String? = null,
    ): NotificationApiResponse<Any?>

    @Streaming
    @GET("notifications/subscribe")
    suspend fun subscribe(
        @Header("Authorization") authorization: String? = null,
    ): Response<ResponseBody>
}
