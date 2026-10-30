package com.smartqueue.notification.provider;

import com.smartqueue.notification.domain.Notification;
import com.smartqueue.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LogPushNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(LogPushNotificationProvider.class);

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public ProviderResult send(Notification notification) {
        String recipient = notification.getRecipient();
        if (recipient == null && notification.getUser() != null) {
            recipient = notification.getUser().getId().toString();
        }

        log.info("[SAFE_DEV_ADAPTER] Sending PUSH to {}: Title='{}', Body='{}'",
                recipient, notification.getTitle(), notification.getMessage());
        return ProviderResult.sent("push-log-" + UUID.randomUUID());
    }
}
