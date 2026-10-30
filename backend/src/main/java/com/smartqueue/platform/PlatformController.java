package com.smartqueue.platform;

import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final String applicationName;
    private final String phase;

    PlatformController(
            @Value("${spring.application.name}") String applicationName,
            @Value("${smartqueue.phase}") String phase
    ) {
        this.applicationName = applicationName;
        this.phase = phase;
    }

    @GetMapping("/health")
    ResponseEntity<ApiResponse<PlatformHealthResponse>> health() {
        PlatformHealthResponse health = new PlatformHealthResponse(
                applicationName,
                "UP",
                phase,
                Instant.now()
        );
        return ResponseEntity.ok(ApiResponse.success(health, MDC.get("correlationId")));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Map<String, Object> data = Map.of(
                "message", "Platform dashboard - accessible to all authenticated users",
                "user", userDetails.getUsername(),
                "roles", userDetails.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .toList()
        );
        return ResponseEntity.ok(ApiResponse.success(data, MDC.get("correlationId")));
    }

    @GetMapping("/admin/staff-panel")
    @PreAuthorize("hasRole('STAFF') or hasRole('BRANCH_ADMIN') or hasRole('ORGANIZATION_ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> staffPanel() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "Staff panel - STAFF, ORG_ADMIN, or SUPER_ADMIN only"),
                MDC.get("correlationId")));
    }

    @GetMapping("/admin/org-management")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> orgManagement() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "Organization management - ORG_ADMIN or SUPER_ADMIN only"),
                MDC.get("correlationId")));
    }

    @GetMapping("/admin/super-admin")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> superAdminPanel() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "Super admin panel - SUPER_ADMIN only"),
                MDC.get("correlationId")));
    }

    @GetMapping("/admin/branch-management")
    @PreAuthorize("hasRole('BRANCH_ADMIN') or hasRole('ORGANIZATION_ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> branchManagement() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("message", "Branch management - BRANCH_ADMIN, ORG_ADMIN, or SUPER_ADMIN only"),
                MDC.get("correlationId")));
    }

    record PlatformHealthResponse(
            String application,
            String status,
            String phase,
            Instant checkedAt
    ) {
    }
}
