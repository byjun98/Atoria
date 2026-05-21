package com.ssafy.culture.domain.model

data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val message: String,
    val isRead: Boolean,
    val createdAt: String,
)

enum class NotificationType {
    MISSION,
    SYSTEM,
    EVENT,
    UNKNOWN,
    ;

    companion object {
        fun from(value: String?): NotificationType =
            entries.firstOrNull { type -> type.name == value } ?: UNKNOWN
    }
}

data class NotificationSettings(
    val pushEnabled: Boolean = true,
    val eventEnabled: Boolean = true,
    val recommendEnabled: Boolean = true,
)

data class NotificationSummary(
    val totalCount: Int = 0,
    val unreadCount: Int = 0,
    val latestMessage: String? = null,
)
