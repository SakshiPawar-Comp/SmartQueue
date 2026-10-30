package com.smartqueue.organization.api.dto;

import com.smartqueue.organization.domain.Organization;
import java.time.ZonedDateTime;
import java.util.UUID;

public class OrganizationResponse {
    private UUID id;
    private String name;
    private String slug;
    private String status;
    private ZonedDateTime createdAt;

    public static OrganizationResponse fromEntity(Organization org) {
        OrganizationResponse dto = new OrganizationResponse();
        dto.id = org.getId();
        dto.name = org.getName();
        dto.slug = org.getSlug();
        dto.status = org.getStatus();
        dto.createdAt = org.getCreatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
}
