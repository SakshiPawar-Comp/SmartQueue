package com.smartqueue.notification.api;

import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.notification.api.dto.NotificationResponse;
import com.smartqueue.notification.application.NotificationService;
import com.smartqueue.notification.domain.Notification;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** GET /api/admin/notifications - Organization operational notification log */
    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getOrganizationNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        Page<Notification> notifs = notificationService.getAdminNotifications(orgId, PageRequest.of(page, size));
        List<NotificationResponse> responses = notifs.map(NotificationResponse::fromEntity).getContent();
        return ResponseEntity.ok(ApiResponse.success(responses, MDC.get("correlationId")));
    }
}
