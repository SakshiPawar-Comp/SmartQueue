package com.smartqueue.branch.api;

import com.smartqueue.branch.api.dto.BranchResponse;
import com.smartqueue.branch.api.dto.CreateBranchRequest;
import com.smartqueue.branch.api.dto.UpdateBranchRequest;
import com.smartqueue.branch.application.BranchAdminService;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.queue.api.dto.QueueResponse;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import com.smartqueue.service.api.dto.ServiceResponse;
import com.smartqueue.service.infrastructure.ServiceRepository;
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
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchRepository branchRepository;
    private final BranchAdminService branchAdminService;
    private final ServiceRepository serviceRepository;
    private final QueueRepository queueRepository;
    private final QueueTokenRepository queueTokenRepository;

    public BranchController(
            BranchRepository branchRepository,
            BranchAdminService branchAdminService,
            ServiceRepository serviceRepository,
            QueueRepository queueRepository,
            QueueTokenRepository queueTokenRepository) {
        this.branchRepository = branchRepository;
        this.branchAdminService = branchAdminService;
        this.serviceRepository = serviceRepository;
        this.queueRepository = queueRepository;
        this.queueTokenRepository = queueTokenRepository;
    }

    /** GET /api/branches - List branches for authenticated user's organization */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getBranches(
            @RequestParam(required = false) UUID organizationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID effectiveOrgId = organizationId != null ? organizationId : TenantContext.getCurrentTenant();
        List<Branch> branches = effectiveOrgId != null
                ? branchRepository.findByOrganizationId(effectiveOrgId)
                : List.of();
        List<BranchResponse> responses = branches.stream().map(BranchResponse::fromEntity).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, MDC.get("correlationId")));
    }

    /** GET /api/branches/{branchId} - Get branch by ID */
    @GetMapping("/{branchId}")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranchById(@PathVariable UUID branchId) {
        return branchRepository.findById(branchId)
                .map(branch -> ResponseEntity.ok(ApiResponse.success(BranchResponse.fromEntity(branch), MDC.get("correlationId"))))
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /api/branches - Create new branch for organization */
    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BranchResponse>> createBranch(
            @Valid @RequestBody CreateBranchRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        Branch branch = branchAdminService.createBranch(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BranchResponse.fromEntity(branch), MDC.get("correlationId")));
    }

    /** PUT /api/branches/{branchId} - Update branch details */
    @PutMapping("/{branchId}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BranchResponse>> updateBranch(
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateBranchRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        Branch branch = branchAdminService.updateBranch(orgId, branchId, request);
        return ResponseEntity.ok(ApiResponse.success(BranchResponse.fromEntity(branch), MDC.get("correlationId")));
    }

    /** GET /api/branches/{branchId}/services - Get services for branch */
    @GetMapping("/{branchId}/services")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getServicesByBranch(@PathVariable UUID branchId) {
        var branchOpt = branchRepository.findById(branchId);
        if (branchOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var branch = branchOpt.get();
        var services = serviceRepository.findByOrganizationIdAndBranchIdOrBranchNull(
                branch.getOrganization().getId(), branchId);
        List<ServiceResponse> responses = services.stream().map(ServiceResponse::fromEntity).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, MDC.get("correlationId")));
    }

    /** GET /api/branches/{branchId}/queues - Get queues for branch */
    @GetMapping("/{branchId}/queues")
    public ResponseEntity<ApiResponse<List<QueueResponse>>> getQueuesByBranch(@PathVariable UUID branchId) {
        List<Queue> queues = queueRepository.findByBranchId(branchId);
        List<QueueResponse> responses = queues.stream().map(q -> {
            long waitingCount = queueTokenRepository.countWaitingByQueueId(q.getId());
            int estWait = (int) (waitingCount * (q.getDefaultServiceTimeMinutes() != null ? q.getDefaultServiceTimeMinutes() : 5));
            return QueueResponse.fromEntity(q, waitingCount, estWait);
        }).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, MDC.get("correlationId")));
    }
}
