package com.smartqueue.auth.api;

import com.smartqueue.auth.application.AuthResult;
import com.smartqueue.auth.application.AuthService;
import com.smartqueue.auth.api.dto.AuthResponseDto;
import com.smartqueue.auth.api.dto.LoginRequestDto;
import com.smartqueue.auth.api.dto.RegisterRequestDto;
import com.smartqueue.auth.api.dto.UserDto;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.security.CustomUserDetails;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${smartqueue.refresh-token.cookie-name:sq_rt}")
    private String cookieName;

    @Value("${smartqueue.refresh-token.cookie-path:/api/auth/refresh}")
    private String cookiePath;

    @Value("${smartqueue.refresh-token.secure:false}")
    private boolean cookieSecure;

    @Value("${jwt.expiration.refresh}")
    private long refreshTokenDurationMs;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponseDto>> register(
            @Valid @RequestBody RegisterRequestDto request,
            HttpServletResponse response
    ) {
        AuthResult result = authService.register(request);
        addRefreshTokenCookie(response, result.refreshTokenPlain());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result.authResponse(), MDC.get("correlationId")));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletResponse response
    ) {
        AuthResult result = authService.login(request);
        addRefreshTokenCookie(response, result.refreshTokenPlain());
        return ResponseEntity.ok(ApiResponse.success(result.authResponse(), MDC.get("correlationId")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponseDto>> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractCookieValue(request, cookieName);
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.success(null, MDC.get("correlationId")));
        }

        AuthResult result = authService.refresh(refreshToken);
        addRefreshTokenCookie(response, result.refreshTokenPlain());
        return ResponseEntity.ok(ApiResponse.success(result.authResponse(), MDC.get("correlationId")));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractCookieValue(request, cookieName);
        authService.logout(refreshToken);
        clearRefreshTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> me(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.success(null, MDC.get("correlationId")));
        }

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

        UserDto userDto = new UserDto(
                userDetails.getUser().getId(),
                userDetails.getUser().getEmail(),
                userDetails.getUser().getFullName(),
                userDetails.getUser().getPhone(),
                userDetails.getUser().getPreferredLanguage(),
                userDetails.getUser().getStatus(),
                roles,
                organizationId
        );

        return ResponseEntity.ok(ApiResponse.success(userDto, MDC.get("correlationId")));
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String token) {
        int maxAge = (int) refreshTokenDurationMs / 1000;
        String cookieValue = buildCookieHeader(cookieName, token, cookiePath, cookieSecure, true, maxAge);
        response.addHeader("Set-Cookie", cookieValue);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        String cookieValue = buildCookieHeader(cookieName, "", cookiePath, cookieSecure, true, 0);
        response.addHeader("Set-Cookie", cookieValue);
    }

    private String buildCookieHeader(String name, String value, String path, boolean secure, boolean httpOnly, int maxAge) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value);
        sb.append("; Path=").append(path);
        sb.append("; Max-Age=").append(maxAge);
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (secure) {
            sb.append("; Secure");
        }
        sb.append("; SameSite=Strict");
        return sb.toString();
    }

    private String extractCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
