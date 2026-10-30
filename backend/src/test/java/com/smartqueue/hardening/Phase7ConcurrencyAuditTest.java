package com.smartqueue.hardening;

import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.counter.domain.Counter;
import com.smartqueue.counter.domain.CounterQueueAssignment;
import com.smartqueue.counter.infrastructure.CounterQueueAssignmentRepository;
import com.smartqueue.counter.infrastructure.CounterRepository;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.application.QueueCapacityExceededException;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class Phase7ConcurrencyAuditTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private CounterRepository counterRepository;

    @Autowired
    private CounterQueueAssignmentRepository counterQueueAssignmentRepository;

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

    private Branch createBranch(Organization org, String name) {
        Branch b = new Branch();
        b.setId(UUID.randomUUID());
        b.setOrganization(org);
        b.setName(name);
        b.setCode("BR-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        b.setTimezone("UTC");
        b.setStatus("ACTIVE");
        return branchRepository.save(b);
    }

    private User createAdminUser(Organization org, String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setPasswordHash("$2a$10$dummyhash");
        u.setFullName("Admin " + email);
        u.setStatus("ACTIVE");
        u.setCreatedAt(ZonedDateTime.now());
        u.setUpdatedAt(ZonedDateTime.now());
        u = userRepository.save(u);

        Role role = roleRepository.findByCode("ORGANIZATION_ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setId(UUID.randomUUID());
            r.setCode("ORGANIZATION_ADMIN");
            r.setName("Organization Admin");
            return roleRepository.save(r);
        });

        UserRole ur = new UserRole(u, role, org);
        userRoleRepository.save(ur);
        return u;
    }

    private User createCustomer(Organization org, String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setPasswordHash("$2a$10$dummyhash");
        u.setFullName("Customer " + email);
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

    private Queue createQueue(Organization org, Branch branch, int capacity) {
        Queue q = new Queue();
        q.setId(UUID.randomUUID());
        q.setName("Concurrency Queue");
        q.setOrganizationId(org.getId());
        q.setBranchId(branch.getId());
        q.setTokenPrefix("C");
        q.setCurrentTokenNumber("C000");
        q.setStatus(QueueStatus.ACTIVE);
        q.setCapacity(capacity);
        q.setIsPriorityEnabled(false);
        q.setCreatedAt(ZonedDateTime.now());
        q.setUpdatedAt(ZonedDateTime.now());
        return queueRepository.save(q);
    }

    private Counter createCounter(Organization org, Branch branch, Queue queue, String name) {
        Counter c = new Counter(org, branch, name, "C-" + UUID.randomUUID().toString().substring(0, 4));
        c = counterRepository.save(c);

        CounterQueueAssignment cqa = new CounterQueueAssignment(c, queue);
        counterQueueAssignmentRepository.save(cqa);
        return c;
    }

    @Test
    @DisplayName("Concurrency: Simultaneous Multi-Counter Call Next Claims Exactly One Token Each")
    void testConcurrentMultiCounterCallNext() throws Exception {
        Organization org = createOrg("MultiCounter Org");
        Branch branch = createBranch(org, "Main Branch");
        Queue queue = createQueue(org, branch, 0);
        User admin = createAdminUser(org, "admin_mc_" + UUID.randomUUID() + "@test.com");

        TenantContext.setCurrentTenant(org.getId());

        int tokenCount = 10;
        List<QueueToken> createdTokens = new ArrayList<>();
        for (int i = 0; i < tokenCount; i++) {
            User cust = createCustomer(org, "cust_mc_" + i + "_" + UUID.randomUUID() + "@test.com");
            createdTokens.add(queueService.generateToken(queue.getId(), cust));
        }

        Counter c1 = createCounter(org, branch, queue, "Counter 1");
        Counter c2 = createCounter(org, branch, queue, "Counter 2");
        Counter c3 = createCounter(org, branch, queue, "Counter 3");
        List<Counter> counters = List.of(c1, c2, c3);

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<QueueToken> calledTokens = Collections.synchronizedList(new ArrayList<>());
        final UUID orgId = org.getId();

        for (int i = 0; i < threadCount; i++) {
            final Counter counter = counters.get(i);
            executor.submit(() -> {
                try {
                    TenantContext.setCurrentTenant(orgId);
                    startLatch.await();
                    QueueToken called = queueService.callNextByCounter(counter.getId(), admin);
                    if (called != null) {
                        calledTokens.add(called);
                    }
                } catch (Exception e) {
                    // unexpected
                } finally {
                    TenantContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent callNext operations should finish promptly");
        executor.shutdown();

        assertEquals(3, calledTokens.size(), "Each counter should have claimed a distinct token");
        Set<UUID> uniqueTokenIds = new HashSet<>();
        for (QueueToken t : calledTokens) {
            assertTrue(uniqueTokenIds.add(t.getId()), "No token should be claimed by multiple counters");
            assertEquals(TokenStatus.CALLED, t.getStatus());
            assertNotNull(t.getServedByCounterId());
        }
    }

    @Test
    @DisplayName("Concurrency: Concurrent Joins Obey Strict Capacity Limit")
    void testConcurrentJoinCapacityEnforcement() throws Exception {
        Organization org = createOrg("Capacity Org");
        Branch branch = createBranch(org, "Main Branch");
        int maxCapacity = 5;
        Queue queue = createQueue(org, branch, maxCapacity);

        int concurrentJoinAttempts = 15;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentJoinAttempts);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentJoinAttempts);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        List<QueueToken> joinedTokens = Collections.synchronizedList(new ArrayList<>());
        final UUID orgId = org.getId();

        List<User> customers = new ArrayList<>();
        for (int i = 0; i < concurrentJoinAttempts; i++) {
            customers.add(createCustomer(org, "cap_cust_" + i + "_" + UUID.randomUUID() + "@test.com"));
        }

        for (int i = 0; i < concurrentJoinAttempts; i++) {
            final User cust = customers.get(i);
            executor.submit(() -> {
                try {
                    TenantContext.setCurrentTenant(orgId);
                    startLatch.await();
                    QueueToken token = queueService.generateToken(queue.getId(), cust);
                    joinedTokens.add(token);
                    successCount.incrementAndGet();
                } catch (QueueCapacityExceededException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception e) {
                    // other errors
                } finally {
                    TenantContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent joins should complete promptly");
        executor.shutdown();

        assertEquals(maxCapacity, successCount.get(), "Only capacity limit number of tokens should be issued");
        assertEquals(concurrentJoinAttempts - maxCapacity, rejectedCount.get(), "Excess requests must receive capacity exceeded");
        assertEquals(maxCapacity, joinedTokens.size());

        Set<String> tokenNumbers = new HashSet<>();
        for (QueueToken t : joinedTokens) {
            tokenNumbers.add(t.getTokenNumber());
        }
        assertEquals(maxCapacity, tokenNumbers.size(), "Token numbers must be unique");
    }
}
