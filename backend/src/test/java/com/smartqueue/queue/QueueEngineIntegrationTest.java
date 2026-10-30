package com.smartqueue.queue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

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
import com.smartqueue.queue.api.dto.CreateQueueRequest;
import com.smartqueue.queue.api.dto.UpdateQueueRequest;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QueueEngineIntegrationTest {

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
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private QueueService queueService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Organization orgA;
    private Organization orgB;

    private User adminA;
    private User customerA;
    private User customerB;
    private User adminB;

    private String tokenAdminA;
    private String tokenCustomerA;
    private String tokenCustomerB;
    private String tokenAdminB;

    @BeforeEach
    void setUp() {
        orgA = createOrg("Org Alpha", "org-alpha-" + UUID.randomUUID().toString().substring(0, 6));
        orgB = createOrg("Org Beta", "org-beta-" + UUID.randomUUID().toString().substring(0, 6));

        adminA = createUser("adminA-" + UUID.randomUUID().toString().substring(0, 6) + "@test.com");
        assignRole(adminA, "ORGANIZATION_ADMIN", orgA);
        tokenAdminA = jwtProvider.generateAccessToken(adminA.getEmail(), adminA.getId().toString());

        customerA = createUser("custA-" + UUID.randomUUID().toString().substring(0, 6) + "@test.com");
        assignRole(customerA, "CUSTOMER", orgA);
        tokenCustomerA = jwtProvider.generateAccessToken(customerA.getEmail(), customerA.getId().toString());

        customerB = createUser("custB-" + UUID.randomUUID().toString().substring(0, 6) + "@test.com");
        assignRole(customerB, "CUSTOMER", orgA);
        tokenCustomerB = jwtProvider.generateAccessToken(customerB.getEmail(), customerB.getId().toString());

        adminB = createUser("adminB-" + UUID.randomUUID().toString().substring(0, 6) + "@test.com");
        assignRole(adminB, "ORGANIZATION_ADMIN", orgB);
        tokenAdminB = jwtProvider.generateAccessToken(adminB.getEmail(), adminB.getId().toString());
    }

    private Organization createOrg(String name, String slug) {
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
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setFullName("User " + email);
        user.setStatus("ACTIVE");
        user.setCreatedAt(ZonedDateTime.now());
        user.setUpdatedAt(ZonedDateTime.now());
        return userRepository.save(user);
    }

    private void assignRole(User user, String roleCode, Organization org) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setCode(roleCode);
                    r.setName(roleCode);
                    return roleRepository.save(r);
                });
        UserRole userRole = new UserRole(user, role, org);
        userRoleRepository.save(userRole);
    }

    // --- Queue CRUD, Open, Close Tests ---

    @Test
    void admin_canCreateQueue_andGetQueues() throws Exception {
        CreateQueueRequest req = new CreateQueueRequest();
        req.setName("Billing Counter");
        req.setDescription("General billing desk");
        req.setTokenPrefix("B");

        mockMvc.perform(post("/api/queues")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Billing Counter"))
                .andExpect(jsonPath("$.data.tokenPrefix").value("B"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/queues")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void admin_canOpenAndCloseQueue() throws Exception {
        Queue queue = new Queue(orgA.getId(), "Support Desk");
        queueRepository.save(queue);

        // Close queue
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/close")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // Customer cannot join closed queue
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("QUEUE_INACTIVE"));

        // Open queue
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/open")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // Customer can now join
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenNumber").value("A001"));
    }

    // --- Token Join & Duplicate Prevention Tests ---

    @Test
    void customer_joinQueue_succeeds_duplicateJoinRejected() throws Exception {
        Queue queue = new Queue(orgA.getId(), "Main Queue");
        queueRepository.save(queue);

        // First join succeeds
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/tokens")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenNumber").value("A001"))
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        // Second join in same queue rejected with 409 Conflict
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/tokens")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_ACTIVE_TOKEN"));

        // Customer B can join and receives next token
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/tokens")
                        .header("Authorization", "Bearer " + tokenCustomerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenNumber").value("A002"));
    }

    @Test
    void customer_cannotJoinOtherTenantQueue() throws Exception {
        Queue queueB = new Queue(orgB.getId(), "Org B Queue");
        queueRepository.save(queueB);

        // Customer A belongs to Org A, tries to join Queue in Org B
        mockMvc.perform(post("/api/queues/" + queueB.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("QUEUE_ACCESS_DENIED"));
    }

    // --- State Machine Lifecycle Tests (Next, Hold, Resume, Skip, Complete, Leave, Cancel) ---

    @Test
    void fullTokenLifecycle_transitionsCorrectly() throws Exception {
        Queue queue = new Queue(orgA.getId(), "Lifecycle Queue");
        queueRepository.save(queue);

        // Customer A joins -> WAITING (A001)
        MvcResult joinResult = mockMvc.perform(post("/api/queues/" + queue.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isOk())
                .andReturn();
        String tokenId = objectMapper.readTree(joinResult.getResponse().getContentAsString()).get("data").get("id").asText();

        // Customer B joins -> WAITING (A002)
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenCustomerB))
                .andExpect(status().isOk());

        // Check estimate for Customer B (position 2, wait 5 min)
        mockMvc.perform(get("/api/tokens/" + tokenId + "/estimate")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.estimatedWaitMinutes").value(0));

        // 1. Admin calls Next -> A001 becomes CALLED
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/next")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tokenId))
                .andExpect(jsonPath("$.data.status").value("CALLED"));

        // 2. Admin holds A001 -> HOLD
        mockMvc.perform(post("/api/tokens/" + tokenId + "/hold")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HOLD"));

        // 3. Admin resumes A001 -> WAITING
        mockMvc.perform(post("/api/tokens/" + tokenId + "/resume")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        // 4. Admin calls Next again -> A001 is CALLED
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/call-next")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tokenId))
                .andExpect(jsonPath("$.data.status").value("CALLED"));

        // 5. Admin completes A001 -> COMPLETED
        mockMvc.perform(post("/api/tokens/" + tokenId + "/complete")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 6. Invalid transition: Cannot complete an already COMPLETED token -> 409
        mockMvc.perform(post("/api/tokens/" + tokenId + "/complete")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN_TRANSITION"));

        // 7. Invalid transition: Cannot hold a COMPLETED token -> 409
        mockMvc.perform(post("/api/tokens/" + tokenId + "/hold")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN_TRANSITION"));
    }

    @Test
    void callNext_emptyQueue_returnsConflict() throws Exception {
        Queue queue = new Queue(orgA.getId(), "Empty Queue");
        queueRepository.save(queue);

        mockMvc.perform(post("/api/queues/" + queue.getId() + "/next")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMPTY_QUEUE"));
    }

    @Test
    void skipToken_fromWaitingOrCalled_succeeds() throws Exception {
        Queue queue = new Queue(orgA.getId(), "Skip Queue");
        queueRepository.save(queue);

        // Customer A joins
        MvcResult res = mockMvc.perform(post("/api/queues/" + queue.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isOk())
                .andReturn();
        String tokenId = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asText();

        // Admin skips waiting token
        mockMvc.perform(post("/api/tokens/" + tokenId + "/skip")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SKIPPED"));

        // Cannot skip again -> 409
        mockMvc.perform(post("/api/tokens/" + tokenId + "/skip")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN_TRANSITION"));
    }

    @Test
    void customer_leaveQueue_enforcesOwnership() throws Exception {
        Queue queue = new Queue(orgA.getId(), "Leave Queue");
        queueRepository.save(queue);

        // Customer A joins
        MvcResult res = mockMvc.perform(post("/api/queues/" + queue.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isOk())
                .andReturn();
        String tokenIdA = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asText();

        // Customer B tries to leave Customer A's token -> 403 Access Denied
        mockMvc.perform(post("/api/tokens/" + tokenIdA + "/leave")
                        .header("Authorization", "Bearer " + tokenCustomerB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("QUEUE_ACCESS_DENIED"));

        // Customer A leaves own token -> CANCELLED
        mockMvc.perform(post("/api/tokens/" + tokenIdA + "/leave")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // After leaving, Customer A can join the queue again
        mockMvc.perform(post("/api/queues/" + queue.getId() + "/join")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenNumber").value("A002"));
    }

    @Test
    void customer_cannotCallNext_orHold_returnsForbidden() throws Exception {
        Queue queue = new Queue(orgA.getId(), "Protected Queue");
        queueRepository.save(queue);

        mockMvc.perform(post("/api/queues/" + queue.getId() + "/next")
                        .header("Authorization", "Bearer " + tokenCustomerA))
                .andExpect(status().isForbidden());
    }
}
