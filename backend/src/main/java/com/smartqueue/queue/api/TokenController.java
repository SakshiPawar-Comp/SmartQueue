package com.smartqueue.queue.api;

import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.queue.api.dto.QueueTokenResponse;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.application.QueueService.QueueStatusEstimate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final QueueService queueService;

    public TokenController(QueueService queueService) {
        this.queueService = queueService;
    }

    /** GET /api/tokens/{tokenId} - Get token details */
    @GetMapping("/{tokenId}")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> getTokenById(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.findTokenById(tokenId);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** GET /api/tokens/{tokenId}/estimate - Get token estimate */
    @GetMapping("/{tokenId}/estimate")
    public ResponseEntity<ApiResponse<QueueStatusEstimate>> getEstimate(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var estimate = queueService.getEstimate(tokenId);
        return ResponseEntity.ok(ApiResponse.success(estimate, null));
    }

    /** POST /api/tokens/{tokenId}/hold - Hold token */
    @PostMapping("/{tokenId}/hold")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'BRANCH_ADMIN', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> holdToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdminOrStaff = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZATION_ADMIN") ||
                               a.getAuthority().equals("ROLE_BRANCH_ADMIN") ||
                               a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                               a.getAuthority().equals("ROLE_STAFF"));
        var token = queueService.holdToken(tokenId, userDetails.getUser(), isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** POST /api/tokens/{tokenId}/resume - Resume token */
    @PostMapping("/{tokenId}/resume")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'BRANCH_ADMIN', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> resumeToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdminOrStaff = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZATION_ADMIN") ||
                               a.getAuthority().equals("ROLE_BRANCH_ADMIN") ||
                               a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                               a.getAuthority().equals("ROLE_STAFF"));
        var token = queueService.resumeToken(tokenId, userDetails.getUser(), isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** POST /api/tokens/{tokenId}/skip - Skip token */
    @PostMapping("/{tokenId}/skip")
    @PreAuthorize("hasAnyRole('STAFF', 'BRANCH_ADMIN', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> skipToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.skipToken(tokenId);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** POST /api/tokens/{tokenId}/complete - Complete token */
    @PostMapping("/{tokenId}/complete")
    @PreAuthorize("hasAnyRole('STAFF', 'BRANCH_ADMIN', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> completeToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.completeToken(tokenId);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** POST /api/tokens/{tokenId}/leave - Leave token (customer) */
    @PostMapping("/{tokenId}/leave")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'BRANCH_ADMIN', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> leaveToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.leaveToken(tokenId, userDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** POST /api/tokens/{tokenId}/cancel - Cancel token (admin or owner) */
    @PostMapping("/{tokenId}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'BRANCH_ADMIN', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> cancelToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZATION_ADMIN") ||
                               a.getAuthority().equals("ROLE_BRANCH_ADMIN") ||
                               a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                               a.getAuthority().equals("ROLE_STAFF"));
        var token = queueService.cancelToken(tokenId, userDetails.getUser(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** PUT /api/tokens/{tokenId}/travel-time - Update expected travel time */
    @PutMapping("/{tokenId}/travel-time")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> updateTravelTime(
            @PathVariable UUID tokenId,
            @Valid @RequestBody com.smartqueue.queue.api.dto.UpdateTravelTimeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.updateTravelTime(tokenId, request.getTravelTimeMinutes(), userDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }
}
