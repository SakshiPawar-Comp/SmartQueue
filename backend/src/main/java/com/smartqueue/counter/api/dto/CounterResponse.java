package com.smartqueue.counter.api.dto;

import com.smartqueue.counter.domain.Counter;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class CounterResponse {
    private UUID id;
    private UUID organizationId;
    private UUID branchId;
    private String branchName;
    private String name;
    private String code;
    private String description;
    private String status;
    private UUID assignedStaffUserId;
    private String assignedStaffName;
    private List<UUID> assignedQueueIds;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static CounterResponse fromEntity(Counter counter) {
        return fromEntity(counter, null, null, List.of());
    }

    public static CounterResponse fromEntity(
            Counter counter,
            UUID assignedStaffUserId,
            String assignedStaffName,
            List<UUID> assignedQueueIds) {
        CounterResponse dto = new CounterResponse();
        dto.id = counter.getId();
        if (counter.getOrganization() != null) {
            dto.organizationId = counter.getOrganization().getId();
        }
        if (counter.getBranch() != null) {
            dto.branchId = counter.getBranch().getId();
            dto.branchName = counter.getBranch().getName();
        }
        dto.name = counter.getName();
        dto.code = counter.getCode();
        dto.description = counter.getDescription();
        dto.status = counter.getStatus();
        dto.assignedStaffUserId = assignedStaffUserId;
        dto.assignedStaffName = assignedStaffName;
        dto.assignedQueueIds = assignedQueueIds != null ? assignedQueueIds : List.of();
        dto.createdAt = counter.getCreatedAt();
        dto.updatedAt = counter.getUpdatedAt();
        return dto;
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

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getAssignedStaffUserId() {
        return assignedStaffUserId;
    }

    public void setAssignedStaffUserId(UUID assignedStaffUserId) {
        this.assignedStaffUserId = assignedStaffUserId;
    }

    public String getAssignedStaffName() {
        return assignedStaffName;
    }

    public void setAssignedStaffName(String assignedStaffName) {
        this.assignedStaffName = assignedStaffName;
    }

    public List<UUID> getAssignedQueueIds() {
        return assignedQueueIds;
    }

    public void setAssignedQueueIds(List<UUID> assignedQueueIds) {
        this.assignedQueueIds = assignedQueueIds;
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
