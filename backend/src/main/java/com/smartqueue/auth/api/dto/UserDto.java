package com.smartqueue.auth.api.dto;

import java.util.List;
import java.util.UUID;

public class UserDto {

    private UUID id;
    private String email;
    private String fullName;
    private String phone;
    private String preferredLanguage;
    private String status;
    private List<String> roles;
    private UUID organizationId;

    public UserDto() {}

    public UserDto(UUID id, String email, String fullName, String status) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.status = status;
    }

    public UserDto(UUID id, String email, String fullName, String status, List<String> roles) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.status = status;
        this.roles = roles;
    }

    public UserDto(UUID id, String email, String fullName, String status, List<String> roles, UUID organizationId) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.status = status;
        this.roles = roles;
        this.organizationId = organizationId;
    }

    public UserDto(UUID id, String email, String fullName, String phone, String preferredLanguage, String status, List<String> roles, UUID organizationId) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.preferredLanguage = preferredLanguage;
        this.status = status;
        this.roles = roles;
        this.organizationId = organizationId;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
}
