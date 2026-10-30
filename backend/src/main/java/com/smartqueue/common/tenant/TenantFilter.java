package com.smartqueue.common.tenant;

import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.common.tenant.TenantViolationException;
import com.smartqueue.auth.domain.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String tenantIdHeader = request.getHeader("X-Organization-Id");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (StringUtils.hasText(tenantIdHeader)) {
            UUID requestedOrgId;
            try {
                requestedOrgId = UUID.fromString(tenantIdHeader);
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"error\":{\"code\":\"INVALID_ORGANIZATION_ID\","
                        + "\"message\":\"X-Organization-Id header is not a valid UUID\"}}"
                );
                return;
            }

            if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"error\":{\"code\":\"ACCESS_DENIED\","
                        + "\"message\":\"Authentication required to select an organization\"}}"
                );
                return;
            }

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            // Verify the requested organization matches the authenticated user's organization
            boolean hasAccess = userDetails.getUserRoles().stream()
                    .anyMatch(ur -> ur.getOrganization() != null
                            && ur.getOrganization().getId().equals(requestedOrgId));

            if (!hasAccess) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"error\":{\"code\":\"ORGANIZATION_ACCESS_DENIED\","
                        + "\"message\":\"You do not have access to the requested organization\"}}"
                );
                return;
            }

            TenantContext.setCurrentTenant(requestedOrgId);
        } else {
            // No header — auto-resolve tenant from the user's authenticated organization-scoped role
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
                CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
                UUID organizationId = userDetails.getUserRoles().stream()
                        .map(UserRole::getOrganization)
                        .filter(org -> org != null)
                        .map(org -> org.getId())
                        .findFirst()
                        .orElse(null);
                if (organizationId != null) {
                    TenantContext.setCurrentTenant(organizationId);
                }
                // If no organization found (e.g., customer without org), tenant context remains null
                // This allows customers to discover queues across organizations
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
