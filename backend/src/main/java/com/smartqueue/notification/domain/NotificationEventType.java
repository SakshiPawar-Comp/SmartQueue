package com.smartqueue.notification.domain;

public enum NotificationEventType {
    TOKEN_CREATED,
    TOKEN_CALLED,
    POSITION_NEAR,
    APPROACHING_TURN_5,
    APPROACHING_TURN_3,
    TOKEN_HOLD,
    TOKEN_RESUMED,
    TOKEN_SKIPPED,
    TOKEN_COMPLETED,
    TOKEN_CANCELLED,
    QUEUE_CLOSED
}
