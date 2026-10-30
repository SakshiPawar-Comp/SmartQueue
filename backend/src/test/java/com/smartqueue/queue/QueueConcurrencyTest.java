package com.smartqueue.queue;

import static org.junit.jupiter.api.Assertions.*;

import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.application.EmptyQueueException;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@ActiveProfiles("test")
class QueueConcurrencyTest {

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
    private com.smartqueue.counter.infrastructure.CounterRepository counterRepository;

    @Autowired
    private com.smartqueue.counter.infrastructure.StaffCounterAssignmentRepository staffCounterAssignmentRepository;

    @Autowired
    private com.smartqueue.counter.infrastructure.CounterQueueAssignmentRepository counterQueueAssignmentRepository;

    @Autowired
    private com.smartqueue.branch.infrastructure.BranchRepository branchRepository;

    @Autowired
    private QueueService queueService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Organization org;

    @BeforeEach
    void setUp() {
        org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName("Concurrency Org");
        org.setSlug("org-conc-" + UUID.randomUUID().toString().substring(0, 6));
        org.setStatus("ACTIVE");
        org.setCreatedAt(ZonedDateTime.now());
        org.setUpdatedAt(ZonedDateTime.now());
        organizationRepository.save(org);
        TenantContext.setCurrentTenant(org.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
        User saved = userRepository.save(user);

        Role role = roleRepository.findByCode("STAFF")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setCode("STAFF");
                    r.setName("Staff");
                    return roleRepository.save(r);
                });
        userRoleRepository.save(new UserRole(saved, role, org));
        return saved;
    }

    @Test
    void concurrentCallNext_onlyOneCallerGetsToken() throws Exception {
        Queue queue = new Queue(org.getId(), "Concurrent Next Queue");
        queue = queueRepository.save(queue);

        // Generate exactly 1 waiting token
        User cust = createUser("single-" + UUID.randomUUID() + "@test.com");
        QueueToken token = queueService.generateToken(queue.getId(), cust);
        assertEquals(TokenStatus.WAITING, token.getStatus());

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        List<QueueToken> successfulCalls = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger emptyQueueErrors = new AtomicInteger(0);

        UUID queueId = queue.getId();
        UUID orgId = org.getId();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                TenantContext.setCurrentTenant(orgId);
                try {
                    startLatch.await();
                    QueueToken called = queueService.callNext(queueId);
                    successfulCalls.add(called);
                } catch (EmptyQueueException eqe) {
                    emptyQueueErrors.incrementAndGet();
                } catch (Exception e) {
                    // unexpected exception
                } finally {
                    TenantContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 1 thread must have succeeded in calling the token
        assertEquals(1, successfulCalls.size(), "Exactly one thread must successfully call the next token");
        assertEquals(TokenStatus.CALLED, successfulCalls.get(0).getStatus());
        assertEquals(threads - 1, emptyQueueErrors.get(), "Remaining threads must receive EmptyQueueException");
    }

    @Test
    void concurrentJoin_generatesUniqueSequentialTokenNumbers() throws Exception {
        Queue queue = new Queue(org.getId(), "Concurrent Join Queue");
        queue = queueRepository.save(queue);

        int customerCount = 6;
        List<User> customers = new ArrayList<>();
        for (int i = 0; i < customerCount; i++) {
            customers.add(createUser("cust-conc-" + i + "-" + UUID.randomUUID() + "@test.com"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(customerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(customerCount);

        List<QueueToken> generatedTokens = Collections.synchronizedList(new ArrayList<>());
        UUID queueId = queue.getId();
        UUID orgId = org.getId();

        for (int i = 0; i < customerCount; i++) {
            final User c = customers.get(i);
            executor.submit(() -> {
                TenantContext.setCurrentTenant(orgId);
                try {
                    startLatch.await();
                    QueueToken tok = queueService.generateToken(queueId, c);
                    generatedTokens.add(tok);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    TenantContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(customerCount, generatedTokens.size(), "All concurrent joins must succeed");

        Set<String> tokenNumbers = new HashSet<>();
        for (QueueToken tok : generatedTokens) {
            tokenNumbers.add(tok.getTokenNumber());
        }

        // Must have unique token numbers without duplicates
        assertEquals(customerCount, tokenNumbers.size(), "All generated token numbers must be unique");
    }

    @Test
    void concurrentCapacityJoin_onlyOneSucceedsAtCapacityOne() throws Exception {
        Queue queue = new Queue(org.getId(), "Capped Concurrency Queue");
        queue.setCapacity(1); // Strict capacity limit = 1
        queue = queueRepository.save(queue);

        User customerA = createUser("cust-cap-a-" + UUID.randomUUID() + "@test.com");
        User customerB = createUser("cust-cap-b-" + UUID.randomUUID() + "@test.com");

        int callers = 2;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(callers);

        List<QueueToken> successfulJoins = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger capacityExceededErrors = new AtomicInteger(0);

        UUID queueId = queue.getId();
        UUID orgId = org.getId();

        executor.submit(() -> {
            TenantContext.setCurrentTenant(orgId);
            try {
                startLatch.await();
                QueueToken tok = queueService.generateToken(queueId, customerA);
                successfulJoins.add(tok);
            } catch (com.smartqueue.queue.application.QueueCapacityExceededException ce) {
                capacityExceededErrors.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                TenantContext.clear();
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            TenantContext.setCurrentTenant(orgId);
            try {
                startLatch.await();
                QueueToken tok = queueService.generateToken(queueId, customerB);
                successfulJoins.add(tok);
            } catch (com.smartqueue.queue.application.QueueCapacityExceededException ce) {
                capacityExceededErrors.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                TenantContext.clear();
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successfulJoins.size(), "Exactly one concurrent join must succeed at capacity=1");
        assertEquals(1, capacityExceededErrors.get(), "Exactly one concurrent join must fail with QueueCapacityExceededException");

        long activeWaitingInDb = queueTokenRepository.countWaitingByQueueId(queueId);
        assertEquals(1, activeWaitingInDb, "Database must contain exactly 1 active WAITING token");
    }

    @Test
    void concurrentSameCounterCallNext_noDuplicateServing() throws Exception {
        com.smartqueue.branch.domain.Branch branch = new com.smartqueue.branch.domain.Branch();
        branch.setId(UUID.randomUUID());
        branch.setOrganization(org);
        branch.setName("Same Counter Branch");
        branch.setCode("SCB-01");
        branch.setTimezone("UTC");
        branch.setStatus("ACTIVE");
        branch = branchRepository.save(branch);

        Queue queue = new Queue(org.getId(), "Same Counter Queue");
        queue.setBranchId(branch.getId());
        queue = queueRepository.save(queue);

        com.smartqueue.counter.domain.Counter counter = new com.smartqueue.counter.domain.Counter(org, branch, "Counter 1", "C-01");
        counter = counterRepository.save(counter);

        // Assign queue to counter
        counterQueueAssignmentRepository.save(new com.smartqueue.counter.domain.CounterQueueAssignment(counter, queue));

        User staff = createUser("staff-sc-" + UUID.randomUUID() + "@test.com");
        staffCounterAssignmentRepository.save(new com.smartqueue.counter.domain.StaffCounterAssignment(org, branch, counter, staff));

        // Generate exactly 1 token
        User cust = createUser("cust-sc-" + UUID.randomUUID() + "@test.com");
        queueService.generateToken(queue.getId(), cust);

        int callers = 2;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(callers);

        List<QueueToken> calledTokens = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger emptyQueueErrors = new AtomicInteger(0);

        UUID queueId = queue.getId();
        UUID counterId = counter.getId();
        UUID orgId = org.getId();

        for (int i = 0; i < callers; i++) {
            executor.submit(() -> {
                TenantContext.setCurrentTenant(orgId);
                try {
                    startLatch.await();
                    QueueToken tok = queueService.callNext(queueId, counterId, staff);
                    calledTokens.add(tok);
                } catch (EmptyQueueException eqe) {
                    emptyQueueErrors.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    TenantContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, calledTokens.size(), "Only one thread on the same counter can claim the token");
        assertEquals(1, emptyQueueErrors.get(), "The concurrent caller on the same counter must receive EmptyQueueException");
        assertEquals(TokenStatus.CALLED, calledTokens.get(0).getStatus());
    }

    @Test
    void concurrentMultiCounterCallNext_neverAssignsDuplicateTokens() throws Exception {
        com.smartqueue.branch.domain.Branch branch = new com.smartqueue.branch.domain.Branch();
        branch.setId(UUID.randomUUID());
        branch.setOrganization(org);
        branch.setName("Test Branch");
        branch.setCode("TB-01");
        branch.setTimezone("UTC");
        branch.setStatus("ACTIVE");
        branch = branchRepository.save(branch);

        Queue queue = new Queue(org.getId(), "Multi-Counter Queue");
        queue.setBranchId(branch.getId());
        queue = queueRepository.save(queue);

        int tokenCount = 10;
        for (int i = 0; i < tokenCount; i++) {
            User customer = createUser("cust-mc-" + i + "-" + UUID.randomUUID() + "@test.com");
            queueService.generateToken(queue.getId(), customer);
        }

        int counterCount = 4;
        List<com.smartqueue.counter.domain.Counter> counters = new ArrayList<>();
        List<User> staffUsers = new ArrayList<>();

        for (int i = 0; i < counterCount; i++) {
            com.smartqueue.counter.domain.Counter counter = new com.smartqueue.counter.domain.Counter(org, branch, "Counter " + (i + 1), "C0" + (i + 1));
            counter = counterRepository.save(counter);
            counters.add(counter);

            // Assign queue to counter
            counterQueueAssignmentRepository.save(new com.smartqueue.counter.domain.CounterQueueAssignment(counter, queue));

            User staff = createUser("staff-conc-" + i + "-" + UUID.randomUUID() + "@test.com");
            staffUsers.add(staff);

            // Assign staff to counter
            staffCounterAssignmentRepository.save(new com.smartqueue.counter.domain.StaffCounterAssignment(org, branch, counter, staff));
        }

        ExecutorService executor = Executors.newFixedThreadPool(counterCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(counterCount);

        List<QueueToken> calledTokens = Collections.synchronizedList(new ArrayList<>());
        UUID queueId = queue.getId();
        UUID orgId = org.getId();

        for (int i = 0; i < counterCount; i++) {
            final UUID counterId = counters.get(i).getId();
            final User staffUser = staffUsers.get(i);
            executor.submit(() -> {
                TenantContext.setCurrentTenant(orgId);
                try {
                    startLatch.await();
                    // Each counter calls next twice
                    for (int call = 0; call < 2; call++) {
                        try {
                            QueueToken token = queueService.callNext(queueId, counterId, staffUser);
                            if (token != null) {
                                calledTokens.add(token);
                            }
                        } catch (EmptyQueueException eq) {
                            // Queue may be exhausted
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    TenantContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(8, calledTokens.size(), "Total 8 calls must be served across counters");
        Set<UUID> tokenIds = new HashSet<>();
        for (QueueToken t : calledTokens) {
            assertTrue(tokenIds.add(t.getId()), "Token ID " + t.getId() + " was assigned to multiple counters!");
            assertEquals(TokenStatus.CALLED, t.getStatus());
            assertNotNull(t.getServedByCounterId(), "Counter ID must be recorded");
            assertNotNull(t.getServedByUserId(), "Staff user ID must be recorded");
        }
    }
}
