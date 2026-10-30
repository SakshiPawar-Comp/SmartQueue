package com.smartqueue.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RbacAuthorizationTest {

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

    private String tokenFor(User user) {
        return jwtProvider.generateAccessToken(user.getEmail(), user.getId().toString());
    }

    // ==================== /api/platform/dashboard ====================

    @Test
    void unauthenticatedUser_dashboard_returns403() throws Exception {
        mockMvc.perform(get("/api/platform/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customer_canAccessDashboard() throws Exception {
        User user = createUser("customer-dash@example.com");
        assignRole(user, "CUSTOMER", orgA);

        mockMvc.perform(get("/api/platform/dashboard")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Platform dashboard - accessible to all authenticated users"));
    }

    @Test
    void staff_canAccessDashboard() throws Exception {
        User user = createUser("staff-dash@example.com");
        assignRole(user, "STAFF", orgA);

        mockMvc.perform(get("/api/platform/dashboard")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk());
    }

    @Test
    void superAdmin_canAccessDashboard() throws Exception {
        User user = createUser("superadmin-dash@example.com");
        assignRole(user, "SUPER_ADMIN", null);

        mockMvc.perform(get("/api/platform/dashboard")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk());
    }

    // ==================== /api/platform/admin/staff-panel ====================

    @Test
    void customer_cannotAccessStaffPanel() throws Exception {
        User user = createUser("customer-staff@example.com");
        assignRole(user, "CUSTOMER", orgA);

        mockMvc.perform(get("/api/platform/admin/staff-panel")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void staff_canAccessStaffPanel() throws Exception {
        User user = createUser("staff-panel@example.com");
        assignRole(user, "STAFF", orgA);

        mockMvc.perform(get("/api/platform/admin/staff-panel")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Staff panel - STAFF, ORG_ADMIN, or SUPER_ADMIN only"));
    }

    @Test
    void branchAdmin_canAccessStaffPanel() throws Exception {
        User user = createUser("branch-staff@example.com");
        assignRole(user, "BRANCH_ADMIN", orgA);

        mockMvc.perform(get("/api/platform/admin/staff-panel")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk());
    }

    @Test
    void orgAdmin_canAccessStaffPanel() throws Exception {
        User user = createUser("orgadmin-staff@example.com");
        assignRole(user, "ORGANIZATION_ADMIN", orgA);

        mockMvc.perform(get("/api/platform/admin/staff-panel")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk());
    }

    @Test
    void superAdmin_canAccessStaffPanel() throws Exception {
        User user = createUser("superadmin-staff@example.com");
        assignRole(user, "SUPER_ADMIN", null);

        mockMvc.perform(get("/api/platform/admin/staff-panel")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk());
    }

    // ==================== /api/platform/admin/org-management ====================

    @Test
    void customer_cannotAccessOrgManagement() throws Exception {
        User user = createUser("customer-org@example.com");
        assignRole(user, "CUSTOMER", orgA);

        mockMvc.perform(get("/api/platform/admin/org-management")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void staff_cannotAccessOrgManagement() throws Exception {
        User user = createUser("staff-org@example.com");
        assignRole(user, "STAFF", orgA);

        mockMvc.perform(get("/api/platform/admin/org-management")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void branchAdmin_cannotAccessOrgManagement() throws Exception {
        User user = createUser("branch-org@example.com");
        assignRole(user, "BRANCH_ADMIN", orgA);

        mockMvc.perform(get("/api/platform/admin/org-management")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void orgAdmin_canAccessOrgManagement() throws Exception {
        User user = createUser("orgadmin-org@example.com");
        assignRole(user, "ORGANIZATION_ADMIN", orgA);

        mockMvc.perform(get("/api/platform/admin/org-management")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Organization management - ORG_ADMIN or SUPER_ADMIN only"));
    }

    @Test
    void superAdmin_canAccessOrgManagement() throws Exception {
        User user = createUser("superadmin-org@example.com");
        assignRole(user, "SUPER_ADMIN", null);

        mockMvc.perform(get("/api/platform/admin/org-management")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk());
    }

    // ==================== /api/platform/admin/super-admin ====================

    @Test
    void customer_cannotAccessSuperAdmin() throws Exception {
        User user = createUser("customer-super@example.com");
        assignRole(user, "CUSTOMER", orgA);

        mockMvc.perform(get("/api/platform/admin/super-admin")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void staff_cannotAccessSuperAdmin() throws Exception {
        User user = createUser("staff-super@example.com");
        assignRole(user, "STAFF", orgA);

        mockMvc.perform(get("/api/platform/admin/super-admin")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void branchAdmin_cannotAccessSuperAdmin() throws Exception {
        User user = createUser("branch-super@example.com");
        assignRole(user, "BRANCH_ADMIN", orgA);

        mockMvc.perform(get("/api/platform/admin/super-admin")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void orgAdmin_cannotAccessSuperAdmin() throws Exception {
        User user = createUser("orgadmin-super@example.com");
        assignRole(user, "ORGANIZATION_ADMIN", orgA);

        mockMvc.perform(get("/api/platform/admin/super-admin")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void superAdmin_canAccessSuperAdmin() throws Exception {
        User user = createUser("superadmin-super@example.com");
        assignRole(user, "SUPER_ADMIN", null);

        mockMvc.perform(get("/api/platform/admin/super-admin")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Super admin panel - SUPER_ADMIN only"));
    }

    // ==================== /api/platform/admin/branch-management ====================

    @Test
    void customer_cannotAccessBranchManagement() throws Exception {
        User user = createUser("customer-branch@example.com");
        assignRole(user, "CUSTOMER", orgA);

        mockMvc.perform(get("/api/platform/admin/branch-management")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void staff_cannotAccessBranchManagement() throws Exception {
        User user = createUser("staff-branch@example.com");
        assignRole(user, "STAFF", orgA);

        mockMvc.perform(get("/api/platform/admin/branch-management")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void branchAdmin_canAccessBranchManagement() throws Exception {
        User user = createUser("branch-admin-branch@example.com");
        assignRole(user, "BRANCH_ADMIN", orgA);

        mockMvc.perform(get("/api/platform/admin/branch-management")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Branch management - BRANCH_ADMIN, ORG_ADMIN, or SUPER_ADMIN only"));
    }

    @Test
    void orgAdmin_canAccessBranchManagement() throws Exception {
        User user = createUser("orgadmin-branch@example.com");
        assignRole(user, "ORGANIZATION_ADMIN", orgA);

        mockMvc.perform(get("/api/platform/admin/branch-management")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk());
    }

    @Test
    void superAdmin_canAccessBranchManagement() throws Exception {
        User user = createUser("superadmin-branch@example.com");
        assignRole(user, "SUPER_ADMIN", null);

        mockMvc.perform(get("/api/platform/admin/branch-management")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk());
    }

    // ==================== Cross-tenant isolation ====================

    @Test
    void staff_orgA_cannotSelect_orgB() throws Exception {
        User user = createUser("staff-cross@example.com");
        assignRole(user, "STAFF", orgA);

        String token = tokenFor(user);

        mockMvc.perform(get("/api/platform/dashboard")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-Id", orgB.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_ACCESS_DENIED"));
    }

    @Test
    void orgAdmin_orgA_cannotAccess_orgB() throws Exception {
        User user = createUser("orgadmin-cross@example.com");
        assignRole(user, "ORGANIZATION_ADMIN", orgA);

        String token = tokenFor(user);

        mockMvc.perform(get("/api/platform/admin/org-management")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Organization-Id", orgB.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_ACCESS_DENIED"));
    }

    @Test
    void superAdmin_withNoOrgRole_canAccessWithoutOrgHeader() throws Exception {
        User user = createUser("superadmin-noorg@example.com");
        assignRole(user, "SUPER_ADMIN", null);

        mockMvc.perform(get("/api/platform/admin/super-admin")
                        .header("Authorization", "Bearer " + tokenFor(user)))
                .andExpect(status().isOk());
    }

    @Test
    void orgAdmin_withOrg_canAccessWithOrgHeader() throws Exception {
        User user = createUser("orgadmin-header@example.com");
        assignRole(user, "ORGANIZATION_ADMIN", orgA);

        mockMvc.perform(get("/api/platform/admin/org-management")
                        .header("Authorization", "Bearer " + tokenFor(user))
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk());
    }

    // ==================== Existing auth tests compatibility ====================

    @Test
    void existingAuth_meEndpoint_stillWorks() throws Exception {
        User user = createUser("compat-me@example.com");
        assignRole(user, "CUSTOMER", orgA);

        String token = tokenFor(user);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("compat-me@example.com"));
    }
}
