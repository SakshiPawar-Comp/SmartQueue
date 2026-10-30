package com.smartqueue.notification.api.dto;

import com.smartqueue.notification.domain.UserNotificationPreference;

import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID id,
        UUID userId,
        boolean inAppEnabled,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean pushEnabled,
        int positionNearThreshold
) {
    public static NotificationPreferenceResponse fromEntity(UserNotificationPreference entity) {
        return new NotificationPreferenceResponse(
                entity.getId(),
                entity.getUser().getId(),
                entity.isInAppEnabled(),
                entity.isEmailEnabled(),
                entity.isSmsEnabled(),
                entity.isPushEnabled(),
                entity.getPositionNearThreshold()
        );
    }
}
