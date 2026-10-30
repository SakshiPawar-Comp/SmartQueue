package com.smartqueue.auth.application;

import com.smartqueue.auth.api.dto.AuthResponseDto;
import com.smartqueue.auth.api.dto.LoginRequestDto;
import com.smartqueue.auth.api.dto.RegisterRequestDto;
import com.smartqueue.auth.api.dto.UserDto;
import com.smartqueue.auth.domain.RefreshToken;
import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RefreshTokenRepository;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;

    @Value("${jwt.expiration.refresh}")
    private long refreshTokenDurationMs;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       UserRoleRepository userRoleRepository, RefreshTokenRepository refreshTokenRepository,
                       OrganizationRepository organizationRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtProvider = jwtProvider;
    }

    @Transactional
    public AuthResult register(RegisterRequestDto request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AuthException("Email is already registered");
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setStatus("ACTIVE");
        user.setCreatedAt(ZonedDateTime.now());
        user.setUpdatedAt(ZonedDateTime.now());

        userRepository.save(user);

        String roleCode = request.getRole().name();
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new AuthException("Role not found: " + roleCode));

        Organization org = null;
        if ("ORGANIZATION_ADMIN".equals(roleCode)) {
            org = new Organization();
            org.setId(UUID.randomUUID());
            org.setName(request.getFullName() + "'s Organization");
            org.setSlug("org-" + user.getId().toString().substring(0, 8));
            org.setStatus("ACTIVE");
            org.setCreatedAt(ZonedDateTime.now());
            org.setUpdatedAt(ZonedDateTime.now());
            organizationRepository.save(org);
        } else if ("CUSTOMER".equals(roleCode)) {
            String orgCode = request.getOrganizationCode() != null ? request.getOrganizationCode().trim() : "";
            if (orgCode.isEmpty()) {
                // If code is omitted, gracefully default to the first active organization
                List<Organization> activeOrgs = organizationRepository.findByStatus("ACTIVE");
                if (!activeOrgs.isEmpty()) {
                    org = activeOrgs.get(0);
                } else {
                    throw new AuthException("Organization code is required for customers");
                }
            } else {
                final String codeToSearch = orgCode;
                org = organizationRepository.findBySlugIgnoreCase(codeToSearch)
                        .or(() -> organizationRepository.findByNameIgnoreCase(codeToSearch))
                        .orElse(null);

                if (org == null) {
                    try {
                        UUID orgId = UUID.fromString(codeToSearch);
                        org = organizationRepository.findById(orgId).orElse(null);
                    } catch (IllegalArgumentException ignored) {}
                }

                if (org == null) {
                    throw new AuthException("Invalid organization code. Available: org-597c359e");
                }
            }

            if (!"ACTIVE".equals(org.getStatus())) {
                throw new AuthException("Organization is not active");
            }
        }

        UserRole userRole = new UserRole(user, role, org);
        userRoleRepository.save(userRole);

        String accessToken = jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());
        String refreshTokenPlain = generateRefreshToken(user);

        UserDto userDto = toUserDto(user, List.of(roleCode));

        return new AuthResult(
                new AuthResponseDto(userDto, accessToken),
                refreshTokenPlain
        );
    }

    @Transactional
    public AuthResult login(LoginRequestDto request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            throw new AuthException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthException("User not found"));

        String accessToken = jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());
        String refreshTokenPlain = generateRefreshToken(user);

        UserDto userDto = toUserDto(user);

        return new AuthResult(
                new AuthResponseDto(userDto, accessToken),
                refreshTokenPlain
        );
    }

    @Transactional
    public AuthResult refresh(String incomingRefreshTokenPlain) {
        String hashedToken = hashToken(incomingRefreshTokenPlain);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new AuthException("Invalid refresh token"));

        if (!refreshToken.getStatus().equals("ACTIVE") || refreshToken.getExpiresAt().isBefore(ZonedDateTime.now())) {
            if ("REVOKED".equals(refreshToken.getStatus())) {
                log.warn("Refresh token reuse detected for user {}", refreshToken.getUser().getId());
                revokeAllUserTokens(refreshToken.getUser().getId());
                throw new AuthException("Refresh token reuse detected. All tokens revoked.");
            }
            throw new AuthException("Refresh token is expired or inactive");
        }

        refreshToken.setStatus("REVOKED");
        refreshTokenRepository.save(refreshToken);

        User user = refreshToken.getUser();
        String newAccessToken = jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());
        String newRefreshTokenPlain = generateRefreshToken(user);

        UserDto userDto = toUserDto(user);

        return new AuthResult(
                new AuthResponseDto(userDto, newAccessToken),
                newRefreshTokenPlain
        );
    }

    @Transactional
    public void logout(String refreshTokenPlain) {
        if (refreshTokenPlain == null || refreshTokenPlain.isEmpty()) {
            return;
        }
        String hashedToken = hashToken(refreshTokenPlain);
        refreshTokenRepository.findByTokenHash(hashedToken).ifPresent(rt -> {
            rt.setStatus("REVOKED");
            refreshTokenRepository.save(rt);
        });
    }

    private String generateRefreshToken(User user) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(plainToken));
        refreshToken.setStatus("ACTIVE");
        refreshToken.setExpiresAt(ZonedDateTime.now().plusSeconds(refreshTokenDurationMs / 1000));
        refreshToken.setCreatedAt(ZonedDateTime.now());

        refreshTokenRepository.save(refreshToken);
        return plainToken;
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllActiveByUserId(userId);
        log.info("Revoked {} active refresh tokens for user {}", revoked, userId);
    }

    private UserDto toUserDto(User user) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
        List<String> roles = userRoles.stream()
                .map(ur -> ur.getRole().getCode())
                .distinct()
                .collect(Collectors.toList());
        UUID organizationId = userRoles.stream()
                .map(UserRole::getOrganization)
                .filter(org -> org != null)
                .map(org -> org.getId())
                .findFirst()
                .orElse(null);
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getStatus(), roles, organizationId);
    }

    private UserDto toUserDto(User user, List<String> roleCodes) {
        UUID organizationId = userRoleRepository.findByUserId(user.getId()).stream()
                .map(UserRole::getOrganization)
                .filter(org -> org != null)
                .map(org -> org.getId())
                .findFirst()
                .orElse(null);
        return new UserDto(user.getId(), user.getEmail(), user.getFullName(), user.getStatus(), roleCodes, organizationId);
    }
}
