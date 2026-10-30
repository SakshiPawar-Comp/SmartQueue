package com.smartqueue.staff.api.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class StaffResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String phone;
    private String preferredLanguage;
    private String status;
    private UUID organizationId;
    private UUID branchId;
    private String branchName;
    private UUID activeCounterId;
    private String activeCounterName;
    private List<UUID> assignedQueueIds;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public StaffResponse() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public UUID getActiveCounterId() {
        return activeCounterId;
    }

    public void setActiveCounterId(UUID activeCounterId) {
        this.activeCounterId = activeCounterId;
    }

    public String getActiveCounterName() {
        return activeCounterName;
    }

    public void setActiveCounterName(String activeCounterName) {
        this.activeCounterName = activeCounterName;
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
