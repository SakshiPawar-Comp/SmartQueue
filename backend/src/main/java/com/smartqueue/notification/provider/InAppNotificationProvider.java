package com.smartqueue.notification.provider;

import com.smartqueue.notification.domain.Notification;
import com.smartqueue.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class InAppNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationProvider.class);

    private final SimpMessagingTemplate messagingTemplate;

    public InAppNotificationProvider(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public ProviderResult send(Notification notification) {
        if (notification.getUser() == null) {
            return ProviderResult.failed("No user attached to in-app notification");
        }

        UUID userId = notification.getUser().getId();
        log.info("Dispatching IN_APP notification to user {}: [{}] {}",
                userId, notification.getTitle(), notification.getMessage());

        try {
            Map<String, Object> payload = Map.of(
                    "id", notification.getId().toString(),
                    "title", notification.getTitle(),
                    "message", notification.getMessage(),
                    "eventType", notification.getEventType().name(),
                    "tokenId", notification.getToken() != null ? notification.getToken().getId().toString() : "",
                    "tokenNumber", notification.getToken() != null ? notification.getToken().getTokenNumber() : "",
                    "createdAt", notification.getCreatedAt().toString()
            );

            messagingTemplate.convertAndSend("/topic/users/" + userId + "/notifications", payload);
            return ProviderResult.sent("inapp-" + notification.getId());
        } catch (Exception e) {
            log.error("Failed to broadcast in-app notification via WebSocket: {}", e.getMessage(), e);
            return ProviderResult.failed("WebSocket broadcast error: " + e.getMessage());
        }
    }
}
