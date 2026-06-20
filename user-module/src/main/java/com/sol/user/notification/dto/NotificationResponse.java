package com.sol.user.notification.dto;

import com.sol.user.notification.entity.Notification;
import com.sol.user.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType notificationType,
        String title,
        String content,
        String linkTarget,
        Boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getLinkTarget(),
                notification.getRead(),
                notification.getCreatedAt()
        );
    }
}
