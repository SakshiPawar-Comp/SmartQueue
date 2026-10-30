package com.smartqueue.common.tenant;

import java.util.UUID;

public class TenantContext {

    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();

    public static void setCurrentTenant(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        return currentTenant.get();
    }

    public static UUID requireCurrentTenantId() {
        UUID tenant = currentTenant.get();
        if (tenant == null) {
            throw new TenantViolationException("Organization context is required for this operation");
        }
        return tenant;
    }

    public static void clear() {
        currentTenant.remove();
    }
}
