package com.smartqueue.queue.application;

import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.common.tenant.TenantViolationException;
import com.smartqueue.counter.domain.Counter;
import com.smartqueue.counter.domain.CounterQueueAssignment;
import com.smartqueue.counter.infrastructure.CounterQueueAssignmentRepository;
import com.smartqueue.counter.infrastructure.CounterRepository;
import com.smartqueue.counter.infrastructure.StaffCounterAssignmentRepository;
import com.smartqueue.queue.domain.*;
import com.smartqueue.queue.domain.event.QueueEvent;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import com.smartqueue.service.infrastructure.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smartqueue.document.domain.DocumentVerificationStatus;
import com.smartqueue.document.infrastructure.TokenDocumentRepository;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final QueueRepository queueRepository;
    private final QueueTokenRepository queueTokenRepository;
    private final CounterRepository counterRepository;
    private final CounterQueueAssignmentRepository counterQueueAssignmentRepository;
    private final StaffCounterAssignmentRepository staffCounterAssignmentRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenDocumentRepository tokenDocumentRepository;

    public QueueService(
            QueueRepository queueRepository,
            QueueTokenRepository queueTokenRepository,
            CounterRepository counterRepository,
            CounterQueueAssignmentRepository counterQueueAssignmentRepository,
            StaffCounterAssignmentRepository staffCounterAssignmentRepository,
            BranchRepository branchRepository,
            ServiceRepository serviceRepository,
            UserRoleRepository userRoleRepository,
            ApplicationEventPublisher eventPublisher,
            TokenDocumentRepository tokenDocumentRepository
    ) {
        this.queueRepository = queueRepository;
        this.queueTokenRepository = queueTokenRepository;
        this.counterRepository = counterRepository;
        this.counterQueueAssignmentRepository = counterQueueAssignmentRepository;
        this.staffCounterAssignmentRepository = staffCounterAssignmentRepository;
        this.branchRepository = branchRepository;
        this.serviceRepository = serviceRepository;
        this.userRoleRepository = userRoleRepository;
        this.eventPublisher = eventPublisher;
        this.tokenDocumentRepository = tokenDocumentRepository;
    }

    /** Create a new queue for the current tenant organization */
    @Transactional(propagation = Propagation.REQUIRED)
    public Queue createQueue(UUID organizationId, String name) {
        return createQueue(organizationId, name, null, "A", null, null, 0, false, 5);
    }

    /** Create a new queue with full parameters */
    @Transactional(propagation = Propagation.REQUIRED)
    public Queue createQueue(UUID organizationId, String name, String description, String tokenPrefix) {
        return createQueue(organizationId, name, description, tokenPrefix, null, null, 0, false, 5);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Queue createQueue(
            UUID organizationId,
            String name,
            String description,
            String tokenPrefix,
            UUID branchId,
            UUID serviceId,
            Integer capacity,
            Boolean isPriorityEnabled,
            Integer defaultServiceTimeMinutes) {
        return createQueue(organizationId, name, description, tokenPrefix, branchId, serviceId, capacity, isPriorityEnabled, defaultServiceTimeMinutes, false);
    }

    /** Create a new queue with full operational configuration including document verification */
    @Transactional(propagation = Propagation.REQUIRED)
    public Queue createQueue(
            UUID organizationId,
            String name,
            String description,
            String tokenPrefix,
            UUID branchId,
            UUID serviceId,
            Integer capacity,
            Boolean isPriorityEnabled,
            Integer defaultServiceTimeMinutes,
            Boolean requiresDocumentVerification) {
        var queue = new Queue(organizationId, name);
        if (description != null) {
            queue.setDescription(description.trim());
        }
        if (tokenPrefix != null && !tokenPrefix.isBlank()) {
            queue.setTokenPrefix(tokenPrefix.trim().toUpperCase());
            queue.setCurrentTokenNumber(tokenPrefix.trim().toUpperCase() + "000");
        }
        if (branchId != null) {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
            if (!branch.getOrganization().getId().equals(organizationId)) {
                throw new TenantViolationException("Branch does not belong to your organization");
            }
            queue.setBranchId(branchId);
        }
        if (serviceId != null) {
            com.smartqueue.service.domain.Service service = serviceRepository.findById(serviceId);
            if (service == null) {
                throw new IllegalArgumentException("Service not found");
            }
            if (!service.getOrganization().getId().equals(organizationId)) {
                throw new TenantViolationException("Service does not belong to your organization");
            }
            queue.setServiceId(serviceId);
        }
        if (capacity != null && capacity >= 0) {
            queue.setCapacity(capacity);
        }
        if (isPriorityEnabled != null) {
            queue.setIsPriorityEnabled(isPriorityEnabled);
        }
        if (defaultServiceTimeMinutes != null && defaultServiceTimeMinutes > 0) {
            queue.setDefaultServiceTimeMinutes(defaultServiceTimeMinutes);
        }
        if (requiresDocumentVerification != null) {
            queue.setRequiresDocumentVerification(requiresDocumentVerification);
        }

        var saved = queueRepository.save(queue);
        log.info("QUEUE_CREATED: queueId={}, orgId={}, name={}", saved.getId(), organizationId, name);
        publishQueueEvent(saved, "QUEUE_CREATED", null);
        return saved;
    }

    /** Open a queue (set status to ACTIVE) */
    @Transactional(propagation = Propagation.REQUIRED)
    public Queue openQueue(UUID queueId) {
        var queue = findQueueById(queueId);
        queue.setStatus(QueueStatus.ACTIVE);
        var saved = queueRepository.save(queue);

        log.info("QUEUE_OPENED: queueId={}", queueId);
        publishQueueEvent(saved, "QUEUE_OPENED", null);
        return saved;
    }

    /** Close a queue (set status to CLOSED) */
    @Transactional(propagation = Propagation.REQUIRED)
    public Queue closeQueue(UUID queueId) {
        var queue = findQueueById(queueId);
        queue.setStatus(QueueStatus.CLOSED);
        var saved = queueRepository.save(queue);

        log.info("QUEUE_CLOSED: queueId={}", queueId);
        publishQueueEvent(saved, "QUEUE_CLOSED", null);
        return saved;
    }

    /** Delete / remove a queue */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteQueue(UUID queueId) {
        var queue = findQueueById(queueId);
        // 1. Delete counter assignments for this queue
        counterQueueAssignmentRepository.deleteByQueueId(queueId);
        // 2. Delete tokens associated with this queue
        queueTokenRepository.deleteByQueueId(queueId);
        // 3. Delete queue entity
        queueRepository.deleteById(queueId);
        log.info("QUEUE_DELETED: queueId={}", queueId);
        publishQueueEvent(queue, "QUEUE_DELETED", null);
    }

    /** Update queue details */
    @Transactional(propagation = Propagation.REQUIRED)
    public Queue updateQueue(UUID queueId, String name, String description) {
        return updateQueue(queueId, name, description, null, null, null, null, null, null);
    }

    /** Update queue details with full Phase 4 properties */
    @Transactional(propagation = Propagation.REQUIRED)
    public Queue updateQueue(
            UUID queueId,
            String name,
            String description,
            QueueStatus status,
            UUID branchId,
            UUID serviceId,
            Integer capacity,
            Boolean isPriorityEnabled,
            Integer defaultServiceTimeMinutes) {
        return updateQueue(queueId, name, description, status, branchId, serviceId, capacity, isPriorityEnabled, defaultServiceTimeMinutes, null);
    }

    /** Update queue details with full properties including document verification */
    @Transactional(propagation = Propagation.REQUIRED)
    public Queue updateQueue(
            UUID queueId,
            String name,
            String description,
            QueueStatus status,
            UUID branchId,
            UUID serviceId,
            Integer capacity,
            Boolean isPriorityEnabled,
            Integer defaultServiceTimeMinutes,
            Boolean requiresDocumentVerification) {
        var queue = findQueueById(queueId);
        if (name != null && !name.isBlank()) {
            queue.setName(name.trim());
        }
        if (description != null) {
            queue.setDescription(description.trim());
        }
        if (status != null) {
            queue.setStatus(status);
        }
        if (branchId != null) {
            Branch branch = branchRepository.findById(branchId)
                    .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
            if (!branch.getOrganization().getId().equals(queue.getOrganizationId())) {
                throw new TenantViolationException("Branch does not belong to your organization");
            }
            queue.setBranchId(branchId);
        }
        if (serviceId != null) {
            com.smartqueue.service.domain.Service service = serviceRepository.findById(serviceId);
            if (service == null) {
                throw new IllegalArgumentException("Service not found");
            }
            if (!service.getOrganization().getId().equals(queue.getOrganizationId())) {
                throw new TenantViolationException("Service does not belong to your organization");
            }
            queue.setServiceId(serviceId);
        }
        if (capacity != null && capacity >= 0) {
            queue.setCapacity(capacity);
        }
        if (isPriorityEnabled != null) {
            queue.setIsPriorityEnabled(isPriorityEnabled);
        }
        if (defaultServiceTimeMinutes != null && defaultServiceTimeMinutes > 0) {
            queue.setDefaultServiceTimeMinutes(defaultServiceTimeMinutes);
        }
        if (requiresDocumentVerification != null) {
            queue.setRequiresDocumentVerification(requiresDocumentVerification);
        }

        var saved = queueRepository.save(queue);
        log.info("QUEUE_UPDATED: queueId={}", queueId);
        publishQueueEvent(saved, "QUEUE_UPDATED", null);
        return saved;
    }

    /** Find all queues for the current tenant organization */
    public List<Queue> findByOrganizationId(UUID orgId) {
        return queueRepository.findByOrganizationId(orgId);
    }

    /** Find all queues with a specific status */
    public List<Queue> findByStatus(QueueStatus status) {
        return queueRepository.findByStatus(status);
    }

    /** Find a queue by ID with tenant isolation */
    public Queue findQueueById(UUID queueId) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        var queue = queueRepository.findById(queueId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found");
        }
        if (!queue.getOrganizationId().equals(orgId)) {
            throw new QueueAccessDeniedException("Access denied to this queue");
        }
        return queue;
    }

    /** Find a queue by ID without tenant isolation check */
    public Queue findQueueByIdWithoutTenant(UUID queueId) {
        var queue = queueRepository.findById(queueId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found");
        }
        if (queue.getStatus() != QueueStatus.ACTIVE) {
            throw new QueueInactiveException("Cannot generate token for inactive queue");
        }
        return queue;
    }

    /** Generate token with default priority */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public QueueToken generateToken(UUID queueId, User customer) {
        return generateToken(queueId, customer, TokenPriority.NORMAL);
    }

    /**
     * Generate a new token for an ACTIVE queue with pessimistic concurrency protection.
     * Prevents duplicate token numbers, enforces capacity limits, and records token priority.
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public QueueToken generateToken(UUID queueId, User customer, TokenPriority priority) {
        // 1. Lock Queue row for exclusive sequential token number increment and capacity enforcement
        var queue = queueRepository.findByIdWithLock(queueId);
        if (queue == null) {
            throw new QueueNotFoundException("Queue not found");
        }

        UUID orgId = TenantContext.getCurrentTenant();
        if (orgId != null) {
            List<UserRole> roles = userRoleRepository.findByUserId(customer.getId());
            boolean isStaffOrAdmin = roles.stream().anyMatch(ur ->
                    ur.getRole() != null && ("ORGANIZATION_ADMIN".equals(ur.getRole().getName()) || "STAFF".equals(ur.getRole().getName())));
            if (isStaffOrAdmin && !queue.getOrganizationId().equals(orgId)) {
                throw new QueueAccessDeniedException("Access denied to this queue");
            }
        }

        if (TenantContext.getCurrentTenant() == null) {
            TenantContext.setCurrentTenant(queue.getOrganizationId());
        }

        if (queue.getStatus() != QueueStatus.ACTIVE) {
            throw new QueueInactiveException("Cannot generate token for inactive queue");
        }

        // 2. Enforce capacity limit atomically
        if (queue.getCapacity() != null && queue.getCapacity() > 0) {
            long currentWaiting = queueTokenRepository.countWaitingByQueueId(queue.getId());
            if (currentWaiting >= queue.getCapacity()) {
                throw new QueueCapacityExceededException("Queue capacity limit (" + queue.getCapacity() + ") reached");
            }
        }

        // 3. Enforce duplicate active token prevention
        var existingActiveToken = queueTokenRepository.findActiveTokenByCustomerIdAndQueueId(customer.getId(), queueId);
        if (existingActiveToken.isPresent()) {
            throw new DuplicateActiveTokenException(
                    "Customer already has an active token (" + existingActiveToken.get().getTokenNumber() + ") in this queue"
            );
        }

        // 4. Atomically increment token number
        String nextToken = incrementTokenNumber(queue.getCurrentTokenNumber(), queue.getTokenPrefix());
        queue.setCurrentTokenNumber(nextToken);
        queueRepository.save(queue);

        // 5. Create and persist token
        var token = new QueueToken(queue, nextToken, customer);
        token.setPriority(priority != null ? priority : TokenPriority.NORMAL);
        token = queueTokenRepository.save(token);

        log.info("TOKEN_JOIN: queueId={}, customerId={}, tokenNumber={}, priority={}",
                queueId, customer.getId(), nextToken, token.getPriority());
        publishQueueEvent(queue, "TOKEN_CREATED", token);
        return token;
    }

    /** Increment token number sequentially (e.g. A000 -> A001 -> A002) */
    private String incrementTokenNumber(String current, String defaultPrefix) {
        if (current == null || current.isBlank()) {
            return (defaultPrefix != null ? defaultPrefix : "A") + "001";
        }
        String prefix = current.replaceAll("\\d+$", "");
        if (prefix.isEmpty()) {
            prefix = (defaultPrefix != null && !defaultPrefix.isBlank()) ? defaultPrefix : "A";
        }
        String numberPart = current.replaceAll("\\D+", "");
        long num = numberPart.isEmpty() ? 0 : Long.parseLong(numberPart);
        return prefix + String.format("%03d", num + 1);
    }

    /** Get all tokens for a queue with tenant isolation */
    public List<QueueToken> findTokensByQueueId(UUID queueId) {
        findQueueById(queueId); // Verify access
        return queueTokenRepository.findByQueueId(queueId);
    }

    /** Find a token by ID */
    public QueueToken findTokenById(UUID tokenId) {
        var token = queueTokenRepository.findById(tokenId);
        if (token == null) {
            throw new TokenNotFoundException("Token not found");
        }
        return token;
    }

    /** Get waiting tokens count for a queue */
    public long countWaitingTokens(UUID queueId) {
        findQueueById(queueId);
        return queueTokenRepository.countWaitingByQueueId(queueId);
    }

    /** Call next without counter */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public QueueToken callNext(UUID queueId) {
        return callNext(queueId, null, null);
    }

    /**
     * Call the next WAITING token using pessimistic row lock for concurrency protection.
     * Prevents race conditions between concurrent operators and multiple counters.
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public QueueToken callNext(UUID queueId, UUID counterId, User staffUser) {
        return callNext(queueId, counterId, staffUser, false);
    }

    /** Call next customer in queue with optional force-call override for document gatekeeping */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public QueueToken callNext(UUID queueId, UUID counterId, User staffUser, boolean forceCall) {
        var queue = findQueueById(queueId);

        if (staffUser != null) {
            validateStaffUser(staffUser, queue.getOrganizationId());
            boolean isAdmin = isOrgAdminOrSuperAdmin(staffUser, queue.getOrganizationId());

            if (!isAdmin) {
                // STAFF role: must be assigned to counter and counter assigned to queue
                var activeAssignment = staffCounterAssignmentRepository.findActiveByUserId(staffUser.getId());
                if (counterId != null) {
                    if (activeAssignment.isEmpty() || !activeAssignment.get().getCounter().getId().equals(counterId)) {
                        throw new QueueAccessDeniedException("Staff member is not assigned to this counter");
                    }
                } else {
                    if (activeAssignment.isEmpty()) {
                        throw new QueueAccessDeniedException("Staff member is not assigned to any counter");
                    }
                    counterId = activeAssignment.get().getCounter().getId();
                }

                if (!counterQueueAssignmentRepository.existsByCounterIdAndQueueId(counterId, queueId)) {
                    throw new QueueAccessDeniedException("Counter is not assigned to this queue");
                }
            }
        }

        if (counterId != null) {
            Counter counter = counterRepository.findById(counterId)
                    .orElseThrow(() -> new IllegalArgumentException("Counter not found"));
            if (!counter.getOrganization().getId().equals(queue.getOrganizationId())) {
                throw new TenantViolationException("Counter does not belong to queue's organization");
            }
            if (!"ACTIVE".equalsIgnoreCase(counter.getStatus())) {
                throw new QueueAccessDeniedException("Counter is inactive");
            }
            if (queue.getBranchId() != null && !queue.getBranchId().equals(counter.getBranch().getId())) {
                throw new IllegalArgumentException("Counter belongs to a different branch than this queue");
            }
        }

        var nextTokenOpt = queueTokenRepository.findFirstWaitingTokenByQueueIdWithLock(
                queueId, Boolean.TRUE.equals(queue.getIsPriorityEnabled()));

        if (nextTokenOpt.isEmpty()) {
            throw new EmptyQueueException("No waiting customers in queue");
        }

        var token = nextTokenOpt.get();

        // Document Verification Gatekeeping
        boolean queueRequires = Boolean.TRUE.equals(queue.getRequiresDocumentVerification());
        boolean serviceRequires = false;
        if (queue.getServiceId() != null) {
            var service = serviceRepository.findById(queue.getServiceId());
            if (service != null && Boolean.TRUE.equals(service.getRequiresDocumentVerification())) {
                serviceRequires = true;
            }
        }

        if ((queueRequires || serviceRequires) && !forceCall) {
            long verifiedDocs = tokenDocumentRepository.countByQueueTokenIdAndVerificationStatusIn(
                    token.getId(),
                    List.of(DocumentVerificationStatus.VERIFIED, DocumentVerificationStatus.MANUALLY_APPROVED)
            );
            if (verifiedDocs == 0) {
                log.warn("QUEUE_DOC_GATEKEEP: Token {} cannot proceed to service without verified documents (queueId={})",
                        token.getTokenNumber(), queueId);
                throw new DocumentVerificationRequiredException(
                        "Token " + token.getTokenNumber() + " cannot proceed: AI Document Verification is required before serving. Please verify documents or proceed with staff override."
                );
            }
        }

        token.setStatus(TokenStatus.CALLED);
        token.setCalledAt(ZonedDateTime.now());
        if (counterId != null) {
            token.setServedByCounterId(counterId);
        }
        if (staffUser != null) {
            token.setServedByUserId(staffUser.getId());
        }
        token = queueTokenRepository.save(token);

        log.info("QUEUE_NEXT: queueId={}, tokenId={}, tokenNumber={}, counterId={}",
                queueId, token.getId(), token.getTokenNumber(), counterId);
        publishQueueEvent(queue, "TOKEN_CALLED", token);
        return token;
    }

    /**
     * Call next token across all queues assigned to a counter.
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public QueueToken callNextByCounter(UUID counterId, User staffUser) {
        return callNextByCounter(counterId, staffUser, false);
    }

    /**
     * Call next token across all queues assigned to a counter with optional forceCall override.
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public QueueToken callNextByCounter(UUID counterId, User staffUser, boolean forceCall) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        Counter counter = counterRepository.findById(counterId)
                .orElseThrow(() -> new IllegalArgumentException("Counter not found"));

        if (!counter.getOrganization().getId().equals(orgId)) {
            throw new TenantViolationException("Counter does not belong to your organization");
        }

        if (!"ACTIVE".equalsIgnoreCase(counter.getStatus())) {
            throw new QueueAccessDeniedException("Counter is inactive");
        }

        if (staffUser != null) {
            validateStaffUser(staffUser, orgId);
            boolean isAdmin = isOrgAdminOrSuperAdmin(staffUser, orgId);
            if (!isAdmin) {
                var activeAssignment = staffCounterAssignmentRepository.findActiveByUserId(staffUser.getId());
                if (activeAssignment.isEmpty() || !activeAssignment.get().getCounter().getId().equals(counterId)) {
                    throw new QueueAccessDeniedException("Staff member is not assigned to this counter");
                }
            }
        }

        List<CounterQueueAssignment> assignments = counterQueueAssignmentRepository.findByCounterId(counterId);
        if (assignments.isEmpty()) {
            throw new EmptyQueueException("No queues assigned to this counter");
        }

        QueueToken chosenToken = null;
        Queue chosenQueue = null;

        for (CounterQueueAssignment cqa : assignments) {
            Queue queue = cqa.getQueue();
            if (queue.getStatus() != QueueStatus.ACTIVE) {
                continue;
            }

            Optional<QueueToken> candidate = queueTokenRepository.findFirstWaitingTokenByQueueIdWithLock(
                    queue.getId(), Boolean.TRUE.equals(queue.getIsPriorityEnabled()));

            if (candidate.isPresent()) {
                QueueToken token = candidate.get();
                if (chosenToken == null) {
                    chosenToken = token;
                    chosenQueue = queue;
                } else {
                    // Compare priority first
                    int chosenRank = chosenToken.getPriority() != null ? chosenToken.getPriority().getRank() : TokenPriority.NORMAL.getRank();
                    int candidateRank = token.getPriority() != null ? token.getPriority().getRank() : TokenPriority.NORMAL.getRank();

                    if (candidateRank < chosenRank || (candidateRank == chosenRank && token.getCreatedAt().isBefore(chosenToken.getCreatedAt()))) {
                        chosenToken = token;
                        chosenQueue = queue;
                    }
                }
            }
        }

        if (chosenToken == null || chosenQueue == null) {
            throw new EmptyQueueException("No waiting customers in any assigned queues");
        }

        // Document Verification Gatekeeping
        boolean queueRequires = Boolean.TRUE.equals(chosenQueue.getRequiresDocumentVerification());
        boolean serviceRequires = false;
        if (chosenQueue.getServiceId() != null) {
            var service = serviceRepository.findById(chosenQueue.getServiceId());
            if (service != null && Boolean.TRUE.equals(service.getRequiresDocumentVerification())) {
                serviceRequires = true;
            }
        }

        if ((queueRequires || serviceRequires) && !forceCall) {
            long verifiedDocs = tokenDocumentRepository.countByQueueTokenIdAndVerificationStatusIn(
                    chosenToken.getId(),
                    List.of(DocumentVerificationStatus.VERIFIED, DocumentVerificationStatus.MANUALLY_APPROVED)
            );
            if (verifiedDocs == 0) {
                log.warn("COUNTER_DOC_GATEKEEP: Token {} cannot proceed to service without verified documents (queueId={}, counterId={})",
                        chosenToken.getTokenNumber(), chosenQueue.getId(), counterId);
                throw new DocumentVerificationRequiredException(
                        "Token " + chosenToken.getTokenNumber() + " cannot proceed: AI Document Verification is required before serving. Please verify documents or proceed with staff override."
                );
            }
        }

        chosenToken.setStatus(TokenStatus.CALLED);
        chosenToken.setCalledAt(ZonedDateTime.now());
        chosenToken.setServedByCounterId(counterId);
        if (staffUser != null) {
            chosenToken.setServedByUserId(staffUser.getId());
        }
        chosenToken = queueTokenRepository.save(chosenToken);

        log.info("COUNTER_NEXT: counterId={}, queueId={}, tokenId={}, tokenNumber={}",
                counterId, chosenQueue.getId(), chosenToken.getId(), chosenToken.getTokenNumber());
        publishQueueEvent(chosenQueue, "TOKEN_CALLED", chosenToken);
        return chosenToken;
    }

    /** Hold a CALLED or WAITING token */
    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken holdToken(UUID tokenId) {
        return holdToken(tokenId, null, true);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken holdToken(UUID tokenId, User operator) {
        return holdToken(tokenId, operator, true);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken holdToken(UUID tokenId, User user, boolean isAdminOrStaff) {
        var token = queueTokenRepository.findByIdWithLock(tokenId);
        if (token == null) {
            throw new TokenNotFoundException("Token not found");
        }
        verifyQueueAccess(token.getQueue().getId());
        if (isAdminOrStaff) {
            if (user != null) {
                validateStaffUser(user, token.getQueue().getOrganizationId());
            }
        } else {
            if (user == null || token.getCustomer() == null || !token.getCustomer().getId().equals(user.getId())) {
                throw new QueueAccessDeniedException("You can only hold your own token");
            }
        }

        // Transition: CALLED or WAITING -> HOLD
        if (token.getStatus() != TokenStatus.CALLED && token.getStatus() != TokenStatus.WAITING) {
            throw new InvalidTokenTransitionException("Cannot hold token with status: " + token.getStatus());
        }

        token.setStatus(TokenStatus.HOLD);
        token.setHeldAt(ZonedDateTime.now());
        token = queueTokenRepository.save(token);

        log.info("QUEUE_HOLD: tokenId={}, tokenNumber={}", tokenId, token.getTokenNumber());
        publishQueueEvent(token.getQueue(), "TOKEN_HELD", token);
        return token;
    }

    /** Resume a HOLD token back to WAITING */
    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken resumeToken(UUID tokenId) {
        return resumeToken(tokenId, null, true);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken resumeToken(UUID tokenId, User operator) {
        return resumeToken(tokenId, operator, true);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken resumeToken(UUID tokenId, User user, boolean isAdminOrStaff) {
        var token = queueTokenRepository.findByIdWithLock(tokenId);
        if (token == null) {
            throw new TokenNotFoundException("Token not found");
        }
        verifyQueueAccess(token.getQueue().getId());
        if (isAdminOrStaff) {
            if (user != null) {
                validateStaffUser(user, token.getQueue().getOrganizationId());
            }
        } else {
            if (user == null || token.getCustomer() == null || !token.getCustomer().getId().equals(user.getId())) {
                throw new QueueAccessDeniedException("You can only resume your own token");
            }
        }

        // Transition: HOLD -> WAITING
        if (token.getStatus() != TokenStatus.HOLD) {
            throw new InvalidTokenTransitionException("Cannot resume token with status: " + token.getStatus());
        }

        token.setStatus(TokenStatus.WAITING);
        token.setHeldAt(null);
        token = queueTokenRepository.save(token);

        log.info("QUEUE_RESUME: tokenId={}, tokenNumber={}", tokenId, token.getTokenNumber());
        publishQueueEvent(token.getQueue(), "TOKEN_RESUMED", token);
        return token;
    }

    /** Skip a token (from WAITING, CALLED, or HOLD) */
    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken skipToken(UUID tokenId) {
        return skipToken(tokenId, null);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken skipToken(UUID tokenId, User operator) {
        var token = queueTokenRepository.findByIdWithLock(tokenId);
        if (token == null) {
            throw new TokenNotFoundException("Token not found");
        }
        verifyQueueAccess(token.getQueue().getId());
        if (operator != null) {
            validateStaffUser(operator, token.getQueue().getOrganizationId());
        }

        if (token.getStatus() != TokenStatus.WAITING &&
                token.getStatus() != TokenStatus.CALLED &&
                token.getStatus() != TokenStatus.HOLD) {
            throw new InvalidTokenTransitionException("Cannot skip token with status: " + token.getStatus());
        }

        token.setStatus(TokenStatus.SKIPPED);
        token.setSkippedAt(ZonedDateTime.now());
        token = queueTokenRepository.save(token);

        log.info("QUEUE_SKIP: tokenId={}, tokenNumber={}", tokenId, token.getTokenNumber());
        publishQueueEvent(token.getQueue(), "TOKEN_SKIPPED", token);
        return token;
    }

    /** Complete a CALLED token */
    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken completeToken(UUID tokenId) {
        return completeToken(tokenId, null);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken completeToken(UUID tokenId, User operator) {
        var token = queueTokenRepository.findByIdWithLock(tokenId);
        if (token == null) {
            throw new TokenNotFoundException("Token not found");
        }
        verifyQueueAccess(token.getQueue().getId());
        if (operator != null) {
            validateStaffUser(operator, token.getQueue().getOrganizationId());
        }

        // Transition: CALLED -> COMPLETED
        if (token.getStatus() != TokenStatus.CALLED) {
            throw new InvalidTokenTransitionException("Cannot complete token with status: " + token.getStatus());
        }

        token.setStatus(TokenStatus.COMPLETED);
        token.setCompletedAt(ZonedDateTime.now());
        token = queueTokenRepository.save(token);

        log.info("QUEUE_COMPLETE: tokenId={}, tokenNumber={}", tokenId, token.getTokenNumber());
        publishQueueEvent(token.getQueue(), "TOKEN_COMPLETED", token);
        return token;
    }

    private void validateStaffUser(User staffUser, UUID organizationId) {
        if (staffUser.getStatus() != null && !"ACTIVE".equalsIgnoreCase(staffUser.getStatus())) {
            throw new QueueAccessDeniedException("Staff member is inactive");
        }
        List<UserRole> roles = userRoleRepository.findByUserId(staffUser.getId());
        boolean hasOrgRole = roles.stream().anyMatch(ur ->
                (ur.getOrganization() != null && ur.getOrganization().getId().equals(organizationId)) ||
                (ur.getRole() != null && "SUPER_ADMIN".equals(ur.getRole().getCode())));
        if (!hasOrgRole) {
            throw new TenantViolationException("Staff user does not belong to this organization");
        }
    }

    private boolean isOrgAdminOrSuperAdmin(User user, UUID organizationId) {
        List<UserRole> roles = userRoleRepository.findByUserId(user.getId());
        return roles.stream().anyMatch(ur -> {
            String roleCode = ur.getRole() != null ? ur.getRole().getCode() : "";
            if ("SUPER_ADMIN".equals(roleCode)) return true;
            return "ORGANIZATION_ADMIN".equals(roleCode) &&
                    ur.getOrganization() != null &&
                    ur.getOrganization().getId().equals(organizationId);
        });
    }

    /** Customer leaves active queue */
    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken leaveToken(UUID tokenId, User customer) {
        return cancelTokenInternal(tokenId, customer, false);
    }

    /** Cancel a token (admin or customer owner) */
    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken cancelToken(UUID tokenId, User user, boolean isAdmin) {
        return cancelTokenInternal(tokenId, user, isAdmin);
    }

    private QueueToken cancelTokenInternal(UUID tokenId, User user, boolean isAdmin) {
        var token = queueTokenRepository.findByIdWithLock(tokenId);
        if (token == null) {
            throw new TokenNotFoundException("Token not found");
        }
        verifyQueueAccess(token.getQueue().getId());

        // If not admin, enforce ownership check
        if (!isAdmin && (token.getCustomer() == null || !token.getCustomer().getId().equals(user.getId()))) {
            throw new QueueAccessDeniedException("You can only cancel/leave your own token");
        }

        // Only active tokens can be cancelled/left
        if (token.getStatus() != TokenStatus.WAITING &&
                token.getStatus() != TokenStatus.CALLED &&
                token.getStatus() != TokenStatus.HOLD) {
            throw new InvalidTokenTransitionException("Cannot cancel token with status: " + token.getStatus());
        }

        token.setStatus(TokenStatus.CANCELLED);
        token.setCancelledAt(ZonedDateTime.now());
        token = queueTokenRepository.save(token);

        log.info("QUEUE_CANCEL: tokenId={}, tokenNumber={}", tokenId, token.getTokenNumber());
        publishQueueEvent(token.getQueue(), "TOKEN_CANCELLED", token);
        return token;
    }

    /** Update travel time for a token */
    @Transactional(propagation = Propagation.REQUIRED)
    public QueueToken updateTravelTime(UUID tokenId, Integer travelTimeMinutes, User customer) {
        var token = queueTokenRepository.findByIdWithLock(tokenId);
        if (token == null) {
            throw new TokenNotFoundException("Token not found");
        }
        verifyQueueAccess(token.getQueue().getId());
        if (customer == null || token.getCustomer() == null || !token.getCustomer().getId().equals(customer.getId())) {
            throw new QueueAccessDeniedException("You can only update travel time for your own token");
        }

        token.setExpectedTravelTimeMinutes(travelTimeMinutes);
        return queueTokenRepository.save(token);
    }

    /** Deterministic estimate waiting time/position for a token */
    public QueueStatusEstimate getEstimate(UUID tokenId) {
        var token = queueTokenRepository.findById(tokenId);
        if (token == null) {
            throw new TokenNotFoundException("Token not found");
        }

        var queue = token.getQueue();

        long tokensAhead = 0;
        int position = 0;

        if (token.getStatus() == TokenStatus.WAITING) {
            boolean isPriorityEnabled = Boolean.TRUE.equals(queue.getIsPriorityEnabled());
            tokensAhead = queueTokenRepository.countTokensAhead(
                    queue.getId(),
                    token.getId(),
                    token.getPriority(),
                    token.getCreatedAt(),
                    isPriorityEnabled
            );
            position = (int) tokensAhead + 1;
        } else if (token.getStatus() == TokenStatus.CALLED || token.getStatus() == TokenStatus.HOLD) {
            position = 1;
            tokensAhead = 0;
        }

        int serviceTime = queue.getDefaultServiceTimeMinutes() != null ? queue.getDefaultServiceTimeMinutes() : 5;
        int estimatedWaitMinutes = (int) (tokensAhead * (long) serviceTime);

        ZonedDateTime expectedTurnTime = null;
        ZonedDateTime recommendedDepartureTime = null;

        if (token.getStatus() == TokenStatus.WAITING) {
            expectedTurnTime = ZonedDateTime.now().plusMinutes(estimatedWaitMinutes);

            if (token.getExpectedTravelTimeMinutes() != null) {
                // Buffer of 5 minutes
                int bufferMinutes = 5;
                recommendedDepartureTime = expectedTurnTime
                        .minusMinutes(token.getExpectedTravelTimeMinutes())
                        .minusMinutes(bufferMinutes);
            }
        }

        return new QueueStatusEstimate(position, estimatedWaitMinutes, expectedTurnTime, recommendedDepartureTime, token.getExpectedTravelTimeMinutes());
    }

    private void verifyQueueAccess(UUID queueId) {
        findQueueById(queueId);
    }

    private void publishQueueEvent(Queue queue, String eventType, QueueToken token) {
        long waitingCount = queueTokenRepository.countWaitingByQueueId(queue.getId());

        QueueEvent event = QueueEvent.create(
                queue.getId(),
                queue.getOrganizationId(),
                eventType,
                queue.getStatus(),
                queue.getCurrentTokenNumber(),
                waitingCount,
                token != null ? token.getId() : null,
                token != null ? token.getTokenNumber() : null,
                token != null ? token.getStatus() : null
        );

        eventPublisher.publishEvent(event);
    }

    /** Queue status estimate DTO */
    public record QueueStatusEstimate(
            int position,
            int estimatedWaitMinutes,
            ZonedDateTime expectedTurnTime,
            ZonedDateTime recommendedDepartureTime,
            Integer expectedTravelTimeMinutes
    ) {
        public QueueStatusEstimate(int position, int estimatedWaitMinutes, ZonedDateTime expectedTurnTime) {
            this(position, estimatedWaitMinutes, expectedTurnTime, null, null);
        }
    }
}