package com.smartqueue.queue.api;

import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.queue.api.dto.CreateQueueRequest;
import com.smartqueue.queue.api.dto.QueueResponse;
import com.smartqueue.queue.api.dto.QueueTokenResponse;
import com.smartqueue.queue.api.dto.UpdateQueueRequest;
import com.smartqueue.queue.application.QueueAccessDeniedException;
import com.smartqueue.queue.application.QueueNotFoundException;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.application.QueueService.QueueStatusEstimate;
import com.smartqueue.queue.domain.Queue;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import com.smartqueue.document.application.DocumentVerificationService;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/queues")
public class QueueController {

    private final QueueService queueService;
    private final QueueRepository queueRepository;
    private final QueueTokenRepository queueTokenRepository;
    private final DocumentVerificationService documentVerificationService;

    public QueueController(
            QueueService queueService,
            QueueRepository queueRepository,
            QueueTokenRepository queueTokenRepository,
            DocumentVerificationService documentVerificationService) {
        this.queueService = queueService;
        this.queueRepository = queueRepository;
        this.queueTokenRepository = queueTokenRepository;
        this.documentVerificationService = documentVerificationService;
    }

    /** POST /api/queues - Create a new queue */
    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<Queue>> createQueue(
            @Valid @RequestBody CreateQueueRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        var queue = queueService.createQueue(
                orgId,
                request.getName(),
                request.getDescription(),
                request.getTokenPrefix(),
                request.getBranchId(),
                request.getServiceId(),
                request.getCapacity() != null ? request.getCapacity() : 0,
                request.getIsPriorityEnabled() != null ? request.getIsPriorityEnabled() : false,
                request.getDefaultServiceTimeMinutes() != null ? request.getDefaultServiceTimeMinutes() : 5,
                request.getRequiresDocumentVerification()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(queue, null));
    }

