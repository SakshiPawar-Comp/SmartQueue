package com.smartqueue.notification.provider;

import com.smartqueue.notification.domain.Notification;
import com.smartqueue.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LogEmailNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(LogEmailNotificationProvider.class);

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public ProviderResult send(Notification notification) {
        String recipient = notification.getRecipient();
        if (recipient == null && notification.getUser() != null) {
            recipient = notification.getUser().getEmail();
        }

        if (recipient == null || recipient.isBlank()) {
            return ProviderResult.failed("No email recipient address");
        }

        log.info("[SAFE_DEV_ADAPTER] Sending EMAIL to {}: Subject='{}', Body='{}'",
                recipient, notification.getTitle(), notification.getMessage());

        return ProviderResult.sent("email-log-" + UUID.randomUUID());
    }
}
