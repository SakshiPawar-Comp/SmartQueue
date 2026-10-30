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
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerControllerTest {

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
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private QueueService queueService;

    @Autowired
    private EntityManager entityManager;

    private Organization organization;
    private User customer1;
    private User customer2;
    private String customer1Jwt;
    private String customer2Jwt;
    private Queue queue;

    @BeforeEach
    void setUp() {
        organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("Customer Test Org");
        organization.setSlug("customer-org-" + UUID.randomUUID().toString().substring(0, 8));
        organization.setStatus("ACTIVE");
        organization.setCreatedAt(ZonedDateTime.now());
        organization.setUpdatedAt(ZonedDateTime.now());
        organization = organizationRepository.save(organization);

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setCode("CUSTOMER");
                    r.setName("Customer");
                    return roleRepository.save(r);
                });

        customer1 = new User();
        customer1.setId(UUID.randomUUID());
        customer1.setEmail("customer1-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com");
        customer1.setPasswordHash(passwordEncoder.encode("Password123!"));
        customer1.setFullName("Customer One");
        customer1.setPhone("+1234567890");
        customer1.setPreferredLanguage("en");
        customer1.setStatus("ACTIVE");
        customer1.setCreatedAt(ZonedDateTime.now());
        customer1.setUpdatedAt(ZonedDateTime.now());
        customer1 = userRepository.save(customer1);
        userRoleRepository.save(new UserRole(customer1, customerRole, organization));

        customer2 = new User();
        customer2.setId(UUID.randomUUID());
        customer2.setEmail("customer2-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com");
        customer2.setPasswordHash(passwordEncoder.encode("Password123!"));
        customer2.setFullName("Customer Two");
        customer2.setStatus("ACTIVE");
        customer2.setCreatedAt(ZonedDateTime.now());
        customer2.setUpdatedAt(ZonedDateTime.now());
        customer2 = userRepository.save(customer2);
        userRoleRepository.save(new UserRole(customer2, customerRole, organization));

        queue = new Queue(organization.getId(), "Customer Service Queue");
        queue.setStatus(QueueStatus.ACTIVE);
        queue = queueRepository.save(queue);

        entityManager.flush();

        customer1Jwt = jwtProvider.generateAccessToken(customer1.getEmail(), customer1.getId().toString());
        customer2Jwt = jwtProvider.generateAccessToken(customer2.getEmail(), customer2.getId().toString());
    }

    @AfterEach
    void tearDown() {
        com.smartqueue.common.tenant.TenantContext.clear();
    }

    @Test
    void getActiveTokens_ReturnsOnlyActiveTokensForAuthenticatedCustomer() throws Exception {
        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(organization.getId());
        // Customer 1 generates a token
        QueueToken token1 = queueService.generateToken(queue.getId(), customer1);
        // Customer 2 generates a token
        QueueToken token2 = queueService.generateToken(queue.getId(), customer2);
        com.smartqueue.common.tenant.TenantContext.clear();

        // Customer 1 requests active tokens
        mockMvc.perform(get("/api/customers/me/tokens/active")
                        .header("Authorization", "Bearer " + customer1Jwt)
                        .header("X-Organization-Id", organization.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(token1.getId().toString()))
                .andExpect(jsonPath("$.data[0].status").value("WAITING"))
                .andExpect(jsonPath("$.data[0].queueName").value("Customer Service Queue"))
                .andExpect(jsonPath("$.data[0].position").value(1))
                .andExpect(jsonPath("$.data[0].peopleAhead").value(0))
                .andExpect(jsonPath("$.data[0].estimatedWaitMinutes").value(0));
    }

    @Test
    void getCustomerHistory_PaginatedAndFilteredByStatus() throws Exception {
        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(organization.getId());
        QueueToken token1 = queueService.generateToken(queue.getId(), customer1);

        // Complete token1 so customer can generate next token
        queueService.callNext(queue.getId());
        queueService.completeToken(token1.getId());

        // Now generate token2 and cancel it
        QueueToken token2 = queueService.generateToken(queue.getId(), customer1);
        queueService.cancelToken(token2.getId(), customer1, false);
        com.smartqueue.common.tenant.TenantContext.clear();

        // Fetch all history
        mockMvc.perform(get("/api/customers/me/tokens/history?page=0&size=10")
                        .header("Authorization", "Bearer " + customer1Jwt)
                        .header("X-Organization-Id", organization.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items", hasSize(2)));

        // Fetch completed history only
        mockMvc.perform(get("/api/customers/me/tokens/history?page=0&size=10&status=COMPLETED")
                        .header("Authorization", "Bearer " + customer1Jwt)
                        .header("X-Organization-Id", organization.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"));
    }

    @Test
    void updateProfile_UpdatesFullNamePhoneAndLanguage() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");
        request.setPhone("+9876543210");
        request.setPreferredLanguage("hi");

        mockMvc.perform(put("/api/customers/me/profile")
                        .header("Authorization", "Bearer " + customer1Jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.data.phone").value("+9876543210"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("hi"));

        User reloaded = userRepository.findById(customer1.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Updated Name", reloaded.getFullName());
        org.junit.jupiter.api.Assertions.assertEquals("+9876543210", reloaded.getPhone());
        org.junit.jupiter.api.Assertions.assertEquals("hi", reloaded.getPreferredLanguage());
    }

    @Test
    void changePassword_SuccessAndIncorrectPasswordValidation() throws Exception {
        // Wrong current password
        ChangePasswordRequest wrongReq = new ChangePasswordRequest();
        wrongReq.setCurrentPassword("WrongPass123!");
        wrongReq.setNewPassword("NewSecurePassword123!");

        mockMvc.perform(post("/api/customers/me/change-password")
                        .header("Authorization", "Bearer " + customer1Jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        // Correct current password
        ChangePasswordRequest correctReq = new ChangePasswordRequest();
        correctReq.setCurrentPassword("Password123!");
        correctReq.setNewPassword("NewSecurePassword123!");

        mockMvc.perform(post("/api/customers/me/change-password")
                        .header("Authorization", "Bearer " + customer1Jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctReq)))
                .andExpect(status().isOk());

        User reloaded = userRepository.findById(customer1.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("NewSecurePassword123!", reloaded.getPasswordHash()));
    }
}