    /** GET /api/queues - Get queues with optional filters */
    @GetMapping
    public ResponseEntity<ApiResponse<List<QueueResponse>>> getQueues(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) QueueStatus status,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID effectiveOrgId = organizationId != null ? organizationId : TenantContext.getCurrentTenant();
        List<Queue> queues;
        if (effectiveOrgId != null || branchId != null || serviceId != null || status != null) {
            queues = queueRepository.findByCriteria(effectiveOrgId, branchId, serviceId, status);
        } else {
            queues = List.of();
        }
        List<QueueResponse> responses = queues.stream().map(q -> {
            long waitingCount = queueTokenRepository.countWaitingByQueueId(q.getId());
            int serviceTime = q.getDefaultServiceTimeMinutes() != null ? q.getDefaultServiceTimeMinutes() : 5;
            int estWait = (int) (waitingCount * (long) serviceTime);
            return QueueResponse.fromEntity(q, waitingCount, estWait);
        }).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, null));
    }

    /** GET /api/queues/{queueId} - Get a specific queue */
    @GetMapping("/{queueId}")
    public ResponseEntity<ApiResponse<QueueResponse>> getQueueById(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Queue queue = queueRepository.findById(queueId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found: " + queueId);
        }
        UUID orgId = TenantContext.getCurrentTenant();
        if (orgId != null && userDetails != null && userDetails.getUserRoles() != null && userDetails.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole() != null && ("ORGANIZATION_ADMIN".equals(ur.getRole().getName()) || "STAFF".equals(ur.getRole().getName())))) {
            if (!queue.getOrganizationId().equals(orgId)) {
                throw new QueueAccessDeniedException("Access denied to this queue");
            }
        }
        long waitingCount = queueTokenRepository.countWaitingByQueueId(queue.getId());
        int serviceTime = queue.getDefaultServiceTimeMinutes() != null ? queue.getDefaultServiceTimeMinutes() : 5;
        int estWait = (int) (waitingCount * (long) serviceTime);
        return ResponseEntity.ok(ApiResponse.success(QueueResponse.fromEntity(queue, waitingCount, estWait), null));
    }

    /** PUT /api/queues/{queueId} - Update queue details */
    @PutMapping("/{queueId}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Queue>> updateQueue(
            @PathVariable UUID queueId,
            @Valid @RequestBody UpdateQueueRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var queue = queueService.updateQueue(
                queueId,
                request.getName(),
                request.getDescription(),
                request.getStatus(),
                request.getBranchId(),
                request.getServiceId(),
                request.getCapacity(),
                request.getIsPriorityEnabled(),
                request.getDefaultServiceTimeMinutes(),
                request.getRequiresDocumentVerification()
        );
        return ResponseEntity.ok(ApiResponse.success(queue, null));
    }

    /** POST /api/queues/{queueId}/open - Open queue */
    @PostMapping("/{queueId}/open")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Queue>> openQueue(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var queue = queueService.openQueue(queueId);
        return ResponseEntity.ok(ApiResponse.success(queue, null));
    }

    /** POST /api/queues/{queueId}/close - Close queue */
    @PostMapping("/{queueId}/close")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Queue>> closeQueue(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var queue = queueService.closeQueue(queueId);
        return ResponseEntity.ok(ApiResponse.success(queue, null));
    }

    /** DELETE /api/queues/{queueId} - Delete / remove a queue */
    @DeleteMapping("/{queueId}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteQueue(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        queueService.deleteQueue(queueId);
        return ResponseEntity.ok(ApiResponse.success(null, null));
    }

    /** POST /api/queues/{queueId}/tokens and POST /api/queues/{queueId}/join - Generate token */
    @PostMapping(value = {"/{queueId}/tokens", "/{queueId}/join"})
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> generateToken(
            @PathVariable UUID queueId,
            @RequestParam(required = false, defaultValue = "NORMAL") com.smartqueue.queue.domain.TokenPriority priority,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.generateToken(queueId, userDetails.getUser(), priority != null ? priority : com.smartqueue.queue.domain.TokenPriority.NORMAL);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** GET /api/queues/{queueId}/tokens - Get all tokens for a queue */
    @GetMapping("/{queueId}/tokens")
    public ResponseEntity<ApiResponse<List<QueueTokenResponse>>> getTokensByQueue(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var tokens = queueService.findTokensByQueueId(queueId);
        List<QueueTokenResponse> responses = tokens.stream().map(tok -> {
            QueueTokenResponse resp = QueueTokenResponse.fromEntity(tok);
            resp.setDocumentVerificationStatus(documentVerificationService.getAggregateStatus(tok));
            resp.setDocumentCount(documentVerificationService.getDocumentsForToken(tok.getId()).size());
            return resp;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success(responses, null));
    }

    /** GET /api/queues/{queueId}/tokens/waiting - Get waiting tokens count */
    @GetMapping("/{queueId}/tokens/waiting")
    public ResponseEntity<ApiResponse<Long>> getWaitingTokensCount(
            @PathVariable UUID queueId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long count = queueService.countWaitingTokens(queueId);
        return ResponseEntity.ok(ApiResponse.success(count, null));
    }

    /** POST /api/queues/{queueId}/call-next and POST /api/queues/{queueId}/next - Call next */
    @PostMapping(value = {"/{queueId}/call-next", "/{queueId}/next"})
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> callNext(
            @PathVariable UUID queueId,
            @RequestParam(required = false) UUID counterId,
            @RequestParam(required = false, defaultValue = "false") boolean forceCall,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.callNext(queueId, counterId, userDetails != null ? userDetails.getUser() : null, forceCall);
        QueueTokenResponse resp = QueueTokenResponse.fromEntity(token);
        resp.setDocumentVerificationStatus(documentVerificationService.getAggregateStatus(token));
        resp.setDocumentCount(documentVerificationService.getDocumentsForToken(token.getId()).size());
        return ResponseEntity.ok(ApiResponse.success(resp, null));
    }

    /** POST /api/queues/tokens/{tokenId}/hold - Hold a called or waiting token */
    @PostMapping("/tokens/{tokenId}/hold")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> holdToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdminOrStaff = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZATION_ADMIN") ||
                               a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                               a.getAuthority().equals("ROLE_STAFF"));
        var token = queueService.holdToken(tokenId, userDetails.getUser(), isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** POST /api/queues/tokens/{tokenId}/resume - Resume a held token */
    @PostMapping("/tokens/{tokenId}/resume")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> resumeToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdminOrStaff = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZATION_ADMIN") ||
                               a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                               a.getAuthority().equals("ROLE_STAFF"));
        var token = queueService.resumeToken(tokenId, userDetails.getUser(), isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** POST /api/queues/tokens/{tokenId}/skip - Skip a token */
    @PostMapping("/tokens/{tokenId}/skip")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> skipToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.skipToken(tokenId, userDetails != null ? userDetails.getUser() : null);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** POST /api/queues/tokens/{tokenId}/complete - Complete a called token */
    @PostMapping("/tokens/{tokenId}/complete")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> completeToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.completeToken(tokenId, userDetails != null ? userDetails.getUser() : null);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** POST /api/queues/tokens/{tokenId}/leave - Customer leave queue */
    @PostMapping("/tokens/{tokenId}/leave")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> leaveToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.leaveToken(tokenId, userDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** POST /api/queues/tokens/{tokenId}/cancel - Cancel token */
    @PostMapping("/tokens/{tokenId}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> cancelToken(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZATION_ADMIN") ||
                               a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                               a.getAuthority().equals("ROLE_STAFF"));
        var token = queueService.cancelToken(tokenId, userDetails.getUser(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** GET /api/queues/tokens/{tokenId} - Get token details */
    @GetMapping("/tokens/{tokenId}")
    public ResponseEntity<ApiResponse<QueueTokenResponse>> getTokenById(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        var token = queueService.findTokenById(tokenId);
        return ResponseEntity.ok(ApiResponse.success(QueueTokenResponse.fromEntity(token), null));
    }

    /** GET /api/queues/{queueId}/estimate/{tokenId} - Get estimate */
    @GetMapping("/{queueId}/estimate/{tokenId}")
    public ResponseEntity<ApiResponse<QueueStatusEstimate>> getEstimate(
            @PathVariable UUID queueId,
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        queueService.findQueueById(queueId);
        var estimate = queueService.getEstimate(tokenId);
        return ResponseEntity.ok(ApiResponse.success(estimate, null));
    }
}
