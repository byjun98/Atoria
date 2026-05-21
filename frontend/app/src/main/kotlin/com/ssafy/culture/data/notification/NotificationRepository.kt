package com.ssafy.culture.data.notification

import com.ssafy.culture.domain.model.NotificationItem
import com.ssafy.culture.domain.model.NotificationSettings
import com.ssafy.culture.domain.model.NotificationSummary
import com.ssafy.culture.domain.model.NotificationType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class NotificationRepository @Inject constructor() {
    private var mockNotifications: List<NotificationItem> = createMockNotifications()
    private var notificationSettings: NotificationSettings = NotificationSettings()

    suspend fun getNotifications(): List<NotificationItem> = withContext(Dispatchers.IO) {
        mockNotifications
    }

    suspend fun getNotificationSummary(): NotificationSummary = withContext(Dispatchers.IO) {
        createSummary(getNotifications())
    }

    suspend fun getNotificationSettings(): NotificationSettings = withContext(Dispatchers.IO) {
        notificationSettings
    }

    suspend fun updateNotificationSettings(
        settings: NotificationSettings,
    ): NotificationSettings = withContext(Dispatchers.IO) {
        notificationSettings = settings
        notificationSettings
    }

    suspend fun markAsRead(notificationId: String): Unit = withContext(Dispatchers.IO) {
        mockNotifications = mockNotifications.map { notification ->
            if (notification.id == notificationId) {
                notification.copy(isRead = true)
            } else {
                notification
            }
        }
    }

    suspend fun markAllAsRead(): Unit = withContext(Dispatchers.IO) {
        mockNotifications = mockNotifications.map { notification ->
            notification.copy(isRead = true)
        }
    }

    suspend fun deleteNotification(notificationId: String): Unit = withContext(Dispatchers.IO) {
        mockNotifications = mockNotifications.filterNot { notification ->
            notification.id == notificationId
        }
    }

    suspend fun subscribe(): Boolean = withContext(Dispatchers.IO) {
        true
    }
}

private fun NotificationApiResponse<*>.requireSuccess() {
    check(success) {
        message
    }
}

private fun createSummary(notifications: List<NotificationItem>): NotificationSummary =
    NotificationSummary(
        totalCount = notifications.size,
        unreadCount = notifications.count { notification -> !notification.isRead },
        latestMessage = notifications.firstOrNull()?.message,
    )

private fun createMockNotifications(): List<NotificationItem> =
    listOf(
        NotificationItem(
            id = "mock-notification-1",
            type = NotificationType.MISSION,
            message = "첨성대 별빛 퀘스트가 새로 도착했어요.",
            isRead = false,
            createdAt = "2026-04-28T09:30:00",
        ),
        NotificationItem(
            id = "mock-notification-2",
            type = NotificationType.EVENT,
            message = "이번 주말 경주 문화 산책 이벤트가 열려요.",
            isRead = false,
            createdAt = "2026-04-27T18:10:00",
        ),
        NotificationItem(
            id = "mock-notification-3",
            type = NotificationType.SYSTEM,
            message = "프로필 설정이 정상적으로 저장되었습니다.",
            isRead = true,
            createdAt = "2026-04-26T14:05:00",
        ),
    )
