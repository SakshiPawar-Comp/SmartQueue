package com.smartqueue.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.service.domain.Service;
import com.smartqueue.service.infrastructure.ServiceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class DiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private EntityManager entityManager;

    private Organization org1;
    private Branch branch1;
    private Service service1;
    private Queue queue1;
    private String customerJwt;

    @BeforeEach
    void setUp() {
        org1 = new Organization();
        org1.setId(UUID.randomUUID());
        org1.setName("Main Hospital");
        org1.setSlug("main-hosp-" + UUID.randomUUID().toString().substring(0, 8));
        org1.setStatus("ACTIVE");
        org1.setCreatedAt(ZonedDateTime.now());
        org1.setUpdatedAt(ZonedDateTime.now());
        org1 = organizationRepository.save(org1);

        branch1 = new Branch();
        branch1.setId(UUID.randomUUID());
        branch1.setOrganization(org1);
        branch1.setName("Central Wing");
        branch1.setCode("MAIN_BR");
        branch1.setTimezone("UTC");
        branch1.setStatus("ACTIVE");
        branch1 = branchRepository.save(branch1);

        service1 = new Service(org1, "General OPD");
        service1.setBranch(branch1);
        service1.setStatus("ACTIVE");
        service1 = serviceRepository.save(service1);

        queue1 = new Queue(org1.getId(), "OPD Room 1");
        queue1.setBranchId(branch1.getId());
        queue1.setServiceId(service1.getId());
        queue1.setStatus(QueueStatus.ACTIVE);
        queue1 = queueRepository.save(queue1);

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setCode("CUSTOMER");
                    r.setName("Customer");
                    return roleRepository.save(r);
                });

        User customer = new User();
        customer.setId(UUID.randomUUID());
        customer.setEmail("cust-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com");
        customer.setPasswordHash(passwordEncoder.encode("Password123!"));
        customer.setFullName("Test Customer");
        customer.setStatus("ACTIVE");
        customer.setCreatedAt(ZonedDateTime.now());
        customer.setUpdatedAt(ZonedDateTime.now());
        customer = userRepository.save(customer);
        userRoleRepository.save(new UserRole(customer, customerRole, org1));

        entityManager.flush();

        customerJwt = jwtProvider.generateAccessToken(customer.getEmail(), customer.getId().toString());
    }

    @AfterEach
    void tearDown() {
        com.smartqueue.common.tenant.TenantContext.clear();
    }

    @Test
    void listOrganizations_ReturnsActiveOrganizations() throws Exception {
        mockMvc.perform(get("/api/organizations")
                        .header("Authorization", "Bearer " + customerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[?(@.id == '" + org1.getId() + "')].name").value("Main Hospital"));
    }

    @Test
    void getBranchesByOrganization_ReturnsBranches() throws Exception {
        mockMvc.perform(get("/api/organizations/" + org1.getId() + "/branches")
                        .header("Authorization", "Bearer " + customerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Central Wing"));
    }

    @Test
    void getServicesByBranch_ReturnsServices() throws Exception {
        mockMvc.perform(get("/api/branches/" + branch1.getId() + "/services")
                        .header("Authorization", "Bearer " + customerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("General OPD"));
    }

    @Test
    void getQueuesByBranchAndService_ReturnsQueuesWithWaitEstimates() throws Exception {
        mockMvc.perform(get("/api/branches/" + branch1.getId() + "/queues")
                        .header("Authorization", "Bearer " + customerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("OPD Room 1"))
                .andExpect(jsonPath("$.data[0].waitingCount").value(0))
                .andExpect(jsonPath("$.data[0].estimatedWaitMinutes").value(0));

        mockMvc.perform(get("/api/services/" + service1.getId() + "/queues")
                        .header("Authorization", "Bearer " + customerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("OPD Room 1"));
    }

    @Test
    void getQueuesWithQueryParameters_FiltersCorrectly() throws Exception {
        mockMvc.perform(get("/api/queues?branchId=" + branch1.getId() + "&serviceId=" + service1.getId())
                        .header("Authorization", "Bearer " + customerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("OPD Room 1"));
    }
}
