package com.smartqueue.notification.application;

import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.common.tenant.TenantViolationException;
import com.smartqueue.notification.domain.*;
import com.smartqueue.notification.infrastructure.NotificationRepository;
import com.smartqueue.notification.infrastructure.UserNotificationPreferenceRepository;
import com.smartqueue.notification.provider.NotificationProviderRegistry;
import com.smartqueue.notification.provider.ProviderResult;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final NotificationProviderRegistry providerRegistry;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserNotificationPreferenceRepository preferenceRepository,
            UserRepository userRepository,
            NotificationProviderRegistry providerRegistry
    ) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.providerRegistry = providerRegistry;
    }

    /**
     * Create and persist a notification in QUEUED status, then dispatch to the provider.
     * Guaranteed idempotency by unique idempotencyKey.
     */
    @Transactional
    public Notification createAndDispatch(
            Organization organization,
            User user,
            QueueToken token,
            Queue queue,
            NotificationChannel channel,
            NotificationEventType eventType,
            String recipient,
            String title,
            String message,
            String idempotencyKey
    ) {
        // 1. Check idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Notification> existing = notificationRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate notification ignored by idempotency key: {}", idempotencyKey);
                return existing.get();
            }
        }

        // 2. Persist QUEUED record first (survives provider crashes)
        Notification notification = new Notification(
                organization,
                user,
                token,
                queue,
                channel,
                eventType,
                recipient,
                title,
                message,
                idempotencyKey
        );
        notification.setStatus(NotificationStatus.QUEUED);
        notification = notificationRepository.save(notification);

        // 3. Dispatch to channel provider
        dispatchToProvider(notification);

        return notificationRepository.save(notification);
    }

    private void dispatchToProvider(Notification notification) {
        var providerOpt = providerRegistry.getProvider(notification.getChannel());
        if (providerOpt.isEmpty()) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError("No provider registered for channel: " + notification.getChannel());
            notification.setAttemptCount(notification.getAttemptCount() + 1);
            notification.setUpdatedAt(ZonedDateTime.now());
            return;
        }

        try {
            notification.setAttemptCount(notification.getAttemptCount() + 1);
            ProviderResult result = providerOpt.get().send(notification);
            if (result.success()) {
                notification.setStatus(NotificationStatus.SENT);
                notification.setProviderMessageId(result.providerMessageId());
                notification.setSentAt(ZonedDateTime.now());
                notification.setLastError(null);
            } else {
                notification.setStatus(NotificationStatus.FAILED);
                notification.setLastError(result.errorMessage());
            }
        } catch (Exception e) {
            log.error("Exception dispatching notification {}: {}", notification.getId(), e.getMessage(), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError("Provider dispatch exception: " + e.getMessage());
        }
        notification.setUpdatedAt(ZonedDateTime.now());
    }

    /**
     * Get or initialize default user notification preferences.
     */
    @Transactional
    public UserNotificationPreference getOrCreatePreferences(UUID userId) {
        return preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
                    UserNotificationPreference defaultPref = new UserNotificationPreference(user);
                    return preferenceRepository.save(defaultPref);
                });
    }

    /**
     * Update user notification preferences.
     */
    @Transactional
    public UserNotificationPreference updatePreferences(
            UUID userId,
            Boolean inAppEnabled,
            Boolean emailEnabled,
            Boolean smsEnabled,
            Boolean pushEnabled,
            Integer positionNearThreshold
    ) {
        UserNotificationPreference pref = getOrCreatePreferences(userId);
        if (inAppEnabled != null) {
            pref.setInAppEnabled(inAppEnabled);
        }
        if (emailEnabled != null) {
            pref.setEmailEnabled(emailEnabled);
        }
        if (smsEnabled != null) {
            pref.setSmsEnabled(smsEnabled);
        }
        if (pushEnabled != null) {
            pref.setPushEnabled(pushEnabled);
        }
        if (positionNearThreshold != null && positionNearThreshold > 0) {
            pref.setPositionNearThreshold(positionNearThreshold);
        }
        pref.setUpdatedAt(ZonedDateTime.now());
        return preferenceRepository.save(pref);
    }

    /**
     * Customer get my notifications with pagination.
     */
    public Page<Notification> getCustomerNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Customer unread notifications count.
     */
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Mark single notification as read with ownership verification.
     */
    @Transactional
    public Notification markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        if (notification.getUser() == null || !notification.getUser().getId().equals(userId)) {
            throw new TenantViolationException("You cannot access another user's notification");
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(ZonedDateTime.now());
            notification.setUpdatedAt(ZonedDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return notification;
    }

    /**
     * Mark all notifications as read for a customer.
     */
    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsReadForUser(userId, ZonedDateTime.now());
    }

    /**
     * Admin view operational notifications for organization.
     */
    public Page<Notification> getAdminNotifications(UUID organizationId, Pageable pageable) {
        return notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
    }

    /**
     * Retry failed notifications (controlled retry up to maxAttempts).
     */
    @Transactional
    public void retryFailedNotifications(int maxAttempts) {
        List<Notification> failed = notificationRepository
                .findByStatusAndAttemptCountLessThanOrderByCreatedAtAsc(NotificationStatus.FAILED, maxAttempts);

        for (Notification n : failed) {
            log.info("Retrying failed notification {} (attempt {})", n.getId(), n.getAttemptCount() + 1);
            dispatchToProvider(n);
            notificationRepository.save(n);
        }
    }
}
