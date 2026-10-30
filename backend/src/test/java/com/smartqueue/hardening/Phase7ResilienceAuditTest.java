package com.smartqueue.hardening;

import com.smartqueue.ai.api.dto.AiEtaResponse;
import com.smartqueue.ai.application.AiService;
import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.notification.application.NotificationService;
import com.smartqueue.notification.domain.Notification;
import com.smartqueue.notification.domain.NotificationChannel;
import com.smartqueue.notification.domain.NotificationEventType;
import com.smartqueue.notification.domain.NotificationStatus;
import com.smartqueue.notification.infrastructure.NotificationRepository;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class Phase7ResilienceAuditTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private AiService aiService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Organization createOrg(String name) {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName(name);
        org.setSlug("org-" + UUID.randomUUID().toString().substring(0, 8));
        org.setStatus("ACTIVE");
        org.setCreatedAt(ZonedDateTime.now());
        org.setUpdatedAt(ZonedDateTime.now());
        return organizationRepository.save(org);
    }

    private User createUser(Organization org, String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setPasswordHash("$2a$10$dummyhash");
        u.setFullName("User " + email);
        u.setStatus("ACTIVE");
        u.setCreatedAt(ZonedDateTime.now());
        u.setUpdatedAt(ZonedDateTime.now());
        u = userRepository.save(u);

        Role role = roleRepository.findByCode("CUSTOMER").orElseGet(() -> {
            Role r = new Role();
            r.setId(UUID.randomUUID());
            r.setCode("CUSTOMER");
            r.setName("Customer");
            return roleRepository.save(r);
        });

        UserRole ur = new UserRole(u, role, org);
        userRoleRepository.save(ur);
        return u;
    }

    private Queue createQueue(Organization org, int defaultServiceMins) {
        Queue q = new Queue();
        q.setId(UUID.randomUUID());
        q.setName("Resilience Queue");
        q.setOrganizationId(org.getId());
        q.setTokenPrefix("R");
        q.setCurrentTokenNumber("R000");
        q.setStatus(QueueStatus.ACTIVE);
        q.setDefaultServiceTimeMinutes(defaultServiceMins);
        q.setIsPriorityEnabled(false);
        q.setCreatedAt(ZonedDateTime.now());
        q.setUpdatedAt(ZonedDateTime.now());
        return queueRepository.save(q);
    }

    @Test
    @DisplayName("Resilience: AI Service Failure Falls Back Safely to Deterministic ETA Calculation")
    void testAiServiceUnavailableFallback() {
        Organization org = createOrg("AI Resilience Org");
        Queue queue = createQueue(org, 6);

        User cust1 = createUser(org, "cust1_" + UUID.randomUUID() + "@test.com");
        User cust2 = createUser(org, "cust2_" + UUID.randomUUID() + "@test.com");
        User cust3 = createUser(org, "cust3_" + UUID.randomUUID() + "@test.com");

        TenantContext.setCurrentTenant(org.getId());

        QueueToken t1 = queueService.generateToken(queue.getId(), cust1);
        QueueToken t2 = queueService.generateToken(queue.getId(), cust2);
        QueueToken t3 = queueService.generateToken(queue.getId(), cust3);

        // Call predictEta when Python AI microservice is not running in test env
        AiEtaResponse prediction = aiService.predictEta(t3.getId());

        assertNotNull(prediction, "Prediction must never be null on AI unavailability");
        assertEquals(3, prediction.position(), "Position should match tokens in line");
        // Ahead of t3 is t1 and t2 = 2 tokens ahead. Deterministic estimate = 2 * 6 = 12 mins.
        assertEquals(12, prediction.estimatedWaitMinutes(), "Estimated wait must equal deterministic fallback");
        assertEquals("DETERMINISTIC", prediction.predictionType());
        assertNotNull(prediction.factors());
        assertEquals(2L, ((Number) prediction.factors().get("tokensAhead")).longValue());
    }

    @Test
    @DisplayName("Resilience: Notification Dispatch Survives & Persists in PostgreSQL")
    @Transactional
    void testNotificationPersistenceAndDeliveryResilience() {
        Organization org = createOrg("Notification Resilience Org");
        User user = createUser(org, "notif_user_" + UUID.randomUUID() + "@test.com");
        Queue queue = createQueue(org, 5);

        TenantContext.setCurrentTenant(org.getId());

        Notification notif = new Notification();
        notif.setOrganization(org);
        notif.setUser(user);
        notif.setQueue(queue);
        notif.setChannel(NotificationChannel.IN_APP);
        notif.setEventType(NotificationEventType.TOKEN_CALLED);
        notif.setTitle("Your Turn");
        notif.setMessage("Please proceed to counter");
        notif.setStatus(NotificationStatus.SENT);
        Notification saved = notificationRepository.save(notif);

        assertNotNull(saved.getId(), "Notification must be persisted in PostgreSQL");
        var retrieved = notificationRepository.findById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(NotificationStatus.SENT, retrieved.get().getStatus());
    }

    @Test
    @DisplayName("Resilience: Core Queue Operations Function Directly & Authoritatively from PostgreSQL")
    void testCoreQueueAuthoritativeStateWithoutRedis() {
        Organization org = createOrg("Authoritative Core Org");
        Queue queue = createQueue(org, 5);
        User user = createUser(org, "core_user_" + UUID.randomUUID() + "@test.com");

        TenantContext.setCurrentTenant(org.getId());

        // Join
        QueueToken token = queueService.generateToken(queue.getId(), user);
        assertNotNull(token);
        assertEquals("R001", token.getTokenNumber());

        // Call next
        QueueToken called = queueService.callNext(queue.getId());
        assertNotNull(called);
        assertEquals(token.getId(), called.getId());

        // Complete
        QueueToken completed = queueService.completeToken(token.getId());
        assertNotNull(completed);
        assertEquals(com.smartqueue.queue.domain.TokenStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
    }
}
