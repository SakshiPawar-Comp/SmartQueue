package com.smartqueue.counter.api;

import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.counter.api.dto.*;
import com.smartqueue.counter.application.CounterService;
import com.smartqueue.queue.api.dto.QueueTokenResponse;
import com.smartqueue.queue.application.QueueService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/counters")
public class CounterController {

    private final CounterService counterService;
    private final QueueService queueService;

    public CounterController(CounterService counterService, QueueService queueService) {
        this.counterService = counterService;
        this.queueService = queueService;
    }

    /** GET /api/counters - List counters for organization/branch */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CounterResponse>>> getCounters(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID branchId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID effectiveOrgId = organizationId != null ? organizationId : TenantContext.getCurrentTenant();
        List<CounterResponse> counters = effectiveOrgId != null
                ? counterService.listCounters(effectiveOrgId, branchId)
                : List.of();
        return ResponseEntity.ok(ApiResponse.success(counters, MDC.get("correlationId")));
    }

    /** GET /api/counters/{counterId} - Get counter by ID */
    @GetMapping("/{counterId}")
    public ResponseEntity<ApiResponse<CounterResponse>> getCounterById(
            @PathVariable UUID counterId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        CounterResponse response = counterService.getCounterResponse(orgId, counterId);
        return ResponseEntity.ok(ApiResponse.success(response, MDC.get("correlationId")));
    }

    /** POST /api/counters - Create counter */
    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CounterResponse>> createCounter(
            @Valid @RequestBody CreateCounterRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        CounterResponse response = counterService.createCounter(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, MDC.get("correlationId")));
    }

    /** PUT /api/counters/{counterId} - Update counter */
    @PutMapping("/{counterId}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CounterResponse>> updateCounter(
            @PathVariable UUID counterId,
            @Valid @RequestBody UpdateCounterRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        CounterResponse response = counterService.updateCounter(orgId, counterId, request);
        return ResponseEntity.ok(ApiResponse.success(response, MDC.get("correlationId")));
    }

    /** POST /api/counters/{counterId}/assign-staff - Assign staff member to counter */
    @PostMapping("/{counterId}/assign-staff")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CounterResponse>> assignStaff(
            @PathVariable UUID counterId,
            @RequestBody AssignStaffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        CounterResponse response = counterService.assignStaffToCounter(orgId, counterId, request.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response, MDC.get("correlationId")));
    }

    /** POST /api/counters/{counterId}/assign-queues - Assign queues to counter */
    @PostMapping("/{counterId}/assign-queues")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CounterResponse>> assignQueues(
            @PathVariable UUID counterId,
            @Valid @RequestBody AssignQueuesRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        CounterResponse response = counterService.assignQueuesToCounter(orgId, counterId, request.getQueueIds());
        return ResponseEntity.ok(ApiResponse.success(response, MDC.get("correlationId")));
    }

    /** POST /api/counters/{counterId}/next - Call next token from assigned queues */
    @PostMapping("/{counterId}/next")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> callNext(
            @PathVariable UUID counterId,
            @RequestParam(required = false, defaultValue = "false") boolean forceCall,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.callNextByCounter(counterId, userDetails != null ? userDetails.getUser() : null, forceCall);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), MDC.get("correlationId")));
    }
}
