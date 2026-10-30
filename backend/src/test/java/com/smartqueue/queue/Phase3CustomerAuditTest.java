package com.smartqueue.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.auth.api.dto.ChangePasswordRequest;
import com.smartqueue.auth.api.dto.UpdateProfileRequest;
import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import com.smartqueue.service.domain.Service;
import com.smartqueue.service.infrastructure.ServiceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Phase3CustomerAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private QueueService queueService;

    @Autowired
    private EntityManager entityManager;

    // Tenant A
    private Organization orgA;
    private Branch branchA;
    private Service serviceA;
    private Queue queueA;
    private User adminA;
    private User customerA1;
    private User customerA2;
    private String adminAJwt;
    private String customerA1Jwt;
    private String customerA2Jwt;

    // Tenant B
    private Organization orgB;
    private Branch branchB;
    private Service serviceB;
    private Queue queueB;
    private User customerB;
    private String customerBJwt;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setCode("CUSTOMER");
                    r.setName("Customer");
                    return roleRepository.save(r);
                });

        Role adminRole = roleRepository.findByCode("ORGANIZATION_ADMIN")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setCode("ORGANIZATION_ADMIN");
                    r.setName("Organization Admin");
                    return roleRepository.save(r);
                });

        // Org A Setup
        orgA = new Organization();
        orgA.setId(UUID.randomUUID());
        orgA.setName("City General Hospital");
        orgA.setSlug("city-hospital-" + UUID.randomUUID().toString().substring(0, 8));
        orgA.setStatus("ACTIVE");
        orgA.setCreatedAt(ZonedDateTime.now());
        orgA.setUpdatedAt(ZonedDateTime.now());
        orgA = organizationRepository.save(orgA);

        branchA = new Branch();
        branchA.setId(UUID.randomUUID());
        branchA.setOrganization(orgA);
        branchA.setName("Downtown Branch");
        branchA.setCode("DT_BR");
        branchA.setTimezone("UTC");
        branchA.setStatus("ACTIVE");
        branchA = branchRepository.save(branchA);

        serviceA = new Service(orgA, "Cardiology OPD");
        serviceA.setBranch(branchA);
        serviceA.setStatus("ACTIVE");
        serviceA = serviceRepository.save(serviceA);

        queueA = new Queue(orgA.getId(), "Cardiology Room 101");
        queueA.setBranchId(branchA.getId());
        queueA.setServiceId(serviceA.getId());
        queueA.setStatus(QueueStatus.ACTIVE);
        queueA = queueRepository.save(queueA);

        adminA = createUser("adminA@hospital.com", "Admin A", "ACTIVE");
        userRoleRepository.save(new UserRole(adminA, adminRole, orgA));
        adminAJwt = jwtProvider.generateAccessToken(adminA.getEmail(), adminA.getId().toString());

        customerA1 = createUser("customerA1@test.com", "Customer A1", "ACTIVE");
        userRoleRepository.save(new UserRole(customerA1, customerRole, orgA));
        customerA1Jwt = jwtProvider.generateAccessToken(customerA1.getEmail(), customerA1.getId().toString());

        customerA2 = createUser("customerA2@test.com", "Customer A2", "ACTIVE");
        userRoleRepository.save(new UserRole(customerA2, customerRole, orgA));
        customerA2Jwt = jwtProvider.generateAccessToken(customerA2.getEmail(), customerA2.getId().toString());

        // Org B Setup
        orgB = new Organization();
        orgB.setId(UUID.randomUUID());
        orgB.setName("Metro Bank");
        orgB.setSlug("metro-bank-" + UUID.randomUUID().toString().substring(0, 8));
        orgB.setStatus("ACTIVE");
        orgB.setCreatedAt(ZonedDateTime.now());
        orgB.setUpdatedAt(ZonedDateTime.now());
        orgB = organizationRepository.save(orgB);

        branchB = new Branch();
        branchB.setId(UUID.randomUUID());
        branchB.setOrganization(orgB);
        branchB.setName("Financial District");
        branchB.setCode("FD_BR");
        branchB.setTimezone("UTC");
        branchB.setStatus("ACTIVE");
        branchB = branchRepository.save(branchB);

        serviceB = new Service(orgB, "Wealth Management");
        serviceB.setBranch(branchB);
        serviceB.setStatus("ACTIVE");
        serviceB = serviceRepository.save(serviceB);

        queueB = new Queue(orgB.getId(), "Teller 1");
        queueB.setBranchId(branchB.getId());
        queueB.setServiceId(serviceB.getId());
        queueB.setStatus(QueueStatus.ACTIVE);
        queueB = queueRepository.save(queueB);

        customerB = createUser("customerB@bank.com", "Customer B", "ACTIVE");
        userRoleRepository.save(new UserRole(customerB, customerRole, orgB));
        customerBJwt = jwtProvider.generateAccessToken(customerB.getEmail(), customerB.getId().toString());

        entityManager.flush();
    }

    private User createUser(String email, String fullName, String status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(UUID.randomUUID().toString().substring(0, 8) + "_" + email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFullName(fullName);
        user.setStatus(status);
        user.setPreferredLanguage("en");
        user.setCreatedAt(ZonedDateTime.now());
        user.setUpdatedAt(ZonedDateTime.now());
        return userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        com.smartqueue.common.tenant.TenantContext.clear();
    }

    @Test
    @DisplayName("Audit 1: Complete Discovery Hierarchy: Org -> Branch -> Service -> Queue")
    void testDiscoveryHierarchy() throws Exception {
        // Step 1: List Organizations
        mockMvc.perform(get("/api/organizations")
                        .header("Authorization", "Bearer " + customerA1Jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + orgA.getId() + "')].name").value("City General Hospital"));

        // Step 2: Get Branches of Org A
        mockMvc.perform(get("/api/organizations/" + orgA.getId() + "/branches")
                        .header("Authorization", "Bearer " + customerA1Jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Downtown Branch"));

        // Step 3: Get Services of Branch A
        mockMvc.perform(get("/api/branches/" + branchA.getId() + "/services")
                        .header("Authorization", "Bearer " + customerA1Jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Cardiology OPD"));

        // Step 4: Get Queues of Branch A
        mockMvc.perform(get("/api/branches/" + branchA.getId() + "/queues")
                        .header("Authorization", "Bearer " + customerA1Jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Cardiology Room 101"));
    }

    @Test
    @DisplayName("Audit 2: Multi-Customer Queue Join, Position Countdown & Deterministic ETA")
    void testMultiCustomerPositionAndETA() throws Exception {
        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(orgA.getId());

        // Customer A1 Joins
        QueueToken tokenA1 = queueService.generateToken(queueA.getId(), customerA1);
        assertEquals("A001", tokenA1.getTokenNumber());

        // Customer A2 Joins
        QueueToken tokenA2 = queueService.generateToken(queueA.getId(), customerA2);
        assertEquals("A002", tokenA2.getTokenNumber());

        com.smartqueue.common.tenant.TenantContext.clear();

        // Verify Customer A1 Active Token state (Position 1, 0 ahead, 0 min ETA)
        mockMvc.perform(get("/api/customers/me/tokens/active")
                        .header("Authorization", "Bearer " + customerA1Jwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tokenNumber").value("A001"))
                .andExpect(jsonPath("$.data[0].position").value(1))
                .andExpect(jsonPath("$.data[0].peopleAhead").value(0))
                .andExpect(jsonPath("$.data[0].estimatedWaitMinutes").value(0));

        // Verify Customer A2 Active Token state (Position 2, 1 ahead, 5 min ETA)
        mockMvc.perform(get("/api/customers/me/tokens/active")
                        .header("Authorization", "Bearer " + customerA2Jwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tokenNumber").value("A002"))
                .andExpect(jsonPath("$.data[0].position").value(2))
                .andExpect(jsonPath("$.data[0].peopleAhead").value(1))
                .andExpect(jsonPath("$.data[0].estimatedWaitMinutes").value(5));

        // Admin calls NEXT -> tokenA1 becomes CALLED
        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(orgA.getId());
        QueueToken calledToken = queueService.callNext(queueA.getId());
        assertEquals("A001", calledToken.getTokenNumber());
        assertEquals(TokenStatus.CALLED, calledToken.getStatus());
        com.smartqueue.common.tenant.TenantContext.clear();

        // Customer A2 Active Token should now be Position 1, 0 ahead, 0 min ETA
        mockMvc.perform(get("/api/customers/me/tokens/active")
                        .header("Authorization", "Bearer " + customerA2Jwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tokenNumber").value("A002"))
                .andExpect(jsonPath("$.data[0].position").value(1))
                .andExpect(jsonPath("$.data[0].peopleAhead").value(0))
                .andExpect(jsonPath("$.data[0].estimatedWaitMinutes").value(0));
    }

    @Test
    @DisplayName("Audit 3: State Transitions: CALLED -> HOLD -> RESUME -> COMPLETE & Leave Queue")
    void testStateTransitionsAndLeaveQueue() throws Exception {
        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(orgA.getId());
        QueueToken tokenA1 = queueService.generateToken(queueA.getId(), customerA1);

        // Call Next -> CALLED
        queueService.callNext(queueA.getId());

        // Hold -> HOLD
        QueueToken held = queueService.holdToken(tokenA1.getId());
        assertEquals(TokenStatus.HOLD, held.getStatus());

        // Resume -> WAITING
        QueueToken resumed = queueService.resumeToken(tokenA1.getId());
        assertEquals(TokenStatus.WAITING, resumed.getStatus());

        // Call Next again -> CALLED
        queueService.callNext(queueA.getId());

        // Complete -> COMPLETED
        QueueToken completed = queueService.completeToken(tokenA1.getId());
        assertEquals(TokenStatus.COMPLETED, completed.getStatus());

        // Invalid Transition: Cannot complete an already completed token
        assertThrows(Exception.class, () -> queueService.completeToken(tokenA1.getId()));

        // Customer A1 generates a second token and leaves
        QueueToken tokenA1New = queueService.generateToken(queueA.getId(), customerA1);
        com.smartqueue.common.tenant.TenantContext.clear();

        mockMvc.perform(post("/api/tokens/" + tokenA1New.getId() + "/leave")
                        .header("Authorization", "Bearer " + customerA1Jwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Audit 4: Ownership Isolation: Customer A cannot cancel/leave Customer B's token")
    void testOwnershipSecurity() throws Exception {
        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(orgA.getId());
        QueueToken tokenA1 = queueService.generateToken(queueA.getId(), customerA1);
        QueueToken tokenA2 = queueService.generateToken(queueA.getId(), customerA2);
        com.smartqueue.common.tenant.TenantContext.clear();

        // Customer A2 attempts to leave Customer A1's token -> 403 Forbidden
        mockMvc.perform(post("/api/tokens/" + tokenA1.getId() + "/leave")
                        .header("Authorization", "Bearer " + customerA2Jwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isForbidden());

        // Customer A2 attempts to cancel Customer A1's token -> 403 Forbidden
        mockMvc.perform(post("/api/tokens/" + tokenA1.getId() + "/cancel")
                        .header("Authorization", "Bearer " + customerA2Jwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Audit 5: Tenant Isolation: Customer A cannot access resources in Org B")
    void testTenantIsolation() throws Exception {
        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(orgB.getId());
        QueueToken tokenB = queueService.generateToken(queueB.getId(), customerB);
        com.smartqueue.common.tenant.TenantContext.clear();

        // Customer A1 attempts to leave Customer B's token in Org B -> 403 Forbidden
        mockMvc.perform(post("/api/tokens/" + tokenB.getId() + "/leave")
                        .header("Authorization", "Bearer " + customerA1Jwt)
                        .header("X-Organization-Id", orgB.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Audit 6: RBAC: Customer cannot perform administrative queue actions")
    void testCustomerRbacRestrictions() throws Exception {
        // Customer cannot call NEXT
        mockMvc.perform(post("/api/queues/" + queueA.getId() + "/next")
                        .header("Authorization", "Bearer " + customerA1Jwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isForbidden());

        // Customer cannot close queue
        mockMvc.perform(post("/api/queues/" + queueA.getId() + "/close")
                        .header("Authorization", "Bearer " + customerA1Jwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Audit 7: History Query & Pagination: Isolated per customer")
    void testCustomerHistoryPaginationAndIsolation() throws Exception {
        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(orgA.getId());

        // Generate and complete 3 tokens for Customer A1
        for (int i = 0; i < 3; i++) {
            QueueToken t = queueService.generateToken(queueA.getId(), customerA1);
            queueService.callNext(queueA.getId());
            queueService.completeToken(t.getId());
        }

        // Generate and cancel 1 token for Customer A2
        QueueToken t2 = queueService.generateToken(queueA.getId(), customerA2);
        queueService.cancelToken(t2.getId(), customerA2, false);

        com.smartqueue.common.tenant.TenantContext.clear();

        // Customer A1 checks history: totalElements = 3
        mockMvc.perform(get("/api/customers/me/tokens/history?page=0&size=2")
                        .header("Authorization", "Bearer " + customerA1Jwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)));

        // Customer A2 checks history: totalElements = 1 (cancelled)
        mockMvc.perform(get("/api/customers/me/tokens/history?page=0&size=10")
                        .header("Authorization", "Bearer " + customerA2Jwt)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Audit 8: Profile Security & Multilingual Preference Persistence")
    void testProfileAndLanguagePersistence() throws Exception {
        // Update language to Hindi ('hi')
        UpdateProfileRequest updateReq = new UpdateProfileRequest();
        updateReq.setFullName("Customer A1 Hindi");
        updateReq.setPhone("+919876543210");
        updateReq.setPreferredLanguage("hi");

        mockMvc.perform(put("/api/customers/me/profile")
                        .header("Authorization", "Bearer " + customerA1Jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Get Profile
        mockMvc.perform(get("/api/customers/me/profile")
                        .header("Authorization", "Bearer " + customerA1Jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferredLanguage").value("hi"))
                .andExpect(jsonPath("$.data.fullName").value("Customer A1 Hindi"))
                .andExpect(jsonPath("$.data.phone").value("+919876543210"));
    }
}
