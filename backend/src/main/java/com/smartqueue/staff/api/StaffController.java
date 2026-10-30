package com.smartqueue.staff.api;

import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.staff.api.dto.CreateStaffRequest;
import com.smartqueue.staff.api.dto.StaffResponse;
import com.smartqueue.staff.api.dto.UpdateStaffRequest;
import com.smartqueue.staff.application.StaffService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    /** GET /api/staff - List staff for organization/branch */
    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> listStaff(
            @RequestParam(required = false) UUID branchId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        List<StaffResponse> staff = staffService.listStaff(orgId, branchId);
        return ResponseEntity.ok(ApiResponse.success(staff, MDC.get("correlationId")));
    }

    /** GET /api/staff/me/assignments - Get authenticated staff user's assignments */
    @GetMapping("/me/assignments")
    @PreAuthorize("hasAnyRole('STAFF', 'ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<StaffResponse>> getMyAssignments(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        StaffResponse assignments = staffService.getStaffAssignments(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(assignments, MDC.get("correlationId")));
    }

    /** GET /api/staff/{staffId} - Get staff member details */
    @GetMapping("/{staffId}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<StaffResponse>> getStaffById(
            @PathVariable UUID staffId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        StaffResponse staff = staffService.getStaffById(orgId, staffId);
        return ResponseEntity.ok(ApiResponse.success(staff, MDC.get("correlationId")));
    }

    /** POST /api/staff - Create new staff user */
    @PostMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<StaffResponse>> createStaff(
            @Valid @RequestBody CreateStaffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        StaffResponse staff = staffService.createStaff(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(staff, MDC.get("correlationId")));
    }

    /** PUT /api/staff/{staffId} - Update staff user */
    @PutMapping("/{staffId}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaff(
            @PathVariable UUID staffId,
            @Valid @RequestBody UpdateStaffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID orgId = TenantContext.requireCurrentTenantId();
        StaffResponse staff = staffService.updateStaff(orgId, staffId, request);
        return ResponseEntity.ok(ApiResponse.success(staff, MDC.get("correlationId")));
    }
}
