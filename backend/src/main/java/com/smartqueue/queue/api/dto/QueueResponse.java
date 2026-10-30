package com.smartqueue.queue.api.dto;

import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import java.time.ZonedDateTime;
import java.util.UUID;

public class QueueResponse {
    private UUID id;
    private UUID organizationId;
    private UUID branchId;
    private UUID serviceId;
    private String name;
    private String description;
    private QueueStatus status;
    private String tokenPrefix;
    private String currentTokenNumber;
    private Integer capacity;
    private Boolean isPriorityEnabled;
    private Integer defaultServiceTimeMinutes;
    private Boolean requiresDocumentVerification;
    private long waitingCount;
    private int estimatedWaitMinutes;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static QueueResponse fromEntity(Queue queue) {
        return fromEntity(queue, 0, 0);
    }

    public static QueueResponse fromEntity(Queue queue, long waitingCount, int estimatedWaitMinutes) {
        QueueResponse dto = new QueueResponse();
        dto.id = queue.getId();
        dto.organizationId = queue.getOrganizationId();
        dto.branchId = queue.getBranchId();
        dto.serviceId = queue.getServiceId();
        dto.name = queue.getName();
        dto.description = queue.getDescription();
        dto.status = queue.getStatus();
        dto.tokenPrefix = queue.getTokenPrefix();
        dto.currentTokenNumber = queue.getCurrentTokenNumber();
        dto.capacity = queue.getCapacity();
        dto.isPriorityEnabled = queue.getIsPriorityEnabled();
        dto.defaultServiceTimeMinutes = queue.getDefaultServiceTimeMinutes();
        dto.requiresDocumentVerification = queue.getRequiresDocumentVerification();
        dto.waitingCount = waitingCount;
        dto.estimatedWaitMinutes = estimatedWaitMinutes;
        dto.createdAt = queue.getCreatedAt();
        dto.updatedAt = queue.getUpdatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }

    public UUID getServiceId() { return serviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public QueueStatus getStatus() { return status; }
    public void setStatus(QueueStatus status) { this.status = status; }

    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }

    public String getCurrentTokenNumber() { return currentTokenNumber; }
    public void setCurrentTokenNumber(String currentTokenNumber) { this.currentTokenNumber = currentTokenNumber; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Boolean getIsPriorityEnabled() { return isPriorityEnabled; }
    public void setIsPriorityEnabled(Boolean isPriorityEnabled) { this.isPriorityEnabled = isPriorityEnabled; }

    public Integer getDefaultServiceTimeMinutes() { return defaultServiceTimeMinutes; }
    public void setDefaultServiceTimeMinutes(Integer defaultServiceTimeMinutes) { this.defaultServiceTimeMinutes = defaultServiceTimeMinutes; }

    public Boolean getRequiresDocumentVerification() { return requiresDocumentVerification != null ? requiresDocumentVerification : false; }
    public void setRequiresDocumentVerification(Boolean requiresDocumentVerification) { this.requiresDocumentVerification = requiresDocumentVerification; }

    public long getWaitingCount() { return waitingCount; }
    public void setWaitingCount(long waitingCount) { this.waitingCount = waitingCount; }

    public int getEstimatedWaitMinutes() { return estimatedWaitMinutes; }
    public void setEstimatedWaitMinutes(int estimatedWaitMinutes) { this.estimatedWaitMinutes = estimatedWaitMinutes; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }
}