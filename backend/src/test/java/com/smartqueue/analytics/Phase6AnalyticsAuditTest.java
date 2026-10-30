package com.smartqueue.analytics;

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
import com.smartqueue.counter.domain.Counter;
import com.smartqueue.counter.infrastructure.CounterRepository;
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
class Phase6AnalyticsAuditTest {

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
    private CounterRepository counterRepository;

    @Autowired
    private EntityManager entityManager;

    private Organization orgA;
    private Organization orgB;
    private Branch branchA;
    private Service serviceA;
    private Queue queueA;
    private Counter counterA;
    private User orgAdminA;
    private User customerA;
    private String orgAdminAToken;
    private String customerAToken;

    @BeforeEach
    void setUp() {
        orgA = new Organization();
        orgA.setId(UUID.randomUUID());
        orgA.setName("FinTech Org A");
        orgA.setSlug("fintech-a-" + UUID.randomUUID().toString().substring(0, 8));
        orgA.setStatus("ACTIVE");
        orgA.setCreatedAt(ZonedDateTime.now());
        orgA.setUpdatedAt(ZonedDateTime.now());
        orgA = organizationRepository.save(orgA);

        orgB = new Organization();
        orgB.setId(UUID.randomUUID());
        orgB.setName("Hospital Org B");
        orgB.setSlug("hospital-b-" + UUID.randomUUID().toString().substring(0, 8));
        orgB.setStatus("ACTIVE");
        orgB.setCreatedAt(ZonedDateTime.now());
        orgB.setUpdatedAt(ZonedDateTime.now());
        orgB = organizationRepository.save(orgB);

        branchA = new Branch();
        branchA.setId(UUID.randomUUID());
        branchA.setName("Main Branch A");
        branchA.setCode("BR-01");
        branchA.setTimezone("UTC");
        branchA.setOrganization(orgA);
        branchA.setStatus("ACTIVE");
        branchA = branchRepository.save(branchA);

        serviceA = new Service();
        serviceA.setId(UUID.randomUUID());
        serviceA.setName("VIP Banking");
        serviceA.setOrganization(orgA);
        serviceA.setBranch(branchA);
        serviceA.setStatus("ACTIVE");
        serviceA = serviceRepository.save(serviceA);

        queueA = new Queue();
        queueA.setId(UUID.randomUUID());
        queueA.setName("VIP Desk 1");
        queueA.setTokenPrefix("T");
        queueA.setCurrentTokenNumber("T000");
        queueA.setCapacity(100);
        queueA.setOrganizationId(orgA.getId());
        queueA.setBranchId(branchA.getId());
        queueA.setServiceId(serviceA.getId());
        queueA.setStatus(QueueStatus.ACTIVE);
        queueA.setDefaultServiceTimeMinutes(10);
        queueA.setIsPriorityEnabled(true);
        queueA.setCreatedAt(ZonedDateTime.now());
        queueA.setUpdatedAt(ZonedDateTime.now());
        queueA = queueRepository.save(queueA);

        counterA = new Counter();
        counterA.setId(UUID.randomUUID());
        counterA.setName("Counter 01");
        counterA.setOrganization(orgA);
        counterA.setBranch(branchA);
        counterA.setStatus("ACTIVE");
        counterA.setCreatedAt(ZonedDateTime.now());
        counterA.setUpdatedAt(ZonedDateTime.now());
        counterA = counterRepository.save(counterA);

        Role adminRole = roleRepository.findByCode("ORGANIZATION_ADMIN").orElseThrow();
        Role customerRole = roleRepository.findByCode("CUSTOMER").orElseThrow();

        orgAdminA = new User();
        orgAdminA.setId(UUID.randomUUID());
        orgAdminA.setEmail("admin." + UUID.randomUUID() + "@orga.com");
        orgAdminA.setPasswordHash("pass");
        orgAdminA.setFullName("Admin A");
        orgAdminA.setStatus("ACTIVE");
        orgAdminA.setCreatedAt(ZonedDateTime.now());
        orgAdminA.setUpdatedAt(ZonedDateTime.now());
        orgAdminA = userRepository.save(orgAdminA);
        userRoleRepository.save(new UserRole(orgAdminA, adminRole, orgA));

        customerA = new User();
        customerA.setId(UUID.randomUUID());
        customerA.setEmail("cust." + UUID.randomUUID() + "@test.com");
        customerA.setPasswordHash("pass");
        customerA.setFullName("Customer A");
        customerA.setStatus("ACTIVE");
        customerA.setCreatedAt(ZonedDateTime.now());
        customerA.setUpdatedAt(ZonedDateTime.now());
        customerA = userRepository.save(customerA);
        userRoleRepository.save(new UserRole(customerA, customerRole, orgA));

        orgAdminAToken = jwtProvider.generateAccessToken(orgAdminA.getEmail(), orgAdminA.getId().toString());
        customerAToken = jwtProvider.generateAccessToken(customerA.getEmail(), customerA.getId().toString());

        // Seed 3 tokens: 1 Completed, 1 Cancelled, 1 Waiting
        ZonedDateTime now = ZonedDateTime.now();
        QueueToken t1 = new QueueToken();
        t1.setId(UUID.randomUUID());
        t1.setQueue(queueA);
        t1.setCustomer(customerA);
        t1.setTokenNumber("T-001");
        t1.setPriority(TokenPriority.VIP);
        t1.setStatus(TokenStatus.COMPLETED);
        t1.setCreatedAt(now.minusMinutes(30));
        t1.setCalledAt(now.minusMinutes(15));
        t1.setCompletedAt(now.minusMinutes(5));
        t1.setServedByCounterId(counterA.getId());
        t1.setServedByUserId(orgAdminA.getId());
        queueTokenRepository.save(t1);

        QueueToken t2 = new QueueToken();
        t2.setId(UUID.randomUUID());
        t2.setQueue(queueA);
        t2.setCustomer(customerA);
        t2.setTokenNumber("T-002");
        t2.setPriority(TokenPriority.NORMAL);
        t2.setStatus(TokenStatus.CANCELLED);
        t2.setCreatedAt(now.minusMinutes(25));
        t2.setCancelledAt(now.minusMinutes(10));
        queueTokenRepository.save(t2);

        QueueToken t3 = new QueueToken();
        t3.setId(UUID.randomUUID());
        t3.setQueue(queueA);
        t3.setCustomer(customerA);
        t3.setTokenNumber("T-003");
        t3.setPriority(TokenPriority.NORMAL);
        t3.setStatus(TokenStatus.WAITING);
        t3.setCreatedAt(now.minusMinutes(5));
        queueTokenRepository.save(t3);

        entityManager.flush();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Admin can retrieve operational overview analytics")
    void testGetOperationalOverviewSuccess() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + orgAdminAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTokens").value(3))
                .andExpect(jsonPath("$.data.completedTokens").value(1))
                .andExpect(jsonPath("$.data.cancelledTokens").value(1))
                .andExpect(jsonPath("$.data.waitingTokens").value(1))
                .andExpect(jsonPath("$.data.avgWaitMinutes").value(15.0))
                .andExpect(jsonPath("$.data.avgServiceMinutes").value(10.0));
    }

