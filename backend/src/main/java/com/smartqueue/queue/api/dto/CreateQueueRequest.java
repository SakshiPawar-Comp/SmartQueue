package com.smartqueue.queue.api.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public class CreateQueueRequest {
    @NotBlank(message = "Queue name is required")
    @Size(max = 255, message = "Queue name must not exceed 255 characters")
    private String name;

    private String description;
    private String tokenPrefix;
    private UUID branchId;
    private UUID serviceId;
    private Integer capacity;
    private Boolean isPriorityEnabled;
    private Integer defaultServiceTimeMinutes;

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

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
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

    private Boolean requiresDocumentVerification;

    public Boolean getRequiresDocumentVerification() {
        return requiresDocumentVerification;
    }

    public void setRequiresDocumentVerification(Boolean requiresDocumentVerification) {
        this.requiresDocumentVerification = requiresDocumentVerification;
    }
}