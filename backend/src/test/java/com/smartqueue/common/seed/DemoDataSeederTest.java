package com.smartqueue.common.seed;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.auth.api.dto.LoginRequestDto;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.counter.infrastructure.CounterRepository;
import com.smartqueue.notification.infrastructure.NotificationRepository;
import com.smartqueue.notification.infrastructure.UserNotificationPreferenceRepository;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import com.smartqueue.service.infrastructure.ServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
public class DemoDataSeederTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private CounterRepository counterRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserNotificationPreferenceRepository userNotificationPreferenceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Test
    @DisplayName("Demo Seeder: Verify complete organization, branches, services, and queues")
    void testDemoOrganizationStructure() {
        var orgOpt = organizationRepository.findBySlug("citycare");
        assertTrue(orgOpt.isPresent(), "CityCare organization should be present");
        var org = orgOpt.get();
        assertEquals("CityCare Multi-Service Center", org.getName());
        assertEquals("citycare", org.getSlug());

        var branches = branchRepository.findByOrganizationId(org.getId());
        assertEquals(2, branches.size(), "Should have 2 branches");

        var services = serviceRepository.findByOrganizationId(org.getId());
        assertEquals(5, services.size(), "Should have 5 services");

        var queues = queueRepository.findByOrganizationId(org.getId());
        assertEquals(5, queues.size(), "Should have 5 queues");

        var counters = counterRepository.findByOrganizationId(org.getId());
        assertEquals(5, counters.size(), "Should have 5 counters");
    }

    @Test
    @DisplayName("Demo Seeder: Verify users, roles, and BCrypt passwords")
    void testDemoUsersAndAuthentication() throws Exception {
        // Org Admin
        var adminOpt = userRepository.findByEmail("admin@citycare.demo");
        assertTrue(adminOpt.isPresent(), "Admin user should exist");
        assertTrue(passwordEncoder.matches("Password123!", adminOpt.get().getPasswordHash()));

        // Staff
        var staffOpt = userRepository.findByEmail("staff.central1@citycare.demo");
        assertTrue(staffOpt.isPresent(), "Staff user should exist");
        assertTrue(passwordEncoder.matches("Password123!", staffOpt.get().getPasswordHash()));

        // Customer
        var customerOpt = userRepository.findByEmail("aarav@customer.demo");
        assertTrue(customerOpt.isPresent(), "Customer user should exist");
        assertTrue(passwordEncoder.matches("Password123!", customerOpt.get().getPasswordHash()));

        // Test login endpoint directly with demo credentials
        LoginRequestDto loginDto = new LoginRequestDto();
        loginDto.setEmail("admin@citycare.demo");
        loginDto.setPassword("Password123!");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(json).path("data").path("accessToken").asText();
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    @DisplayName("Demo Seeder: Verify tokens and lifecycle timestamps")
    void testDemoTokensLifecycle() {
        var org = organizationRepository.findBySlug("citycare").orElseThrow();
        List<Queue> queues = queueRepository.findByOrganizationId(org.getId());
        List<QueueToken> tokens = new ArrayList<>();
        for (Queue q : queues) {
            tokens.addAll(queueTokenRepository.findByQueueId(q.getId()));
        }
        assertFalse(tokens.isEmpty(), "Tokens should be seeded");
        assertEquals(19, tokens.size(), "Should have 19 seeded tokens across all queues");

        long completedCount = tokens.stream().filter(t -> t.getStatus() == TokenStatus.COMPLETED).count();
        long calledCount = tokens.stream().filter(t -> t.getStatus() == TokenStatus.CALLED).count();
        long holdCount = tokens.stream().filter(t -> t.getStatus() == TokenStatus.HOLD).count();
        long waitingCount = tokens.stream().filter(t -> t.getStatus() == TokenStatus.WAITING).count();
        long skippedCount = tokens.stream().filter(t -> t.getStatus() == TokenStatus.SKIPPED).count();
        long cancelledCount = tokens.stream().filter(t -> t.getStatus() == TokenStatus.CANCELLED).count();

        assertTrue(completedCount > 0, "Should have completed tokens");
        assertTrue(calledCount > 0, "Should have called tokens");
        assertTrue(holdCount > 0, "Should have hold tokens");
        assertTrue(waitingCount > 0, "Should have waiting tokens");
        assertTrue(skippedCount > 0, "Should have skipped tokens");
        assertTrue(cancelledCount > 0, "Should have cancelled tokens");

        // Verify chronological invariants
        for (QueueToken token : tokens) {
            assertNotNull(token.getCreatedAt(), "CreatedAt must not be null");
            if (token.getCalledAt() != null) {
                assertTrue(!token.getCalledAt().isBefore(token.getCreatedAt()), "CalledAt must be >= CreatedAt");
            }
            if (token.getCompletedAt() != null) {
                assertTrue(!token.getCompletedAt().isBefore(token.getCalledAt()), "CompletedAt must be >= CalledAt");
            }
        }
    }

    @Test
    @DisplayName("Demo Seeder: Verify notifications and preferences")
    void testDemoNotifications() {
        var org = organizationRepository.findBySlug("citycare").orElseThrow();
        var notificationsPage = notificationRepository.findByOrganizationIdOrderByCreatedAtDesc(org.getId(), Pageable.unpaged());
        assertEquals(7, notificationsPage.getContent().size(), "Should have 7 seeded notifications");

        var preferences = userNotificationPreferenceRepository.findAll();
        assertFalse(preferences.isEmpty(), "Notification preferences should be seeded for demo users");
    }

    @Test
    @DisplayName("Demo Seeder: Idempotency verification")
    void testIdempotentSeeding() throws Exception {
        // Re-run seeder manually
        demoDataSeeder.run();

        // Check counts remain identical
        var orgs = organizationRepository.findAll();
        assertEquals(1, orgs.stream().filter(o -> "citycare".equals(o.getSlug())).count(), "Should still have exactly 1 citycare organization");
    }
}
