package com.smartqueue.notification.provider;

import com.smartqueue.notification.domain.Notification;
import com.smartqueue.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LogSmsNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(LogSmsNotificationProvider.class);

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public ProviderResult send(Notification notification) {
        String recipient = notification.getRecipient();
        if (recipient == null && notification.getUser() != null) {
            recipient = notification.getUser().getPhone();
        }

        if (recipient == null || recipient.isBlank()) {
            return ProviderResult.failed("No SMS recipient phone number");
        }

        log.info("[SAFE_DEV_ADAPTER] Sending SMS to {}: Message='{}'", recipient, notification.getMessage());
        return ProviderResult.sent("sms-log-" + UUID.randomUUID());
    }
}
