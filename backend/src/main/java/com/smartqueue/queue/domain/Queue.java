package com.smartqueue.queue.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "queues")
public class Queue {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QueueStatus status;

    @Column(name = "token_prefix", nullable = false)
    private String tokenPrefix;

    @Column(name = "current_token_number", nullable = false)
    private String currentTokenNumber;

    @Column(name = "capacity", nullable = false)
    private Integer capacity = 0;

    @Column(name = "is_priority_enabled", nullable = false)
    private Boolean isPriorityEnabled = false;

    @Column(name = "default_service_time_minutes", nullable = false)
    private Integer defaultServiceTimeMinutes = 5;

    @Column(name = "requires_document_verification")
    private Boolean requiresDocumentVerification = false;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    // Default constructor for JPA
    public Queue() {
        this.id = UUID.randomUUID();
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    public Queue(UUID organizationId, String name) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.name = name;
        this.status = QueueStatus.ACTIVE;
        this.tokenPrefix = "A";
        this.currentTokenNumber = "A000";
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public QueueStatus getStatus() {
        return status;
    }

    public void setStatus(QueueStatus status) {
        this.status = status;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public String getCurrentTokenNumber() {
        return currentTokenNumber;
    }

    public void setCurrentTokenNumber(String currentTokenNumber) {
        this.currentTokenNumber = currentTokenNumber;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Boolean getIsPriorityEnabled() {
        return isPriorityEnabled;
    }

    public void setIsPriorityEnabled(Boolean isPriorityEnabled) {
        this.isPriorityEnabled = isPriorityEnabled;
    }

    public Integer getDefaultServiceTimeMinutes() {
        return defaultServiceTimeMinutes;
    }

    public void setDefaultServiceTimeMinutes(Integer defaultServiceTimeMinutes) {
        this.defaultServiceTimeMinutes = defaultServiceTimeMinutes;
    }

    public Boolean getRequiresDocumentVerification() {
        return requiresDocumentVerification != null ? requiresDocumentVerification : false;
    }

    public void setRequiresDocumentVerification(Boolean requiresDocumentVerification) {
        this.requiresDocumentVerification = requiresDocumentVerification;
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
}