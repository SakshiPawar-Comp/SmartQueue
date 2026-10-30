package com.smartqueue.notification.application;

import com.smartqueue.auth.domain.User;
import com.smartqueue.notification.domain.NotificationChannel;
import com.smartqueue.notification.domain.NotificationEventType;
import com.smartqueue.notification.domain.UserNotificationPreference;
import com.smartqueue.notification.infrastructure.NotificationRepository;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.application.QueueService.QueueStatusEstimate;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.event.QueueEvent;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final NotificationTemplateService templateService;
    private final NotificationRepository notificationRepository;
    private final QueueRepository queueRepository;
    private final QueueTokenRepository queueTokenRepository;
    private final OrganizationRepository organizationRepository;
    private final QueueService queueService;

    public NotificationEventListener(
            NotificationService notificationService,
            NotificationTemplateService templateService,
            NotificationRepository notificationRepository,
            QueueRepository queueRepository,
            QueueTokenRepository queueTokenRepository,
            OrganizationRepository organizationRepository,
            QueueService queueService
    ) {
        this.notificationService = notificationService;
        this.templateService = templateService;
        this.notificationRepository = notificationRepository;
        this.queueRepository = queueRepository;
        this.queueTokenRepository = queueTokenRepository;
        this.organizationRepository = organizationRepository;
        this.queueService = queueService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueueEvent(QueueEvent event) {
        try {
            log.info("NotificationEventListener received committed QueueEvent: eventType={}, queueId={}, tokenId={}",
                    event.eventType(), event.queueId(), event.tokenId());

            NotificationEventType notifType = mapEventType(event.eventType());
            if (notifType == null) {
                return;
            }

            Queue queue = queueRepository.findById(event.queueId());
            if (queue == null) {
                return;
            }

            Organization org = organizationRepository.findById(queue.getOrganizationId()).orElse(null);
            if (org == null) {
                return;
            }

            // 1. Process target token notification if present
            if (event.tokenId() != null) {
                QueueToken token = queueTokenRepository.findById(event.tokenId());
                if (token != null && token.getCustomer() != null) {
                    processCustomerTokenNotification(org, queue, token, notifType);
                }
            }

            // 2. Check position-near transitions for waiting tokens when queue moves
            if ("TOKEN_CALLED".equals(event.eventType()) || "TOKEN_COMPLETED".equals(event.eventType()) || "TOKEN_SKIPPED".equals(event.eventType())) {
                checkPositionNearTransitions(org, queue);
            }

        } catch (Exception e) {
            log.error("Error processing AFTER_COMMIT notification event: {}", e.getMessage(), e);
        }
    }

    private void processCustomerTokenNotification(
            Organization org,
            Queue queue,
            QueueToken token,
            NotificationEventType eventType
    ) {
        User customer = token.getCustomer();
        UserNotificationPreference pref = notificationService.getOrCreatePreferences(customer.getId());

        QueueStatusEstimate estimate = queueService.getEstimate(token.getId());
        String lang = customer.getPreferredLanguage() != null ? customer.getPreferredLanguage() : "en";

        Map<String, String> variables = new HashMap<>();
        variables.put("tokenNumber", token.getTokenNumber());
        variables.put("queueName", queue.getName());
        
        long tokensAhead = estimate.position() > 0 ? estimate.position() - 1 : 0;
        variables.put("position", String.valueOf(tokensAhead));
        variables.put("estimatedWait", String.valueOf(estimate.estimatedWaitMinutes()));
        
        if (estimate.expectedTurnTime() != null) {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
            variables.put("expectedTurnTime", estimate.expectedTurnTime().format(formatter));
            
            if (estimate.recommendedDepartureTime() != null) {
                // If recommended departure time has passed, say "now"
                if (estimate.recommendedDepartureTime().isBefore(java.time.ZonedDateTime.now())) {
                    variables.put("recommendedDepartureTime", "now (your recommended departure time has passed)");
                } else {
                    variables.put("recommendedDepartureTime", estimate.recommendedDepartureTime().format(formatter));
                }
                
                if (token.getExpectedTravelTimeMinutes() != null) {
                    variables.put("expectedArrivalTime", estimate.expectedTurnTime().minusMinutes(5).format(formatter));
                }
            }
        }

        NotificationTemplateService.RenderedTemplate rendered = templateService.render(eventType, lang, variables);

        // Channel: IN_APP (default true)
        if (pref.isInAppEnabled() || isMandatoryEvent(eventType)) {
            String idempotencyKey = org.getId() + ":" + token.getId() + ":" + eventType + ":IN_APP";
            notificationService.createAndDispatch(
                    org, customer, token, queue,
                    NotificationChannel.IN_APP,
                    eventType,
                    customer.getId().toString(),
                    rendered.title(),
                    rendered.message(),
                    idempotencyKey
            );
        }

        // Channel: EMAIL
        if (pref.isEmailEnabled() && customer.getEmail() != null) {
            String idempotencyKey = org.getId() + ":" + token.getId() + ":" + eventType + ":EMAIL";
            notificationService.createAndDispatch(
                    org, customer, token, queue,
                    NotificationChannel.EMAIL,
                    eventType,
                    customer.getEmail(),
                    rendered.title(),
                    rendered.message(),
                    idempotencyKey
            );
        }

        // Channel: SMS
        if (pref.isSmsEnabled() && customer.getPhone() != null && !customer.getPhone().isBlank()) {
            String idempotencyKey = org.getId() + ":" + token.getId() + ":" + eventType + ":SMS";
            notificationService.createAndDispatch(
                    org, customer, token, queue,
                    NotificationChannel.SMS,
                    eventType,
                    customer.getPhone(),
                    rendered.title(),
                    rendered.message(),
                    idempotencyKey
            );
        }

        // Channel: PUSH
        if (pref.isPushEnabled()) {
            String idempotencyKey = org.getId() + ":" + token.getId() + ":" + eventType + ":PUSH";
            notificationService.createAndDispatch(
                    org, customer, token, queue,
                    NotificationChannel.PUSH,
                    eventType,
                    customer.getId().toString(),
                    rendered.title(),
                    rendered.message(),
                    idempotencyKey
            );
        }
    }

    private void checkPositionNearTransitions(Organization org, Queue queue) {
        List<QueueToken> tokens = queueTokenRepository.findByQueueId(queue.getId());
        for (QueueToken token : tokens) {
            if (token.getStatus() != com.smartqueue.queue.domain.TokenStatus.WAITING || token.getCustomer() == null) {
                continue;
            }

            User customer = token.getCustomer();
            UserNotificationPreference pref = notificationService.getOrCreatePreferences(customer.getId());
            QueueStatusEstimate estimate = queueService.getEstimate(token.getId());

            // If position is within threshold, and POSITION_NEAR hasn't been emitted yet for this token
            if (estimate.position() <= pref.getPositionNearThreshold()) {
                boolean alreadyNotified = notificationRepository.existsByTokenIdAndEventType(
                        token.getId(), NotificationEventType.POSITION_NEAR);

                if (!alreadyNotified) {
                    processCustomerTokenNotification(org, queue, token, NotificationEventType.POSITION_NEAR);
                }
            }

            // 5-person reminder (tokens ahead == 5, so position == 6)
            long tokensAhead = estimate.position() - 1;
            
            if (tokensAhead <= 5) {
                boolean alreadyNotified5 = notificationRepository.existsByTokenIdAndEventType(
                        token.getId(), NotificationEventType.APPROACHING_TURN_5);
                
                if (!alreadyNotified5) {
                    processCustomerTokenNotification(org, queue, token, NotificationEventType.APPROACHING_TURN_5);
                }
            }

            // 3-person reminder (tokens ahead == 3, so position == 4)
            if (tokensAhead <= 3) {
                boolean alreadyNotified3 = notificationRepository.existsByTokenIdAndEventType(
                        token.getId(), NotificationEventType.APPROACHING_TURN_3);
                
                if (!alreadyNotified3) {
                    processCustomerTokenNotification(org, queue, token, NotificationEventType.APPROACHING_TURN_3);
                }
            }
        }
    }

    private boolean isMandatoryEvent(NotificationEventType eventType) {
        return eventType == NotificationEventType.TOKEN_CALLED || eventType == NotificationEventType.TOKEN_CREATED;
    }

    private NotificationEventType mapEventType(String queueEventType) {
        if (queueEventType == null) return null;
        return switch (queueEventType) {
            case "TOKEN_CREATED" -> NotificationEventType.TOKEN_CREATED;
            case "TOKEN_CALLED" -> NotificationEventType.TOKEN_CALLED;
            case "TOKEN_HELD" -> NotificationEventType.TOKEN_HOLD;
            case "TOKEN_RESUMED" -> NotificationEventType.TOKEN_RESUMED;
            case "TOKEN_SKIPPED" -> NotificationEventType.TOKEN_SKIPPED;
            case "TOKEN_COMPLETED" -> NotificationEventType.TOKEN_COMPLETED;
            case "TOKEN_CANCELLED" -> NotificationEventType.TOKEN_CANCELLED;
            case "QUEUE_CLOSED" -> NotificationEventType.QUEUE_CLOSED;
            default -> null;
        };
    }
}
