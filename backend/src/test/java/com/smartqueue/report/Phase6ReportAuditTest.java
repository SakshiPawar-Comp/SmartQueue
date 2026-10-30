package com.smartqueue.report;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Phase6ReportAuditTest {

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
        orgA.setName("Report Test Org");
        orgA.setSlug("report-org-" + UUID.randomUUID().toString().substring(0, 8));
        orgA.setStatus("ACTIVE");
        orgA.setCreatedAt(ZonedDateTime.now());
        orgA.setUpdatedAt(ZonedDateTime.now());
        orgA = organizationRepository.save(orgA);

        branchA = new Branch();
        branchA.setId(UUID.randomUUID());
        branchA.setName("North Hub");
        branchA.setCode("BR-02");
        branchA.setTimezone("UTC");
        branchA.setOrganization(orgA);
        branchA.setStatus("ACTIVE");
        branchA = branchRepository.save(branchA);

        serviceA = new Service();
        serviceA.setId(UUID.randomUUID());
        serviceA.setName("Express Service");
        serviceA.setOrganization(orgA);
        serviceA.setBranch(branchA);
        serviceA.setStatus("ACTIVE");
        serviceA = serviceRepository.save(serviceA);

        queueA = new Queue();
        queueA.setId(UUID.randomUUID());
        queueA.setName("Express Counter Q");
        queueA.setTokenPrefix("E");
        queueA.setCurrentTokenNumber("E000");
        queueA.setCapacity(50);
        queueA.setOrganizationId(orgA.getId());
        queueA.setBranchId(branchA.getId());
        queueA.setServiceId(serviceA.getId());
        queueA.setStatus(QueueStatus.ACTIVE);
        queueA.setDefaultServiceTimeMinutes(5);
        queueA.setIsPriorityEnabled(false);
        queueA.setCreatedAt(ZonedDateTime.now());
        queueA.setUpdatedAt(ZonedDateTime.now());
        queueA = queueRepository.save(queueA);

        counterA = new Counter();
        counterA.setId(UUID.randomUUID());
        counterA.setName("Counter E1");
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
        orgAdminA.setEmail("admin." + UUID.randomUUID() + "@report.com");
        orgAdminA.setPasswordHash("pass");
        orgAdminA.setFullName("Admin Report");
        orgAdminA.setStatus("ACTIVE");
        orgAdminA.setCreatedAt(ZonedDateTime.now());
        orgAdminA.setUpdatedAt(ZonedDateTime.now());
        orgAdminA = userRepository.save(orgAdminA);
        userRoleRepository.save(new UserRole(orgAdminA, adminRole, orgA));

        customerA = new User();
        customerA.setId(UUID.randomUUID());
        customerA.setEmail("cust." + UUID.randomUUID() + "@report.com");
        customerA.setPasswordHash("pass");
        customerA.setFullName("Customer R");
        customerA.setStatus("ACTIVE");
        customerA.setCreatedAt(ZonedDateTime.now());
        customerA.setUpdatedAt(ZonedDateTime.now());
        customerA = userRepository.save(customerA);
        userRoleRepository.save(new UserRole(customerA, customerRole, orgA));

        orgAdminAToken = jwtProvider.generateAccessToken(orgAdminA.getEmail(), orgAdminA.getId().toString());
        customerAToken = jwtProvider.generateAccessToken(customerA.getEmail(), customerA.getId().toString());

        ZonedDateTime now = ZonedDateTime.now();
        QueueToken t1 = new QueueToken();
        t1.setId(UUID.randomUUID());
        t1.setQueue(queueA);
        t1.setCustomer(customerA);
        t1.setTokenNumber("E-101");
        t1.setPriority(TokenPriority.NORMAL);
        t1.setStatus(TokenStatus.COMPLETED);
        t1.setCreatedAt(now.minusMinutes(20));
        t1.setCalledAt(now.minusMinutes(10));
        t1.setCompletedAt(now.minusMinutes(2));
        t1.setServedByCounterId(counterA.getId());
        t1.setServedByUserId(orgAdminA.getId());
        queueTokenRepository.save(t1);

        entityManager.flush();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Admin can export daily operational CSV report")
    void testDailyCsvReportExport() throws Exception {
        byte[] csvBytes = mockMvc.perform(get("/api/reports/daily")
                        .param("format", "csv")
                        .header("Authorization", "Bearer " + orgAdminAToken)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String content = new String(csvBytes);
        assertTrue(content.contains("Date,Total Tokens,Completed,Cancelled,Skipped"));
    }

    @Test
    @DisplayName("Admin can export queues summary CSV report")
    void testQueuesCsvReportExport() throws Exception {
        byte[] csvBytes = mockMvc.perform(get("/api/reports/queues")
                        .param("format", "csv")
                        .header("Authorization", "Bearer " + orgAdminAToken)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String content = new String(csvBytes);
        assertTrue(content.contains("Queue Name,Token Prefix,Branch,Service,Total Tokens"));
        assertTrue(content.contains("Express Counter Q"));
    }

    @Test
    @DisplayName("Admin can get JSON summary report preview")
    void testSummaryReportPreview() throws Exception {
        mockMvc.perform(get("/api/reports/summary")
                        .header("Authorization", "Bearer " + orgAdminAToken)
                        .header("X-Organization-Id", orgA.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTokens").value(1))
                .andExpect(jsonPath("$.data.completedTokens").value(1));
    }

    @Test
    @DisplayName("Customer cannot access reports endpoints (RBAC 403)")
    void testCustomerForbiddenFromReports() throws Exception {
        mockMvc.perform(get("/api/reports/daily")
                        .header("Authorization", "Bearer " + customerAToken)
                        .header("X-Organization-Id", orgA.getId().toString()))
                .andExpect(status().isForbidden());
    }
}