    @Test
    @DisplayName("Customer cannot access analytics endpoints (RBAC 403)")
    void testCustomerForbiddenFromAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + customerAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can retrieve queue performance analytics")
    void testGetQueuesAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/queues")
                        .header("Authorization", "Bearer " + orgAdminAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].queueName").value("VIP Desk 1"))
                .andExpect(jsonPath("$.data[0].totalTokens").value(3))
                .andExpect(jsonPath("$.data[0].completedTokens").value(1));
    }

    @Test
    @DisplayName("Admin can retrieve peak hours analytics")
    void testGetPeakHoursAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/peak-hours")
                        .header("Authorization", "Bearer " + orgAdminAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(24)));
    }

    @Test
    @DisplayName("Admin can retrieve wait time distribution percentiles")
    void testGetWaitTimeDistribution() throws Exception {
        mockMvc.perform(get("/api/analytics/wait-times")
                        .header("Authorization", "Bearer " + orgAdminAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.p50Minutes").isNumber());
    }

    @Test
    @DisplayName("Tenant isolation prevents Org B admin from viewing Org A analytics")
    void testTenantIsolationAnalytics() throws Exception {
        User orgAdminB = new User();
        orgAdminB.setId(UUID.randomUUID());
        orgAdminB.setEmail("admin." + UUID.randomUUID() + "@orgb.com");
        orgAdminB.setPasswordHash("pass");
        orgAdminB.setFullName("Admin B");
        orgAdminB.setStatus("ACTIVE");
        orgAdminB.setCreatedAt(ZonedDateTime.now());
        orgAdminB.setUpdatedAt(ZonedDateTime.now());
        orgAdminB = userRepository.save(orgAdminB);

        Role adminRole = roleRepository.findByCode("ORGANIZATION_ADMIN").orElseThrow();
        userRoleRepository.save(new UserRole(orgAdminB, adminRole, orgB));
        entityManager.flush();

        String orgAdminBToken = jwtProvider.generateAccessToken(orgAdminB.getEmail(), orgAdminB.getId().toString());

        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + orgAdminBToken)
                        .header("X-Organization-Id", orgB.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTokens").value(0))
                .andExpect(jsonPath("$.data.completedTokens").value(0));
    }
}
