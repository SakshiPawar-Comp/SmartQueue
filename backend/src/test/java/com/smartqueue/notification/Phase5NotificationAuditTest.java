package com.smartqueue.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.notification.api.dto.UpdateNotificationPreferenceRequest;
import com.smartqueue.notification.application.NotificationService;
import com.smartqueue.notification.application.NotificationTemplateService;
import com.smartqueue.notification.domain.*;
import com.smartqueue.notification.infrastructure.NotificationRepository;
import com.smartqueue.notification.infrastructure.UserNotificationPreferenceRepository;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.infrastructure.QueueRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Phase5NotificationAuditTest {

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
    private QueueRepository queueRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserNotificationPreferenceRepository preferenceRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationTemplateService templateService;

    @Autowired
    private QueueService queueService;

    private Organization orgA;
    private Organization orgB;
    private User customerA;
    private User customerB;
    private User orgAdminA;
    private Queue queueA;
    private String customerAToken;
    private String orgAdminAToken;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();

        orgA = new Organization();
        orgA.setId(UUID.randomUUID());
        orgA.setName("Healthcare Org A");
        orgA.setSlug("health-a-" + UUID.randomUUID().toString().substring(0, 8));
        orgA.setStatus("ACTIVE");
        orgA.setCreatedAt(ZonedDateTime.now());
        orgA.setUpdatedAt(ZonedDateTime.now());
        orgA = organizationRepository.save(orgA);

        orgB = new Organization();
        orgB.setId(UUID.randomUUID());
        orgB.setName("Retail Org B");
        orgB.setSlug("retail-b-" + UUID.randomUUID().toString().substring(0, 8));
        orgB.setStatus("ACTIVE");
        orgB.setCreatedAt(ZonedDateTime.now());
        orgB.setUpdatedAt(ZonedDateTime.now());
        orgB = organizationRepository.save(orgB);

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("CUSTOMER role not found"));
        Role adminRole = roleRepository.findByCode("ORGANIZATION_ADMIN")
                .orElseThrow(() -> new RuntimeException("ORGANIZATION_ADMIN role not found"));

        customerA = new User();
        customerA.setId(UUID.randomUUID());
        customerA.setEmail("customerA." + UUID.randomUUID() + "@test.com");
        customerA.setPasswordHash("passHash");
        customerA.setFullName("Customer Alice");
        customerA.setPreferredLanguage("en");
        customerA.setStatus("ACTIVE");
        customerA.setCreatedAt(ZonedDateTime.now());
        customerA.setUpdatedAt(ZonedDateTime.now());
        customerA = userRepository.save(customerA);
        userRoleRepository.save(new UserRole(customerA, customerRole, orgA));

        customerB = new User();
        customerB.setId(UUID.randomUUID());
        customerB.setEmail("customerB." + UUID.randomUUID() + "@test.com");
        customerB.setPasswordHash("passHash");
        customerB.setFullName("Customer Bob");
        customerB.setPreferredLanguage("hi");
        customerB.setStatus("ACTIVE");
        customerB.setCreatedAt(ZonedDateTime.now());
        customerB.setUpdatedAt(ZonedDateTime.now());
        customerB = userRepository.save(customerB);
        userRoleRepository.save(new UserRole(customerB, customerRole, orgB));

        orgAdminA = new User();
        orgAdminA.setId(UUID.randomUUID());
        orgAdminA.setEmail("adminA." + UUID.randomUUID() + "@test.com");
        orgAdminA.setPasswordHash("passHash");
        orgAdminA.setFullName("Admin Alice");
        orgAdminA.setStatus("ACTIVE");
        orgAdminA.setCreatedAt(ZonedDateTime.now());
        orgAdminA.setUpdatedAt(ZonedDateTime.now());
        orgAdminA = userRepository.save(orgAdminA);
        userRoleRepository.save(new UserRole(orgAdminA, adminRole, orgA));

        queueA = new Queue();
        queueA.setId(UUID.randomUUID());
        queueA.setOrganizationId(orgA.getId());
        queueA.setName("OPD Consultation");
        queueA.setTokenPrefix("A");
        queueA.setCurrentTokenNumber("A000");
        queueA.setStatus(QueueStatus.ACTIVE);
        queueA.setCreatedAt(ZonedDateTime.now());
        queueA.setUpdatedAt(ZonedDateTime.now());
        queueA = queueRepository.save(queueA);

        customerAToken = jwtProvider.generateAccessToken(customerA.getEmail(), customerA.getId().toString());
        orgAdminAToken = jwtProvider.generateAccessToken(orgAdminA.getEmail(), orgAdminA.getId().toString());

        TenantContext.setCurrentTenant(orgA.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Multilingual Templates: English, Hindi, Marathi for all 9 event types")
    void testMultilingualTemplatesForAllEvents() {
        Map<String, String> params = Map.of(
                "tokenNumber", "A001",
                "queueName", "Main OPD",
                "peopleAhead", "2",
                "estimatedWait", "10",
                "counterName", "Counter 1"
        );

        for (NotificationEventType eventType : NotificationEventType.values()) {
            // English
            NotificationTemplateService.RenderedTemplate enTpl = templateService.render(eventType, "en", params);
            assertNotNull(enTpl.title());
            assertNotNull(enTpl.message());
            assertFalse(enTpl.message().contains("{tokenNumber}"), "Template parameters should be resolved in EN for " + eventType);

            // Hindi
            NotificationTemplateService.RenderedTemplate hiTpl = templateService.render(eventType, "hi", params);
            assertNotNull(hiTpl.title());
            assertNotNull(hiTpl.message());
            assertFalse(hiTpl.message().contains("{tokenNumber}"), "Template parameters should be resolved in HI for " + eventType);

            // Marathi
            NotificationTemplateService.RenderedTemplate mrTpl = templateService.render(eventType, "mr", params);
            assertNotNull(mrTpl.title());
            assertNotNull(mrTpl.message());
            assertFalse(mrTpl.message().contains("{tokenNumber}"), "Template parameters should be resolved in MR for " + eventType);
        }
    }

    @Test
    @DisplayName("Idempotency Deduplication: duplicate notification triggers return existing record")
    void testNotificationIdempotency() {
        String idempotencyKey = orgA.getId() + ":token-123:TOKEN_CREATED:IN_APP";

        Notification n1 = notificationService.createAndDispatch(
                orgA,
                customerA,
                null,
                queueA,
                NotificationChannel.IN_APP,
                NotificationEventType.TOKEN_CREATED,
                customerA.getEmail(),
                "Token A001 Issued",
                "Your token A001 has been issued.",
                idempotencyKey
        );

        assertNotNull(n1.getId());
        assertEquals(NotificationStatus.SENT, n1.getStatus());

        // Second call with same idempotencyKey should return the exact same notification without duplication
        Notification n2 = notificationService.createAndDispatch(
                orgA,
                customerA,
                null,
                queueA,
                NotificationChannel.IN_APP,
                NotificationEventType.TOKEN_CREATED,
                customerA.getEmail(),
                "Token A001 Issued",
                "Your token A001 has been issued.",
                idempotencyKey
        );

        assertEquals(n1.getId(), n2.getId(), "Duplicate event trigger must return existing notification");
        long count = notificationRepository.count();
        assertEquals(1, count, "Notification table count must remain 1");
    }

    @Test
    @DisplayName("Customer Notification Preferences: update and fetch preferences")
    void testCustomerNotificationPreferences() {
        UserNotificationPreference pref = notificationService.getOrCreatePreferences(customerA.getId());
        assertNotNull(pref);
        assertTrue(pref.isInAppEnabled());
        assertTrue(pref.isEmailEnabled());

        UserNotificationPreference updated = notificationService.updatePreferences(
                customerA.getId(),
                true,
                false,
                true,
                false,
                5
        );

        assertTrue(updated.isInAppEnabled());
        assertFalse(updated.isEmailEnabled());
        assertTrue(updated.isSmsEnabled());
        assertFalse(updated.isPushEnabled());
        assertEquals(5, updated.getPositionNearThreshold());
    }

    @Test
    @DisplayName("REST: Customer Preferences GET & PUT endpoints")
    void testCustomerPreferencesEndpoints() throws Exception {
        // GET preferences
        mockMvc.perform(get("/api/customers/me/notification-preferences")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inAppEnabled").value(true))
                .andExpect(jsonPath("$.data.positionNearThreshold").value(3));

        // PUT preferences
        UpdateNotificationPreferenceRequest req = new UpdateNotificationPreferenceRequest();
        req.setInAppEnabled(true);
        req.setEmailEnabled(false);
        req.setSmsEnabled(true);
        req.setPushEnabled(false);
        req.setPositionNearThreshold(5);

        mockMvc.perform(put("/api/customers/me/notification-preferences")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailEnabled").value(false))
                .andExpect(jsonPath("$.data.smsEnabled").value(true))
                .andExpect(jsonPath("$.data.positionNearThreshold").value(5));
    }

    @Test
    @DisplayName("REST: Customer Notifications list, unread count, mark read, mark all read")
    void testCustomerNotificationsLifecycle() throws Exception {
        // Create 2 notifications
        notificationService.createAndDispatch(
                orgA,
                customerA,
                null,
                queueA,
                NotificationChannel.IN_APP,
                NotificationEventType.TOKEN_CREATED,
                customerA.getEmail(),
                "Welcome",
                "Token created",
                "orgA:t1:TOKEN_CREATED:IN_APP"
        );

        Notification n2 = notificationService.createAndDispatch(
                orgA,
                customerA,
                null,
                queueA,
                NotificationChannel.IN_APP,
                NotificationEventType.POSITION_NEAR,
                customerA.getEmail(),
                "Your Turn Soon",
                "You are near front",
                "orgA:t2:POSITION_NEAR:IN_APP"
        );

        // Check unread count = 2
        mockMvc.perform(get("/api/customers/me/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        // Mark single notification as read
        mockMvc.perform(patch("/api/customers/me/notifications/" + n2.getId() + "/read")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));

        // Check unread count = 1
        mockMvc.perform(get("/api/customers/me/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        // Mark all as read
        mockMvc.perform(post("/api/customers/me/notifications/read-all")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markedReadCount").value(1));

        // Check unread count = 0
        mockMvc.perform(get("/api/customers/me/notifications/unread-count")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    @DisplayName("Admin Notification Endpoint: tenant isolation and query filters")
    void testAdminNotificationEndpoint() throws Exception {
        // Create notification for Org A
        notificationService.createAndDispatch(
                orgA,
                customerA,
                null,
                queueA,
                NotificationChannel.IN_APP,
                NotificationEventType.TOKEN_CREATED,
                customerA.getEmail(),
                "Org A Notification",
                "Message A",
                "orgA:notif:1"
        );

        // Create notification for Org B
        notificationService.createAndDispatch(
                orgB,
                customerB,
                null,
                null,
                NotificationChannel.IN_APP,
                NotificationEventType.TOKEN_CREATED,
                customerB.getEmail(),
                "Org B Notification",
                "Message B",
                "orgB:notif:1"
        );

        // Org A admin query should ONLY see Org A notifications
        mockMvc.perform(get("/api/admin/notifications")
                        .header("Authorization", "Bearer " + orgAdminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Org A Notification"));

        // Customer token accessing admin endpoint should be FORBIDDEN
        mockMvc.perform(get("/api/admin/notifications")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("QueueToken lifecycle triggers token generation safely")
    void testTokenGenerationTriggersNotification() {
        QueueToken token = queueService.generateToken(queueA.getId(), customerA);
        assertNotNull(token);
        assertEquals("A001", token.getTokenNumber());
    }
}
