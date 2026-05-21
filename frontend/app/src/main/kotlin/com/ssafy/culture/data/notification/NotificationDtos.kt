package com.ssafy.culture.data.notification

import com.ssafy.culture.domain.model.NotificationItem
import com.ssafy.culture.domain.model.NotificationType

data class NotificationApiResponse<T>(
    val success: Boolean,
    val code: Int,
    val message: String,
    val data: T?,
)

data class NotificationDto(
    val notificationId: String?,
    val type: String?,
    val message: String?,
    val isRead: Boolean?,
    val createdAt: String?,
)

internal fun NotificationDto.toDomain(): NotificationItem =
    NotificationItem(
        id = notificationId.orEmpty(),
        type = NotificationType.from(type),
        message = message.orEmpty(),
        isRead = isRead ?: false,
        createdAt = createdAt.orEmpty(),
    )
