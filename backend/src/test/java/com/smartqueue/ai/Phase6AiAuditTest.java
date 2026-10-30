package com.smartqueue.ai;

import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenPriority;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Phase6AiAuditTest {

    @Autowired
    private MockMvc mockMvc;

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
    private BranchRepository branchRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private EntityManager entityManager;

    private Organization orgA;
    private Branch branchA;
    private Service serviceA;
    private Queue queueA;
    private User orgAdminA;
    private User customerA;
    private QueueToken token1;
    private QueueToken token2;
    private String orgAdminAToken;
    private String customerAToken;

    @BeforeEach
    void setUp() {
        orgA = new Organization();
        orgA.setId(UUID.randomUUID());
        orgA.setName("AI Test Org");
        orgA.setSlug("ai-org-" + UUID.randomUUID().toString().substring(0, 8));
        orgA.setStatus("ACTIVE");
        orgA.setCreatedAt(ZonedDateTime.now());
        orgA.setUpdatedAt(ZonedDateTime.now());
        orgA = organizationRepository.save(orgA);

        branchA = new Branch();
        branchA.setId(UUID.randomUUID());
        branchA.setName("Main Tech Hub");
        branchA.setCode("BR-03");
        branchA.setTimezone("UTC");
        branchA.setOrganization(orgA);
        branchA.setStatus("ACTIVE");
        branchA = branchRepository.save(branchA);

        serviceA = new Service();
        serviceA.setId(UUID.randomUUID());
        serviceA.setName("Consultation");
        serviceA.setOrganization(orgA);
        serviceA.setBranch(branchA);
        serviceA.setStatus("ACTIVE");
        serviceA = serviceRepository.save(serviceA);

        queueA = new Queue();
        queueA.setId(UUID.randomUUID());
        queueA.setName("Consultation Desk");
        queueA.setTokenPrefix("C");
        queueA.setCurrentTokenNumber("C000");
        queueA.setCapacity(100);
        queueA.setOrganizationId(orgA.getId());
        queueA.setBranchId(branchA.getId());
        queueA.setServiceId(serviceA.getId());
        queueA.setStatus(QueueStatus.ACTIVE);
        queueA.setDefaultServiceTimeMinutes(12);
        queueA.setIsPriorityEnabled(true);
        queueA.setCreatedAt(ZonedDateTime.now());
        queueA.setUpdatedAt(ZonedDateTime.now());
        queueA = queueRepository.save(queueA);

        Role adminRole = roleRepository.findByCode("ORGANIZATION_ADMIN").orElseThrow();
        Role customerRole = roleRepository.findByCode("CUSTOMER").orElseThrow();

        orgAdminA = new User();
        orgAdminA.setId(UUID.randomUUID());
        orgAdminA.setEmail("admin." + UUID.randomUUID() + "@ai.com");
        orgAdminA.setPasswordHash("pass");
        orgAdminA.setFullName("Admin AI");
        orgAdminA.setStatus("ACTIVE");
        orgAdminA.setCreatedAt(ZonedDateTime.now());
        orgAdminA.setUpdatedAt(ZonedDateTime.now());
        orgAdminA = userRepository.save(orgAdminA);
        userRoleRepository.save(new UserRole(orgAdminA, adminRole, orgA));

        customerA = new User();
        customerA.setId(UUID.randomUUID());
        customerA.setEmail("cust." + UUID.randomUUID() + "@ai.com");
        customerA.setPasswordHash("pass");
        customerA.setFullName("Customer AI");
        customerA.setStatus("ACTIVE");
        customerA.setCreatedAt(ZonedDateTime.now());
        customerA.setUpdatedAt(ZonedDateTime.now());
        customerA = userRepository.save(customerA);
        userRoleRepository.save(new UserRole(customerA, customerRole, orgA));

        orgAdminAToken = jwtProvider.generateAccessToken(orgAdminA.getEmail(), orgAdminA.getId().toString());
        customerAToken = jwtProvider.generateAccessToken(customerA.getEmail(), customerA.getId().toString());

        ZonedDateTime now = ZonedDateTime.now();
        token1 = new QueueToken();
        token1.setId(UUID.randomUUID());
        token1.setQueue(queueA);
        token1.setCustomer(customerA);
        token1.setTokenNumber("AI-001");
        token1.setPriority(TokenPriority.VIP);
        token1.setStatus(TokenStatus.WAITING);
        token1.setCreatedAt(now.minusMinutes(10));
        token1 = queueTokenRepository.save(token1);

        token2 = new QueueToken();
        token2.setId(UUID.randomUUID());
        token2.setQueue(queueA);
        token2.setCustomer(customerA);
        token2.setTokenNumber("AI-002");
        token2.setPriority(TokenPriority.NORMAL);
        token2.setStatus(TokenStatus.WAITING);
        token2.setCreatedAt(now.minusMinutes(5));
        token2 = queueTokenRepository.save(token2);

        entityManager.flush();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Customer and Admin can predict token ETA with explainability factors")
    void testPredictTokenEta() throws Exception {
        // Token 1 has 0 tokens ahead -> 0 wait time
        mockMvc.perform(get("/api/ai/eta/" + token1.getId())
                        .header("Authorization", "Bearer " + customerAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedWaitMinutes").value(0))
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.predictionType").value("DETERMINISTIC"));

        // Token 2 has 1 token ahead -> wait time based on service time or AI fallback
        mockMvc.perform(get("/api/ai/eta/" + token2.getId())
                        .header("Authorization", "Bearer " + customerAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value(2))
                .andExpect(jsonPath("$.data.estimatedWaitMinutes").isNumber())
                .andExpect(jsonPath("$.data.factors").isMap());
    }

    @Test
    @DisplayName("Admin can request demand forecast")
    void testDemandForecast() throws Exception {
        mockMvc.perform(get("/api/ai/forecast")
                        .param("queueId", queueA.getId().toString())
                        .param("horizonHours", "6")
                        .header("Authorization", "Bearer " + orgAdminAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").isString());
    }

    @Test
    @DisplayName("Admin can request anomaly detection")
    void testAnomalyDetection() throws Exception {
        mockMvc.perform(get("/api/ai/anomalies")
                        .param("queueId", queueA.getId().toString())
                        .header("Authorization", "Bearer " + orgAdminAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.anomalies").isArray());
    }

    @Test
    @DisplayName("Admin can request operational recommendations")
    void testOperationalRecommendations() throws Exception {
        mockMvc.perform(get("/api/ai/recommendations")
                        .param("queueId", queueA.getId().toString())
                        .header("Authorization", "Bearer " + orgAdminAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations").isArray());
    }

    @Test
    @DisplayName("Customer cannot access management AI endpoints (RBAC 403)")
    void testCustomerForbiddenFromAdminAi() throws Exception {
        mockMvc.perform(get("/api/ai/anomalies")
                        .header("Authorization", "Bearer " + customerAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/ai/recommendations")
                        .header("Authorization", "Bearer " + customerAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
