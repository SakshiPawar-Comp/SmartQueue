package com.smartqueue.notification.api;

import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.notification.api.dto.NotificationPreferenceResponse;
import com.smartqueue.notification.api.dto.NotificationResponse;
import com.smartqueue.notification.api.dto.UpdateNotificationPreferenceRequest;
import com.smartqueue.notification.application.NotificationService;
import com.smartqueue.notification.domain.Notification;
import com.smartqueue.notification.domain.UserNotificationPreference;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers/me")
public class CustomerNotificationController {

    private final NotificationService notificationService;

    public CustomerNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** GET /api/customers/me/notifications - Paginated notification list */
    @GetMapping("/notifications")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        Page<Notification> notifs = notificationService.getCustomerNotifications(userId, PageRequest.of(page, size));
        List<NotificationResponse> responses = notifs.map(NotificationResponse::fromEntity).getContent();
        return ResponseEntity.ok(ApiResponse.success(responses, MDC.get("correlationId")));
    }

    /** GET /api/customers/me/notifications/unread-count - Unread count */
    @GetMapping("/notifications/unread-count")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("unreadCount", count), MDC.get("correlationId")));
    }

    /** PATCH /api/customers/me/notifications/{id}/read - Mark as read */
    @PatchMapping("/notifications/{id}/read")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        Notification updated = notificationService.markAsRead(userId, id);
        return ResponseEntity.ok(ApiResponse.success(NotificationResponse.fromEntity(updated), MDC.get("correlationId")));
    }

    /** POST /api/customers/me/notifications/read-all - Mark all as read */
    @PostMapping("/notifications/read-all")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("markedReadCount", count), MDC.get("correlationId")));
    }

    /** GET /api/customers/me/notification-preferences - Get preferences */
    @GetMapping("/notification-preferences")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getPreferences(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        UserNotificationPreference pref = notificationService.getOrCreatePreferences(userId);
        return ResponseEntity.ok(ApiResponse.success(NotificationPreferenceResponse.fromEntity(pref), MDC.get("correlationId")));
    }

    /** PUT /api/customers/me/notification-preferences - Update preferences */
    @PutMapping("/notification-preferences")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferenceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        UserNotificationPreference updated = notificationService.updatePreferences(
                userId,
                request.getInAppEnabled(),
                request.getEmailEnabled(),
                request.getSmsEnabled(),
                request.getPushEnabled(),
                request.getPositionNearThreshold()
        );
        return ResponseEntity.ok(ApiResponse.success(NotificationPreferenceResponse.fromEntity(updated), MDC.get("correlationId")));
    }
}
