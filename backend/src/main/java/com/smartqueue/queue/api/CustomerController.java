package com.smartqueue.queue.api;

import com.smartqueue.auth.api.dto.ChangePasswordRequest;
import com.smartqueue.auth.api.dto.UpdateProfileRequest;
import com.smartqueue.auth.api.dto.UserDto;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import com.smartqueue.queue.api.dto.CustomerHistoryPageResponse;
import com.smartqueue.queue.api.dto.QueueTokenResponse;
import com.smartqueue.queue.application.CustomerService;
import com.smartqueue.queue.domain.TokenStatus;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customers/me")
@PreAuthorize("isAuthenticated()")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** GET /api/customers/me/tokens/active - Get all active tokens for logged in customer */
    @GetMapping("/tokens/active")
    public ResponseEntity<ApiResponse<List<QueueTokenResponse>>> getActiveTokens(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<QueueTokenResponse> activeTokens = customerService.getActiveTokens(userDetails.getUser());
        return ResponseEntity.ok(ApiResponse.success(activeTokens, MDC.get("correlationId")));
    }

    /** GET /api/customers/me/tokens/history - Get paginated visit history */
    @GetMapping("/tokens/history")
    public ResponseEntity<ApiResponse<CustomerHistoryPageResponse>> getHistory(
            @RequestParam(required = false) TokenStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CustomerHistoryPageResponse history = customerService.getCustomerHistory(userDetails.getUser(), status, page, size);
        return ResponseEntity.ok(ApiResponse.success(history, MDC.get("correlationId")));
    }

    /** GET /api/customers/me/profile - Get customer profile */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserDto>> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        List<String> roles = userDetails.getUserRoles().stream()
                .map(UserRole::getRole)
                .map(r -> r.getCode())
                .distinct()
                .collect(Collectors.toList());

        UUID organizationId = userDetails.getUserRoles().stream()
                .map(UserRole::getOrganization)
                .filter(org -> org != null)
                .map(org -> org.getId())
                .findFirst()
                .orElse(null);

        UserDto dto = new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getPreferredLanguage(),
                user.getStatus(),
                roles,
                organizationId
        );
        return ResponseEntity.ok(ApiResponse.success(dto, MDC.get("correlationId")));
    }

    /** PUT/PATCH /api/customers/me/profile - Update customer profile */
    @RequestMapping(value = "/profile", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User updatedUser = customerService.updateProfile(
                userDetails.getUser(),
                request.getFullName(),
                request.getPhone(),
                request.getPreferredLanguage()
        );

        List<String> roles = userDetails.getUserRoles().stream()
                .map(UserRole::getRole)
                .map(r -> r.getCode())
                .distinct()
                .collect(Collectors.toList());

        UUID organizationId = userDetails.getUserRoles().stream()
                .map(UserRole::getOrganization)
                .filter(org -> org != null)
                .map(org -> org.getId())
                .findFirst()
                .orElse(null);

        UserDto dto = new UserDto(
                updatedUser.getId(),
                updatedUser.getEmail(),
                updatedUser.getFullName(),
                updatedUser.getPhone(),
                updatedUser.getPreferredLanguage(),
                updatedUser.getStatus(),
                roles,
                organizationId
        );
        return ResponseEntity.ok(ApiResponse.success(dto, MDC.get("correlationId")));
    }

    /** POST /api/customers/me/change-password - Change password securely */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        customerService.changePassword(userDetails.getUser(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null, MDC.get("correlationId")));
    }
}
