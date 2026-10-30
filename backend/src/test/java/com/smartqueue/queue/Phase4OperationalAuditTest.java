package com.smartqueue.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.branch.api.dto.CreateBranchRequest;
import com.smartqueue.branch.api.dto.UpdateBranchRequest;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.counter.api.dto.AssignQueuesRequest;
import com.smartqueue.counter.api.dto.AssignStaffRequest;
import com.smartqueue.counter.api.dto.CreateCounterRequest;
import com.smartqueue.counter.api.dto.UpdateCounterRequest;
import com.smartqueue.counter.domain.Counter;
import com.smartqueue.counter.infrastructure.CounterQueueAssignmentRepository;
import com.smartqueue.counter.infrastructure.CounterRepository;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenPriority;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import com.smartqueue.service.api.dto.CreateServiceRequest;
import com.smartqueue.service.api.dto.UpdateServiceRequest;
import com.smartqueue.service.domain.Service;
import com.smartqueue.service.infrastructure.ServiceRepository;
import com.smartqueue.staff.api.dto.CreateStaffRequest;
import jakarta.persistence.EntityManager;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Phase4OperationalAuditTest {

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
    private BranchRepository branchRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private CounterRepository counterRepository;

    @Autowired
    private CounterQueueAssignmentRepository counterQueueAssignmentRepository;

    @Autowired
    private com.smartqueue.counter.infrastructure.StaffCounterAssignmentRepository staffCounterAssignmentRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private QueueService queueService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private Organization testOrg;
    private Branch testBranch;
    private Service testService;
    private User orgAdmin;
    private User staffUser;
    private User customerUser;
    private String adminToken;
    private String staffToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByCode("ORGANIZATION_ADMIN")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setCode("ORGANIZATION_ADMIN");
                    r.setName("Organization Admin");
                    return roleRepository.save(r);
                });
        Role staffRole = roleRepository.findByCode("STAFF")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setCode("STAFF");
                    r.setName("Staff");
                    return roleRepository.save(r);
                });
        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setCode("CUSTOMER");
                    r.setName("Customer");
                    return roleRepository.save(r);
                });

        // Create test org
        testOrg = new Organization();
        testOrg.setId(UUID.randomUUID());
        testOrg.setName("Alpha Health Corp");
        testOrg.setSlug("alpha-health-" + UUID.randomUUID().toString().substring(0, 8));
        testOrg.setStatus("ACTIVE");
        testOrg.setCreatedAt(ZonedDateTime.now());
        testOrg.setUpdatedAt(ZonedDateTime.now());
        testOrg = organizationRepository.save(testOrg);

        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(testOrg.getId());

        // Create branch
        testBranch = new Branch();
        testBranch.setId(UUID.randomUUID());
        testBranch.setOrganization(testOrg);
        testBranch.setName("Downtown Center");
        testBranch.setCode("BR-01");
        testBranch.setTimezone("UTC");
        testBranch.setStatus("ACTIVE");
        testBranch = branchRepository.save(testBranch);

        // Create service
        testService = new Service(testOrg, "General Consultation");
        testService.setBranch(testBranch);
        testService.setStatus("ACTIVE");
        testService.setCreatedAt(ZonedDateTime.now());
        testService.setUpdatedAt(ZonedDateTime.now());
        testService = serviceRepository.save(testService);

        // Create users
        orgAdmin = createUser("admin@" + UUID.randomUUID() + ".com", "Admin User");
        userRoleRepository.save(new UserRole(orgAdmin, adminRole, testOrg));
        adminToken = jwtProvider.generateAccessToken(orgAdmin.getEmail(), orgAdmin.getId().toString());

        staffUser = createUser("staff@" + UUID.randomUUID() + ".com", "Staff User");
        userRoleRepository.save(new UserRole(staffUser, staffRole, testOrg));
        staffToken = jwtProvider.generateAccessToken(staffUser.getEmail(), staffUser.getId().toString());

        customerUser = createUser("customer@" + UUID.randomUUID() + ".com", "Customer User");
        userRoleRepository.save(new UserRole(customerUser, customerRole, testOrg));
        customerToken = jwtProvider.generateAccessToken(customerUser.getEmail(), customerUser.getId().toString());

        entityManager.flush();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.smartqueue.common.tenant.TenantContext.clear();
    }

    private User createUser(String email, String fullName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setFullName(fullName);
        user.setStatus("ACTIVE");
        user.setCreatedAt(ZonedDateTime.now());
        user.setUpdatedAt(ZonedDateTime.now());
        return userRepository.save(user);
    }

    // ==========================================
    // 1. BRANCH MANAGEMENT TESTS
    // ==========================================

    @Test
    @DisplayName("Admin can create and list branches; customer cannot create")
    void testBranchManagement() throws Exception {
        CreateBranchRequest createReq = new CreateBranchRequest();
        createReq.setName("Uptown Clinic");
        createReq.setCode("BR-UP");
        createReq.setTimezone("UTC");
        createReq.setAddress("456 North Ave");

        // Customer attempt should fail (403)
        mockMvc.perform(post("/api/branches")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        // Admin create succeeds (201)
        mockMvc.perform(post("/api/branches")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Uptown Clinic"))
                .andExpect(jsonPath("$.data.code").value("BR-UP"));

        // Admin list branches
        mockMvc.perform(get("/api/branches")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));

        // Update branch
        UpdateBranchRequest updateReq = new UpdateBranchRequest();
        updateReq.setName("Downtown Super Center");
        updateReq.setAddress("123 Main St Suite 100");

        mockMvc.perform(put("/api/branches/" + testBranch.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Downtown Super Center"));
    }

    // ==========================================
    // 2. SERVICE MANAGEMENT TESTS
    // ==========================================

    @Test
    @DisplayName("Admin can create, update and list services")
    void testServiceManagement() throws Exception {
        CreateServiceRequest createReq = new CreateServiceRequest();
        createReq.setName("Blood Test");
        createReq.setDescription("Routine blood work");
        createReq.setBranchId(testBranch.getId());

        mockMvc.perform(post("/api/services")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Blood Test"));

        UpdateServiceRequest updateReq = new UpdateServiceRequest();
        updateReq.setName("General Doctor Consultation");
        updateReq.setDescription("Updated consultation details");

        mockMvc.perform(put("/api/services/" + testService.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("General Doctor Consultation"));
    }

    // ==========================================
    // 3. COUNTER & ASSIGNMENT TESTS
    // ==========================================

    @Test
    @DisplayName("Admin can create counter and assign staff & queues with tenant validation")
    void testCounterAndAssignments() throws Exception {
        CreateCounterRequest counterReq = new CreateCounterRequest();
        counterReq.setBranchId(testBranch.getId());
        counterReq.setName("Counter 1");
        counterReq.setCode("C-01");
        counterReq.setDescription("Check-in counter");

        String responseStr = mockMvc.perform(post("/api/counters")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(counterReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Counter 1"))
                .andReturn().getResponse().getContentAsString();

        UUID counterId = UUID.fromString(objectMapper.readTree(responseStr).path("data").path("id").asText());

        // Create a queue for the branch
        Queue testQueue = queueService.createQueue(
                testOrg.getId(),
                "OPD Queue",
                "General OPD",
                "A",
                testBranch.getId(),
                testService.getId(),
                50,
                true,
                5
        );

        // Assign staff to counter
        AssignStaffRequest staffReq = new AssignStaffRequest();
        staffReq.setUserId(staffUser.getId());

        mockMvc.perform(post("/api/counters/" + counterId + "/assign-staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staffReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedStaffUserId").value(staffUser.getId().toString()));

        // Assign queues to counter
        AssignQueuesRequest queuesReq = new AssignQueuesRequest();
        queuesReq.setQueueIds(List.of(testQueue.getId()));

        mockMvc.perform(post("/api/counters/" + counterId + "/assign-queues")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queuesReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedQueueIds", hasSize(1)));

        // Staff can check my assignments
        mockMvc.perform(get("/api/staff/me/assignments")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCounterId").value(counterId.toString()))
                .andExpect(jsonPath("$.data.branchId").value(testBranch.getId().toString()))
                .andExpect(jsonPath("$.data.assignedQueueIds", hasSize(1)));
    }

    // ==========================================
    // 4. STAFF MANAGEMENT TESTS
    // ==========================================

    @Test
    @DisplayName("Admin can create and list staff users")
    void testStaffManagement() throws Exception {
        CreateStaffRequest staffReq = new CreateStaffRequest();
        staffReq.setFullName("Nurse Joy");
        staffReq.setEmail("joy" + UUID.randomUUID().toString().substring(0, 5) + "@hospital.com");
        staffReq.setPassword("Password123!");
        staffReq.setPhone("+1122334455");
        staffReq.setBranchId(testBranch.getId());

        mockMvc.perform(post("/api/staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staffReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fullName").value("Nurse Joy"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/staff")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));
    }

    // ==========================================
    // 5. ADMIN OVERVIEW METRICS TEST
    // ==========================================

    @Test
    @DisplayName("Admin overview returns consolidated counts")
    void testAdminOverview() throws Exception {
        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBranches").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.totalServices").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.totalStaff").value(greaterThanOrEqualTo(1)));
    }

    // ==========================================
    // 6. CAPACITY ENFORCEMENT TEST
    // ==========================================

    @Test
    @DisplayName("Queue with capacity limit rejects when full (409 Conflict)")
    void testQueueCapacityEnforcement() throws Exception {
        Queue cappedQueue = queueService.createQueue(
                testOrg.getId(),
                "Limited Lab",
                "Capacity limited",
                "L",
                testBranch.getId(),
                testService.getId(),
                2, // Max capacity = 2
                false,
                5
        );
        queueService.openQueue(cappedQueue.getId());

        Role customerRole = roleRepository.findByCode("CUSTOMER").orElseThrow();

        User cust1 = createUser("c1" + UUID.randomUUID().toString().substring(0, 5) + "@test.com", "Customer 1");
        userRoleRepository.save(new UserRole(cust1, customerRole, testOrg));
        User cust2 = createUser("c2" + UUID.randomUUID().toString().substring(0, 5) + "@test.com", "Customer 2");
        userRoleRepository.save(new UserRole(cust2, customerRole, testOrg));
        User cust3 = createUser("c3" + UUID.randomUUID().toString().substring(0, 5) + "@test.com", "Customer 3");
        userRoleRepository.save(new UserRole(cust3, customerRole, testOrg));

        String c1Token = jwtProvider.generateAccessToken(cust1.getEmail(), cust1.getId().toString());
        String c2Token = jwtProvider.generateAccessToken(cust2.getEmail(), cust2.getId().toString());
        String c3Token = jwtProvider.generateAccessToken(cust3.getEmail(), cust3.getId().toString());

        // 1st join succeeds
        mockMvc.perform(post("/api/queues/" + cappedQueue.getId() + "/join")
                        .header("Authorization", "Bearer " + c1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenNumber").value("L001"));

        // 2nd join succeeds
        mockMvc.perform(post("/api/queues/" + cappedQueue.getId() + "/join")
                        .header("Authorization", "Bearer " + c2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenNumber").value("L002"));

        // 3rd join exceeds capacity -> 409 CONFLICT
        mockMvc.perform(post("/api/queues/" + cappedQueue.getId() + "/join")
                        .header("Authorization", "Bearer " + c3Token))
                .andExpect(status().isConflict());

        // Complete 1st token
        String nextRes = mockMvc.perform(post("/api/queues/" + cappedQueue.getId() + "/next")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID calledTokenId = UUID.fromString(objectMapper.readTree(nextRes).path("data").path("id").asText());

        mockMvc.perform(post("/api/queues/tokens/" + calledTokenId + "/complete")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Now capacity is freed (1 waiting), 3rd join succeeds
        mockMvc.perform(post("/api/queues/" + cappedQueue.getId() + "/join")
                        .header("Authorization", "Bearer " + c3Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenNumber").value("L003"));
    }

    // ==========================================
    // 7. PRIORITY ORDERING VS FIFO TEST
    // ==========================================

    @Test
    @DisplayName("Priority queue strictly serves VIP > PRIORITY > NORMAL, preserving FIFO within priority tier")
    void testPriorityOrdering() throws Exception {
        Queue priorityQueue = queueService.createQueue(
                testOrg.getId(),
                "Priority Emergency",
                "Triage",
                "E",
                testBranch.getId(),
                testService.getId(),
                0,
                true, // Priority enabled
                5
        );
        queueService.openQueue(priorityQueue.getId());

        Role customerRole = roleRepository.findByCode("CUSTOMER").orElseThrow();

        User u1 = createUser("u1" + UUID.randomUUID().toString().substring(0, 5) + "@test.com", "Cust 1");
        userRoleRepository.save(new UserRole(u1, customerRole, testOrg));
        User u2 = createUser("u2" + UUID.randomUUID().toString().substring(0, 5) + "@test.com", "Cust 2");
        userRoleRepository.save(new UserRole(u2, customerRole, testOrg));
        User u3 = createUser("u3" + UUID.randomUUID().toString().substring(0, 5) + "@test.com", "Cust 3");
        userRoleRepository.save(new UserRole(u3, customerRole, testOrg));
        User u4 = createUser("u4" + UUID.randomUUID().toString().substring(0, 5) + "@test.com", "Cust 4");
        userRoleRepository.save(new UserRole(u4, customerRole, testOrg));

        // Join: Normal 1 -> Priority 1 -> VIP 1 -> Normal 2
        QueueToken tokenN1 = queueService.generateToken(priorityQueue.getId(), u1, TokenPriority.NORMAL);
        QueueToken tokenP1 = queueService.generateToken(priorityQueue.getId(), u2, TokenPriority.PRIORITY);
        QueueToken tokenV1 = queueService.generateToken(priorityQueue.getId(), u3, TokenPriority.VIP);
        QueueToken tokenN2 = queueService.generateToken(priorityQueue.getId(), u4, TokenPriority.NORMAL);

        assertEquals("E001", tokenN1.getTokenNumber());
        assertEquals("E002", tokenP1.getTokenNumber());
        assertEquals("E003", tokenV1.getTokenNumber());
        assertEquals("E004", tokenN2.getTokenNumber());

        // 1st call: must be VIP (tokenV1)
        QueueToken called1 = queueService.callNext(priorityQueue.getId());
        assertEquals(tokenV1.getId(), called1.getId());
        queueService.completeToken(called1.getId());

        // 2nd call: must be PRIORITY (tokenP1)
        QueueToken called2 = queueService.callNext(priorityQueue.getId());
        assertEquals(tokenP1.getId(), called2.getId());
        queueService.completeToken(called2.getId());

        // 3rd call: must be NORMAL 1 (tokenN1, FIFO before N2)
        QueueToken called3 = queueService.callNext(priorityQueue.getId());
        assertEquals(tokenN1.getId(), called3.getId());
        queueService.completeToken(called3.getId());

        // 4th call: must be NORMAL 2 (tokenN2)
        QueueToken called4 = queueService.callNext(priorityQueue.getId());
        assertEquals(tokenN2.getId(), called4.getId());
        queueService.completeToken(called4.getId());
    }

    // ==========================================
    // 8. MULTI-COUNTER OPERATIONAL SERVING TEST
    // ==========================================

    @Test
    @DisplayName("Counter next serves token with counter and staff user recorded")
    void testCounterNextServing() throws Exception {
        Counter counter = counterRepository.save(new Counter(testOrg, testBranch, "Registration 1", "R1"));
        Queue queue = queueService.createQueue(
                testOrg.getId(),
                "Reg Queue",
                "Registration",
                "R",
                testBranch.getId(),
                testService.getId(),
                0,
                false,
                5
        );
        queueService.openQueue(queue.getId());

        // Assign queue to counter
        counterQueueAssignmentRepository.save(new com.smartqueue.counter.domain.CounterQueueAssignment(counter, queue));

        // Assign staff to counter
        staffCounterAssignmentRepository.save(new com.smartqueue.counter.domain.StaffCounterAssignment(testOrg, testBranch, counter, staffUser));

        // Customer joins
        queueService.generateToken(queue.getId(), customerUser);

        // Staff calls next from counter endpoint
        mockMvc.perform(post("/api/counters/" + counter.getId() + "/next")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CALLED"))
                .andExpect(jsonPath("$.data.tokenNumber").value("R001"))
                .andExpect(jsonPath("$.data.servedByCounterId").value(counter.getId().toString()))
                .andExpect(jsonPath("$.data.servedByUserId").value(staffUser.getId().toString()));
    }

    // ==========================================
    // 9. STAFF AUTHORIZATION MATRIX AUDIT TEST
    // ==========================================

    @Test
    @DisplayName("Staff authorization strictly enforces assignment and active status")
    void testStaffAuthorization_validAndInvalidCases() throws Exception {
        Counter counterA = counterRepository.save(new Counter(testOrg, testBranch, "Counter A", "CA"));
        Counter counterB = counterRepository.save(new Counter(testOrg, testBranch, "Counter B", "CB"));

        Queue queueA = queueService.createQueue(testOrg.getId(), "Queue A", "QA", "A", testBranch.getId(), testService.getId(), 0, false, 5);
        Queue queueB = queueService.createQueue(testOrg.getId(), "Queue B", "QB", "B", testBranch.getId(), testService.getId(), 0, false, 5);
        queueService.openQueue(queueA.getId());
        queueService.openQueue(queueB.getId());

        // Assign Queue A to Counter A only
        counterQueueAssignmentRepository.save(new com.smartqueue.counter.domain.CounterQueueAssignment(counterA, queueA));

        // Assign Staff A to Counter A
        staffCounterAssignmentRepository.save(new com.smartqueue.counter.domain.StaffCounterAssignment(testOrg, testBranch, counterA, staffUser));

        // Join customer to Queue A and Queue B
        queueService.generateToken(queueA.getId(), customerUser);
        queueService.generateToken(queueB.getId(), customerUser);

        // 1. Staff A calls Next on Counter A (assigned) -> SUCCESS (200)
        mockMvc.perform(post("/api/counters/" + counterA.getId() + "/next")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenNumber").value("A001"));

        // 2. Staff A calls Next on Counter B (NOT assigned to Staff A) -> 403 FORBIDDEN
        mockMvc.perform(post("/api/counters/" + counterB.getId() + "/next")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        // 3. Staff A calls Next on Queue B (Queue B NOT assigned to Counter A) -> 403 FORBIDDEN
        mockMvc.perform(post("/api/queues/" + queueB.getId() + "/next?counterId=" + counterA.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        // 4. Inactive Staff member -> 403 FORBIDDEN
        User inactiveStaff = createUser("inactive.staff@" + UUID.randomUUID() + ".com", "Inactive Staff");
        Role staffRole = roleRepository.findByCode("STAFF").orElseThrow();
        userRoleRepository.save(new UserRole(inactiveStaff, staffRole, testOrg));
        inactiveStaff.setStatus("INACTIVE");
        userRepository.save(inactiveStaff);
        String inactiveToken = jwtProvider.generateAccessToken(inactiveStaff.getEmail(), inactiveStaff.getId().toString());

        staffCounterAssignmentRepository.save(new com.smartqueue.counter.domain.StaffCounterAssignment(testOrg, testBranch, counterA, inactiveStaff));

        mockMvc.perform(post("/api/counters/" + counterA.getId() + "/next")
                        .header("Authorization", "Bearer " + inactiveToken))
                .andExpect(status().isForbidden());

        // 5. Inactive Counter -> 403 FORBIDDEN
        counterA.setStatus("INACTIVE");
        counterRepository.save(counterA);

        mockMvc.perform(post("/api/counters/" + counterA.getId() + "/next")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 10. CROSS-TENANT ISOLATION AUDIT TEST
    // ==========================================

    @Test
    @DisplayName("Cross-tenant isolation strictly blocks cross-organization operations")
    void testCrossTenantIsolation_allResourcesProtected() throws Exception {
        // Create Organization B
        Organization orgB = new Organization();
        orgB.setId(UUID.randomUUID());
        orgB.setName("Beta Medical Corp");
        orgB.setSlug("beta-med-" + UUID.randomUUID().toString().substring(0, 8));
        orgB.setStatus("ACTIVE");
        orgB.setCreatedAt(ZonedDateTime.now());
        orgB.setUpdatedAt(ZonedDateTime.now());
        orgB = organizationRepository.save(orgB);

        Branch branchB = new Branch();
        branchB.setId(UUID.randomUUID());
        branchB.setOrganization(orgB);
        branchB.setName("Beta Branch");
        branchB.setCode("BB-01");
        branchB.setTimezone("UTC");
        branchB.setStatus("ACTIVE");
        branchB = branchRepository.save(branchB);

        Service serviceB = new Service(orgB, "Beta Service");
        serviceB.setBranch(branchB);
        serviceB.setStatus("ACTIVE");
        serviceB.setCreatedAt(ZonedDateTime.now());
        serviceB.setUpdatedAt(ZonedDateTime.now());
        serviceB = serviceRepository.save(serviceB);

        Counter counterB = counterRepository.save(new Counter(orgB, branchB, "Beta Counter 1", "BC-01"));

        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(orgB.getId());
        Queue queueB = queueService.createQueue(orgB.getId(), "Beta Queue", "BQ", "B", branchB.getId(), serviceB.getId(), 0, false, 5);
        queueService.openQueue(queueB.getId());
        com.smartqueue.common.tenant.TenantContext.setCurrentTenant(testOrg.getId());

        // Admin A trying to update Branch B -> 403 FORBIDDEN
        UpdateBranchRequest updateBranch = new UpdateBranchRequest();
        updateBranch.setName("Hacked Branch");
        mockMvc.perform(put("/api/branches/" + branchB.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBranch)))
                .andExpect(status().isForbidden());

        // Admin A trying to assign Staff A to Counter B in Org B -> 403 FORBIDDEN
        AssignStaffRequest assignStaffReq = new AssignStaffRequest();
        assignStaffReq.setUserId(staffUser.getId());
        mockMvc.perform(post("/api/counters/" + counterB.getId() + "/assign-staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignStaffReq)))
                .andExpect(status().isForbidden());

        // Admin A trying to assign Queue B to Counter A in Org A -> 403 FORBIDDEN
        Counter counterA = counterRepository.save(new Counter(testOrg, testBranch, "Counter A", "CA"));
        AssignQueuesRequest assignQueuesReq = new AssignQueuesRequest();
        assignQueuesReq.setQueueIds(List.of(queueB.getId()));
        mockMvc.perform(post("/api/counters/" + counterA.getId() + "/assign-queues")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignQueuesReq)))
                .andExpect(status().isForbidden());

        // Staff A trying to call next on Counter B -> 403 FORBIDDEN
        mockMvc.perform(post("/api/counters/" + counterB.getId() + "/next")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }
}
