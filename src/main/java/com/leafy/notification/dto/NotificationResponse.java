package com.leafy.notification.dto;

import com.leafy.notification.domain.Notification;

import java.time.LocalDateTime;

/** 알림함 목록의 한 줄. */
public record NotificationResponse(
        Long notificationId,
        String notificationType,
        String message,
        boolean isRead,
        Long plantId,
        String plantNickname,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getNotificationType(),
                n.getMessage(),
                Boolean.TRUE.equals(n.getIsRead()),
                n.getMyPlant() != null ? n.getMyPlant().getPlantId() : null,
                n.getMyPlant() != null ? n.getMyPlant().getNickname() : null,
                n.getCreatedAt());
    }
}
