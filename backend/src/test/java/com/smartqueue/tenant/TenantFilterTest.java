package com.smartqueue.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.auth.api.dto.RegisterRequestDto;
import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Organization orgA;
    private Organization orgB;

    @BeforeEach
    void setUp() {
        orgA = createOrganization("Org A", "org-a");
        orgB = createOrganization("Org B", "org-b");
    }

    private Organization createOrganization(String name, String slug) {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName(name);
        org.setSlug(slug);
        org.setStatus("ACTIVE");
        org.setCreatedAt(ZonedDateTime.now());
        org.setUpdatedAt(ZonedDateTime.now());
        return organizationRepository.save(org);
    }

    private User createUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFullName("User " + email);
        user.setStatus("ACTIVE");
        user.setCreatedAt(ZonedDateTime.now());
        user.setUpdatedAt(ZonedDateTime.now());
        return userRepository.save(user);
    }

    private void assignRole(User user, String roleCode, Organization org) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleCode));
        UserRole userRole = new UserRole(user, role, org);
        userRoleRepository.save(userRole);
    }

    private MvcResult registerUser(String email, String password) throws Exception {
        RegisterRequestDto r = new RegisterRequestDto();
        r.setEmail(email);
        r.setPassword(password);
        r.setFullName("User " + email);
        r.setRole(RegisterRequestDto.RoleCode.ORGANIZATION_ADMIN);
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(body);
        return node.get("data").get("accessToken").asText();
    }

    @Test
    void authenticatedUser_validOrgHeader_allowed() throws Exception {
        User user = createUser("orgmember@example.com");
        assignRole(user, "CUSTOMER", orgA);

        String token = jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("orgmember@example.com"));
    }

    @Test
    void authenticatedUser_anotherOrg_returns403() throws Exception {
        User user = createUser("orguser@example.com");
        assignRole(user, "CUSTOMER", orgA);

        String token = jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-Id", orgB.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_ACCESS_DENIED"));
    }

    @Test
    void authenticatedUser_malformedUuid_returns400() throws Exception {
        User user = createUser("malformed@example.com");
        assignRole(user, "CUSTOMER", orgA);

        String token = jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ORGANIZATION_ID"));
    }

    @Test
    void noOrgHeader_noTenantSet() throws Exception {
        User user = createUser("noheader@example.com");
        assignRole(user, "CUSTOMER", orgA);

        String token = jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());

        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("noheader@example.com"))
                .andReturn();

        Assertions.assertNull(TenantContext.getCurrentTenant(),
                "TenantContext should be cleared after request");
    }

    @Test
    void tenantContext_clearedAfterRequest() throws Exception {
        User user = createUser("cleared@example.com");
        assignRole(user, "CUSTOMER", orgA);

        String token = jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk());

        Assertions.assertNull(TenantContext.getCurrentTenant(),
                "TenantContext must be cleared after request completes");
    }

    @Test
    void userWithNoOrgMembership_cannotAccessArbitraryOrg() throws Exception {
        User user = createUser("nomember@example.com");
        assignRole(user, "CUSTOMER", null);

        String token = jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_ACCESS_DENIED"));
    }

    @Test
    void existingAuthTests_stillPass_registerLoginMe() throws Exception {
        MvcResult registerResult = registerUser("compat@example.com", "password123");
        String accessToken = extractAccessToken(registerResult);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("compat@example.com"));
    }

    @Test
    void unauthenticatedUser_withOrgHeader_returns403() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void multipleOrgRoles_accessAllowedForBoth() throws Exception {
        User user = createUser("multiorg@example.com");
        assignRole(user, "STAFF", orgA);
        assignRole(user, "STAFF", orgB);

        String token = jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-Id", orgB.getId().toString()))
                .andExpect(status().isOk());
    }
}
