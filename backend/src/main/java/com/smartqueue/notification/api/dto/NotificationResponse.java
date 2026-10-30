package com.smartqueue.notification.api.dto;

import com.smartqueue.notification.domain.Notification;
import com.smartqueue.notification.domain.NotificationChannel;
import com.smartqueue.notification.domain.NotificationEventType;
import com.smartqueue.notification.domain.NotificationStatus;

import java.time.ZonedDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID organizationId,
        UUID userId,
        UUID tokenId,
        String tokenNumber,
        UUID queueId,
        String queueName,
        NotificationChannel channel,
        NotificationEventType eventType,
        NotificationStatus status,
        String title,
        String message,
        boolean isRead,
        ZonedDateTime readAt,
        ZonedDateTime createdAt
) {
    public static NotificationResponse fromEntity(Notification entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getOrganization() != null ? entity.getOrganization().getId() : null,
                entity.getUser() != null ? entity.getUser().getId() : null,
                entity.getToken() != null ? entity.getToken().getId() : null,
                entity.getToken() != null ? entity.getToken().getTokenNumber() : null,
                entity.getQueue() != null ? entity.getQueue().getId() : null,
                entity.getQueue() != null ? entity.getQueue().getName() : null,
                entity.getChannel(),
                entity.getEventType(),
                entity.getStatus(),
                entity.getTitle(),
                entity.getMessage(),
                entity.isRead(),
                entity.getReadAt(),
                entity.getCreatedAt()
        );
    }
}
