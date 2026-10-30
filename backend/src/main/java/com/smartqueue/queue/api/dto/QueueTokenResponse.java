package com.smartqueue.queue.api.dto;

import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenPriority;
import com.smartqueue.queue.domain.TokenStatus;
import java.time.ZonedDateTime;
import java.util.UUID;

public class QueueTokenResponse {
    private UUID id;
    private UUID queueId;
    private String queueName;
    private UUID organizationId;
    private UUID branchId;
    private UUID serviceId;
    private UUID customerId;
    private String tokenNumber;
    private TokenStatus status;
    private Integer position;
    private Long peopleAhead;
    private Integer estimatedWaitMinutes;
    private ZonedDateTime calledAt;
    private ZonedDateTime completedAt;
    private ZonedDateTime heldAt;
    private ZonedDateTime skippedAt;
    private ZonedDateTime cancelledAt;
    private TokenPriority priority;
    private UUID servedByCounterId;
    private UUID servedByUserId;
    private Integer expectedTravelTimeMinutes;
    private ZonedDateTime expectedTurnTime;
    private ZonedDateTime recommendedDepartureTime;
    private Boolean requiresDocumentVerification = false;
    private String documentVerificationStatus = "NOT_REQUIRED";
    private Integer documentCount = 0;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static QueueTokenResponse fromEntity(QueueToken token) {
        return fromEntity(token, null, null, null, null, null);
    }

    public static QueueTokenResponse fromEntity(QueueToken token, Integer position, Long peopleAhead, Integer estimatedWaitMinutes, ZonedDateTime expectedTurnTime, ZonedDateTime recommendedDepartureTime) {
        QueueTokenResponse dto = new QueueTokenResponse();
        dto.id = token.getId();
        if (token.getQueue() != null) {
            dto.queueId = token.getQueue().getId();
            dto.queueName = token.getQueue().getName();
            dto.organizationId = token.getQueue().getOrganizationId();
            dto.branchId = token.getQueue().getBranchId();
            dto.serviceId = token.getQueue().getServiceId();
            dto.requiresDocumentVerification = token.getQueue().getRequiresDocumentVerification();
        }
        if (token.getCustomer() != null) {
            dto.customerId = token.getCustomer().getId();
        }
        dto.tokenNumber = token.getTokenNumber();
        dto.status = token.getStatus();
        dto.priority = token.getPriority();
        dto.servedByCounterId = token.getServedByCounterId();
        dto.servedByUserId = token.getServedByUserId();
        dto.position = position;
        dto.peopleAhead = peopleAhead;
        dto.estimatedWaitMinutes = estimatedWaitMinutes;
        dto.calledAt = token.getCalledAt();
        dto.completedAt = token.getCompletedAt();
        dto.heldAt = token.getHeldAt();
        dto.skippedAt = token.getSkippedAt();
        dto.cancelledAt = token.getCancelledAt();
        dto.expectedTravelTimeMinutes = token.getExpectedTravelTimeMinutes();
        dto.expectedTurnTime = expectedTurnTime;
        dto.recommendedDepartureTime = recommendedDepartureTime;
        dto.createdAt = token.getCreatedAt();
        dto.updatedAt = token.getUpdatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getQueueId() { return queueId; }
    public void setQueueId(UUID queueId) { this.queueId = queueId; }

    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }

    public UUID getServiceId() { return serviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public Long getPeopleAhead() { return peopleAhead; }
    public void setPeopleAhead(Long peopleAhead) { this.peopleAhead = peopleAhead; }

    public Integer getEstimatedWaitMinutes() { return estimatedWaitMinutes; }
    public void setEstimatedWaitMinutes(Integer estimatedWaitMinutes) { this.estimatedWaitMinutes = estimatedWaitMinutes; }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getTokenNumber() {
        return tokenNumber;
    }

    public void setTokenNumber(String tokenNumber) {
        this.tokenNumber = tokenNumber;
    }

    public TokenStatus getStatus() {
        return status;
    }

    public void setStatus(TokenStatus status) {
        this.status = status;
    }

    public ZonedDateTime getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(ZonedDateTime calledAt) {
        this.calledAt = calledAt;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public ZonedDateTime getHeldAt() {
        return heldAt;
    }

    public void setHeldAt(ZonedDateTime heldAt) {
        this.heldAt = heldAt;
    }

    public ZonedDateTime getSkippedAt() {
        return skippedAt;
    }

    public void setSkippedAt(ZonedDateTime skippedAt) {
        this.skippedAt = skippedAt;
    }

    public ZonedDateTime getCancelledAt() {
        return cancelledAt;
    }

    public TokenPriority getPriority() {
        return priority;
    }

    public void setPriority(TokenPriority priority) {
        this.priority = priority;
    }

    public UUID getServedByCounterId() {
        return servedByCounterId;
    }

    public void setServedByCounterId(UUID servedByCounterId) {
        this.servedByCounterId = servedByCounterId;
    }

    public UUID getServedByUserId() {
        return servedByUserId;
    }

    public void setServedByUserId(UUID servedByUserId) {
        this.servedByUserId = servedByUserId;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Integer getExpectedTravelTimeMinutes() {
        return expectedTravelTimeMinutes;
    }

    public void setExpectedTravelTimeMinutes(Integer expectedTravelTimeMinutes) {
        this.expectedTravelTimeMinutes = expectedTravelTimeMinutes;
    }

    public ZonedDateTime getExpectedTurnTime() {
        return expectedTurnTime;
    }

    public void setExpectedTurnTime(ZonedDateTime expectedTurnTime) {
        this.expectedTurnTime = expectedTurnTime;
    }

    public ZonedDateTime getRecommendedDepartureTime() {
        return recommendedDepartureTime;
    }

    public void setRecommendedDepartureTime(ZonedDateTime recommendedDepartureTime) {
        this.recommendedDepartureTime = recommendedDepartureTime;
    }

    public Boolean getRequiresDocumentVerification() {
        return requiresDocumentVerification != null ? requiresDocumentVerification : false;
    }

    public void setRequiresDocumentVerification(Boolean requiresDocumentVerification) {
        this.requiresDocumentVerification = requiresDocumentVerification;
    }

    public String getDocumentVerificationStatus() {
        return documentVerificationStatus != null ? documentVerificationStatus : "NOT_REQUIRED";
    }

    public void setDocumentVerificationStatus(String documentVerificationStatus) {
        this.documentVerificationStatus = documentVerificationStatus;
    }

    public Integer getDocumentCount() {
        return documentCount != null ? documentCount : 0;
    }

    public void setDocumentCount(Integer documentCount) {
        this.documentCount = documentCount;
    }
}