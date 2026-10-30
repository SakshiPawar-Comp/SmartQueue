package com.smartqueue.common.seed;

import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.branch.domain.Branch;
import com.smartqueue.branch.infrastructure.BranchRepository;
import com.smartqueue.counter.domain.Counter;
import com.smartqueue.counter.domain.CounterQueueAssignment;
import com.smartqueue.counter.domain.StaffCounterAssignment;
import com.smartqueue.counter.infrastructure.CounterQueueAssignmentRepository;
import com.smartqueue.counter.infrastructure.CounterRepository;
import com.smartqueue.counter.infrastructure.StaffCounterAssignmentRepository;
import com.smartqueue.notification.domain.*;
import com.smartqueue.notification.infrastructure.NotificationRepository;
import com.smartqueue.notification.infrastructure.UserNotificationPreferenceRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Component
@Profile("demo")
@Order(10)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final ServiceRepository serviceRepository;
    private final QueueRepository queueRepository;
    private final CounterRepository counterRepository;
    private final StaffCounterAssignmentRepository staffCounterAssignmentRepository;
    private final CounterQueueAssignmentRepository counterQueueAssignmentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final QueueTokenRepository queueTokenRepository;
    private final NotificationRepository notificationRepository;
    private final UserNotificationPreferenceRepository userNotificationPreferenceRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            OrganizationRepository organizationRepository,
            BranchRepository branchRepository,
            ServiceRepository serviceRepository,
            QueueRepository queueRepository,
            CounterRepository counterRepository,
            StaffCounterAssignmentRepository staffCounterAssignmentRepository,
            CounterQueueAssignmentRepository counterQueueAssignmentRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            QueueTokenRepository queueTokenRepository,
            NotificationRepository notificationRepository,
            UserNotificationPreferenceRepository userNotificationPreferenceRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.organizationRepository = organizationRepository;
        this.branchRepository = branchRepository;
        this.serviceRepository = serviceRepository;
        this.queueRepository = queueRepository;
        this.counterRepository = counterRepository;
        this.staffCounterAssignmentRepository = staffCounterAssignmentRepository;
        this.counterQueueAssignmentRepository = counterQueueAssignmentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.queueTokenRepository = queueTokenRepository;
        this.notificationRepository = notificationRepository;
        this.userNotificationPreferenceRepository = userNotificationPreferenceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (organizationRepository.findBySlug("citycare").isPresent()) {
            ensureDocumentVerificationFlagsSet();
            log.info("[DemoDataSeeder] Demo organization 'citycare' already exists. Verified document verification flags.");
            return;
        }

        log.info("[DemoDataSeeder] Starting production-quality demo dataset seeding...");

        // 1. Roles
        Role adminRole = roleRepository.findByCode("ORGANIZATION_ADMIN").orElseThrow();
        Role staffRole = roleRepository.findByCode("STAFF").orElseThrow();
        Role customerRole = roleRepository.findByCode("CUSTOMER").orElseThrow();

        // 2. Demo Organization: CityCare Multi-Service Center
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName("CityCare Multi-Service Center");
        org.setSlug("citycare");
        org.setStatus("ACTIVE");
        org.setCreatedAt(ZonedDateTime.now().minusDays(30));
        org.setUpdatedAt(ZonedDateTime.now().minusDays(30));
        org = organizationRepository.save(org);

        // 3. Branches
        Branch branchCentral = new Branch();
        branchCentral.setId(UUID.randomUUID());
        branchCentral.setOrganization(org);
        branchCentral.setName("CityCare Central Branch");
        branchCentral.setCode("CITY-CENTRAL");
        branchCentral.setAddress("MG Road, Pune, Maharashtra");
        branchCentral.setTimezone("Asia/Kolkata");
        branchCentral.setStatus("ACTIVE");
        branchCentral = branchRepository.save(branchCentral);

        Branch branchUniv = new Branch();
        branchUniv.setId(UUID.randomUUID());
        branchUniv.setOrganization(org);
        branchUniv.setName("CityCare University Branch");
        branchUniv.setCode("CITY-UNIV");
        branchUniv.setAddress("University Road, Pune, Maharashtra");
        branchUniv.setTimezone("Asia/Kolkata");
        branchUniv.setStatus("ACTIVE");
        branchUniv = branchRepository.save(branchUniv);

        // 4. Services
        Service srvGen = new Service(org, "General Consultation");
        srvGen.setBranch(branchCentral);
        srvGen.setCode("GEN-CONS");
        srvGen.setDescription("General citizen inquiries, registration and consultation services");
        srvGen.setStatus("ACTIVE");
        srvGen = serviceRepository.save(srvGen);

        Service srvDoc = new Service(org, "Document Verification");
        srvDoc.setBranch(branchCentral);
        srvDoc.setCode("DOC-VERIF");
        srvDoc.setDescription("Official document validation, identity checks, and attestation");
        srvDoc.setStatus("ACTIVE");
        srvDoc.setRequiresDocumentVerification(true);
        srvDoc = serviceRepository.save(srvDoc);

        Service srvAcc = new Service(org, "Account Services");
        srvAcc.setBranch(branchCentral);
        srvAcc.setCode("ACC-SERV");
        srvAcc.setDescription("Citizen account updates, fee payments, and profile verification");
        srvAcc.setStatus("ACTIVE");
        srvAcc = serviceRepository.save(srvAcc);

        Service srvStud = new Service(org, "Student Services");
        srvStud.setBranch(branchUniv);
        srvStud.setCode("STUD-SERV");
        srvStud.setDescription("Student onboarding, enrollment verification, and certificates");
        srvStud.setStatus("ACTIVE");
        srvStud = serviceRepository.save(srvStud);

        Service srvDocU = new Service(org, "Document Verification");
        srvDocU.setBranch(branchUniv);
        srvDocU.setCode("DOC-VERIF-U");
        srvDocU.setDescription("Academic and residency document authentication");
        srvDocU.setStatus("ACTIVE");
        srvDocU.setRequiresDocumentVerification(true);
        srvDocU = serviceRepository.save(srvDocU);

        // 5. Queues
        Queue qGeneral = new Queue(org.getId(), "General Consultation");
        qGeneral.setBranchId(branchCentral.getId());
        qGeneral.setServiceId(srvGen.getId());
        qGeneral.setDescription("Walk-in and priority general inquiries desk");
        qGeneral.setTokenPrefix("A");
        qGeneral.setCapacity(25);
        qGeneral.setIsPriorityEnabled(true);
        qGeneral.setDefaultServiceTimeMinutes(5);
        qGeneral.setStatus(QueueStatus.ACTIVE);
        qGeneral.setCurrentTokenNumber("A010");
        qGeneral = queueRepository.save(qGeneral);

        Queue qDoc = new Queue(org.getId(), "Document Verification");
        qDoc.setBranchId(branchCentral.getId());
        qDoc.setServiceId(srvDoc.getId());
        qDoc.setDescription("Official certification and identity verification");
        qDoc.setTokenPrefix("B");
        qDoc.setCapacity(20);
        qDoc.setIsPriorityEnabled(true);
        qDoc.setDefaultServiceTimeMinutes(10);
        qDoc.setStatus(QueueStatus.ACTIVE);
        qDoc.setRequiresDocumentVerification(true);
        qDoc.setCurrentTokenNumber("B005");
        qDoc = queueRepository.save(qDoc);

        Queue qAcc = new Queue(org.getId(), "Account Services");
        qAcc.setBranchId(branchCentral.getId());
        qAcc.setServiceId(srvAcc.getId());
        qAcc.setDescription("Account updates and billing");
        qAcc.setTokenPrefix("C");
        qAcc.setCapacity(15);
        qAcc.setIsPriorityEnabled(false);
        qAcc.setDefaultServiceTimeMinutes(7);
        qAcc.setStatus(QueueStatus.ACTIVE);
        qAcc.setCurrentTokenNumber("C000");
        qAcc = queueRepository.save(qAcc);

        Queue qStud = new Queue(org.getId(), "Student Services");
        qStud.setBranchId(branchUniv.getId());
        qStud.setServiceId(srvStud.getId());
        qStud.setDescription("Student inquiries and university onboarding");
        qStud.setTokenPrefix("S");
        qStud.setCapacity(30);
        qStud.setIsPriorityEnabled(true);
        qStud.setDefaultServiceTimeMinutes(5);
        qStud.setStatus(QueueStatus.ACTIVE);
        qStud.setCurrentTokenNumber("S004");
        qStud = queueRepository.save(qStud);

        Queue qDocU = new Queue(org.getId(), "Document Verification");
        qDocU.setBranchId(branchUniv.getId());
        qDocU.setServiceId(srvDocU.getId());
        qDocU.setDescription("Academic document attestation");
        qDocU.setTokenPrefix("D");
        qDocU.setCapacity(15);
        qDocU.setIsPriorityEnabled(false);
        qDocU.setDefaultServiceTimeMinutes(8);
        qDocU.setStatus(QueueStatus.ACTIVE);
        qDocU.setRequiresDocumentVerification(true);
        qDocU.setCurrentTokenNumber("D000");
        qDocU = queueRepository.save(qDocU);

        // 6. Counters
        Counter c1 = counterRepository.save(new Counter(org, branchCentral, "Counter 1 — General Services", "C1-GEN"));
        Counter c2 = counterRepository.save(new Counter(org, branchCentral, "Counter 2 — General Services", "C2-GEN"));
        Counter c3 = counterRepository.save(new Counter(org, branchCentral, "Counter 3 — Document Services", "C3-DOC"));
        Counter u1 = counterRepository.save(new Counter(org, branchUniv, "Counter 1 — Student Services", "C1-STUD"));
        Counter u2 = counterRepository.save(new Counter(org, branchUniv, "Counter 2 — Document Services", "C2-DOC"));

        // 7. Counter-Queue Assignments
        counterQueueAssignmentRepository.save(new CounterQueueAssignment(c1, qGeneral));
        counterQueueAssignmentRepository.save(new CounterQueueAssignment(c2, qGeneral));
        counterQueueAssignmentRepository.save(new CounterQueueAssignment(c3, qDoc));
        counterQueueAssignmentRepository.save(new CounterQueueAssignment(c3, qAcc));
        counterQueueAssignmentRepository.save(new CounterQueueAssignment(u1, qStud));
        counterQueueAssignmentRepository.save(new CounterQueueAssignment(u2, qDocU));

        // 8. Organization Admin Account
        String encodedPassword = passwordEncoder.encode("Password123!");
        User adminUser = createUser("admin@citycare.demo", encodedPassword, "Dr. Arvind Mehta", "+91 98220 00001", "en");
        userRoleRepository.save(new UserRole(adminUser, adminRole, org));

        // 9. Staff Accounts & Assignments
        User staff1 = createUser("staff.central1@citycare.demo", encodedPassword, "Suresh Deshmukh", "+91 98220 00002", "mr");
        userRoleRepository.save(new UserRole(staff1, staffRole, org));
        staffCounterAssignmentRepository.save(new StaffCounterAssignment(org, branchCentral, c1, staff1));

        User staff2 = createUser("staff.central2@citycare.demo", encodedPassword, "Sunita Gaikwad", "+91 98220 00003", "hi");
        userRoleRepository.save(new UserRole(staff2, staffRole, org));
        staffCounterAssignmentRepository.save(new StaffCounterAssignment(org, branchCentral, c2, staff2));

        User staff3 = createUser("staff.documents@citycare.demo", encodedPassword, "Manoj Kulkarni", "+91 98220 00004", "mr");
        userRoleRepository.save(new UserRole(staff3, staffRole, org));
        staffCounterAssignmentRepository.save(new StaffCounterAssignment(org, branchCentral, c3, staff3));

        User staffUniv1 = createUser("staff.university1@citycare.demo", encodedPassword, "Pooja Jadhav", "+91 98220 00005", "en");
        userRoleRepository.save(new UserRole(staffUniv1, staffRole, org));
        staffCounterAssignmentRepository.save(new StaffCounterAssignment(org, branchUniv, u1, staffUniv1));

        User staffUniv2 = createUser("staff.university2@citycare.demo", encodedPassword, "Amit Shinde", "+91 98220 00006", "hi");
        userRoleRepository.save(new UserRole(staffUniv2, staffRole, org));
        staffCounterAssignmentRepository.save(new StaffCounterAssignment(org, branchUniv, u2, staffUniv2));

        // 10. Customer Accounts (10 realistic users)
        User cust1 = createCustomer("aarav@customer.demo", encodedPassword, "Aarav Sharma", "+91 98230 11001", "en", org, customerRole);
        User cust2 = createCustomer("priya@customer.demo", encodedPassword, "Priya Patil", "+91 98230 11002", "mr", org, customerRole);
        User cust3 = createCustomer("rohan@customer.demo", encodedPassword, "Rohan Kulkarni", "+91 98230 11003", "hi", org, customerRole);
        User cust4 = createCustomer("ananya@customer.demo", encodedPassword, "Ananya Deshmukh", "+91 98230 11004", "en", org, customerRole);
        User cust5 = createCustomer("aditya@customer.demo", encodedPassword, "Aditya Joshi", "+91 98230 11005", "mr", org, customerRole);
        User cust6 = createCustomer("sneha@customer.demo", encodedPassword, "Sneha More", "+91 98230 11006", "hi", org, customerRole);
        User cust7 = createCustomer("rahul@customer.demo", encodedPassword, "Rahul Pawar", "+91 98230 11007", "en", org, customerRole);
        User cust8 = createCustomer("neha@customer.demo", encodedPassword, "Neha Shinde", "+91 98230 11008", "mr", org, customerRole);
        User cust9 = createCustomer("vikram@customer.demo", encodedPassword, "Vikram Malhotra", "+91 98230 11009", "hi", org, customerRole);
        User cust10 = createCustomer("pooja@customer.demo", encodedPassword, "Pooja Mehta", "+91 98230 11010", "en", org, customerRole);

        ZonedDateTime now = ZonedDateTime.now();

        // 11. Realistic Historical & Active Tokens in General Consultation Queue
        // A001 - COMPLETED
        createToken(qGeneral, cust1, "A001", TokenStatus.COMPLETED, TokenPriority.NORMAL,
                now.minusMinutes(120), now.minusMinutes(110), now.minusMinutes(102), null, null, null,
                c1.getId(), staff1.getId());

        // A002 - COMPLETED (VIP)
        createToken(qGeneral, cust2, "A002", TokenStatus.COMPLETED, TokenPriority.VIP,
                now.minusMinutes(105), now.minusMinutes(100), now.minusMinutes(94), null, null, null,
                c2.getId(), staff2.getId());

        // A003 - COMPLETED
        createToken(qGeneral, cust3, "A003", TokenStatus.COMPLETED, TokenPriority.NORMAL,
                now.minusMinutes(90), now.minusMinutes(80), now.minusMinutes(72), null, null, null,
                c1.getId(), staff1.getId());

        // A004 - SKIPPED
        createToken(qGeneral, cust4, "A004", TokenStatus.SKIPPED, TokenPriority.NORMAL,
                now.minusMinutes(75), now.minusMinutes(65), null, null, now.minusMinutes(58), null,
                c1.getId(), staff1.getId());

        // A005 - COMPLETED (PRIORITY)
        createToken(qGeneral, cust5, "A005", TokenStatus.COMPLETED, TokenPriority.PRIORITY,
                now.minusMinutes(60), now.minusMinutes(50), now.minusMinutes(43), null, null, null,
                c2.getId(), staff2.getId());

        // A006 - HOLD
        createToken(qGeneral, cust6, "A006", TokenStatus.HOLD, TokenPriority.NORMAL,
                now.minusMinutes(45), now.minusMinutes(30), null, now.minusMinutes(25), null, null,
                c1.getId(), staff1.getId());

        // A007 - CALLED
        createToken(qGeneral, cust7, "A007", TokenStatus.CALLED, TokenPriority.NORMAL,
                now.minusMinutes(35), now.minusMinutes(5), null, null, null, null,
                c2.getId(), staff2.getId());

        // A008 - WAITING (VIP)
        createToken(qGeneral, cust8, "A008", TokenStatus.WAITING, TokenPriority.VIP,
                now.minusMinutes(20), null, null, null, null, null, null, null);

        // A009 - WAITING
        createToken(qGeneral, cust9, "A009", TokenStatus.WAITING, TokenPriority.NORMAL,
                now.minusMinutes(15), null, null, null, null, null, null, null);

        // A010 - WAITING
        createToken(qGeneral, cust10, "A010", TokenStatus.WAITING, TokenPriority.NORMAL,
                now.minusMinutes(10), null, null, null, null, null, null, null);

        // 12. Realistic Tokens in Document Verification Queue
        // B001 - COMPLETED
        createToken(qDoc, cust9, "B001", TokenStatus.COMPLETED, TokenPriority.NORMAL,
                now.minusMinutes(180), now.minusMinutes(165), now.minusMinutes(150), null, null, null,
                c3.getId(), staff3.getId());

        // B002 - COMPLETED (VIP)
        createToken(qDoc, cust10, "B002", TokenStatus.COMPLETED, TokenPriority.VIP,
                now.minusMinutes(150), now.minusMinutes(135), now.minusMinutes(120), null, null, null,
                c3.getId(), staff3.getId());

        // B003 - CANCELLED
        createToken(qDoc, cust1, "B003", TokenStatus.CANCELLED, TokenPriority.NORMAL,
                now.minusMinutes(120), null, null, null, null, now.minusMinutes(110),
                null, null);

        // B004 - CALLED
        createToken(qDoc, cust2, "B004", TokenStatus.CALLED, TokenPriority.NORMAL,
                now.minusMinutes(50), now.minusMinutes(10), null, null, null, null,
                c3.getId(), staff3.getId());

        // B005 - WAITING (PRIORITY)
        createToken(qDoc, cust3, "B005", TokenStatus.WAITING, TokenPriority.PRIORITY,
                now.minusMinutes(25), null, null, null, null, null, null, null);

        // 13. Realistic Tokens in Student Services Queue
        // S001 - COMPLETED
        createToken(qStud, cust4, "S001", TokenStatus.COMPLETED, TokenPriority.NORMAL,
                now.minusMinutes(120), now.minusMinutes(110), now.minusMinutes(105), null, null, null,
                u1.getId(), staffUniv1.getId());

        // S002 - COMPLETED
        createToken(qStud, cust5, "S002", TokenStatus.COMPLETED, TokenPriority.NORMAL,
                now.minusMinutes(90), now.minusMinutes(82), now.minusMinutes(78), null, null, null,
                u1.getId(), staffUniv1.getId());

        // S003 - CALLED
        createToken(qStud, cust6, "S003", TokenStatus.CALLED, TokenPriority.NORMAL,
                now.minusMinutes(40), now.minusMinutes(12), null, null, null, null,
                u1.getId(), staffUniv1.getId());

        // S004 - WAITING
        createToken(qStud, cust7, "S004", TokenStatus.WAITING, TokenPriority.NORMAL,
                now.minusMinutes(15), null, null, null, null, null, null, null);

        // 14. Notifications for Customer Accounts
        createNotification(org, cust1, null, qGeneral, NotificationChannel.IN_APP, NotificationEventType.TOKEN_COMPLETED,
                NotificationStatus.DELIVERED, cust1.getEmail(), "Service Completed",
                "Your token A001 for General Consultation has been marked COMPLETED. Thank you for visiting CityCare!",
                true, now.minusMinutes(102));

        createNotification(org, cust2, null, qGeneral, NotificationChannel.IN_APP, NotificationEventType.TOKEN_COMPLETED,
                NotificationStatus.DELIVERED, cust2.getEmail(), "Service Completed",
                "Your VIP token A002 for General Consultation was successfully served at Counter 2.",
                true, now.minusMinutes(94));

        createNotification(org, cust6, null, qGeneral, NotificationChannel.IN_APP, NotificationEventType.TOKEN_HOLD,
                NotificationStatus.DELIVERED, cust6.getEmail(), "Token Placed on Hold",
                "Your token A006 was placed on temporary HOLD by Counter 1. Please speak with the desk to resume your turn.",
                false, now.minusMinutes(25));

        createNotification(org, cust7, null, qGeneral, NotificationChannel.IN_APP, NotificationEventType.TOKEN_CALLED,
                NotificationStatus.DELIVERED, cust7.getEmail(), "Now Serving Your Token!",
                "Token A007 is now being called at Counter 2 — General Services. Please proceed to the counter immediately.",
                false, now.minusMinutes(5));

        createNotification(org, cust8, null, qGeneral, NotificationChannel.IN_APP, NotificationEventType.TOKEN_CREATED,
                NotificationStatus.DELIVERED, cust8.getEmail(), "Token Issued Successfully",
                "You joined General Consultation. Your VIP token is A008. Estimated wait: 0 mins.",
                false, now.minusMinutes(20));

        createNotification(org, cust2, null, qDoc, NotificationChannel.IN_APP, NotificationEventType.TOKEN_CALLED,
                NotificationStatus.DELIVERED, cust2.getEmail(), "Now Serving Your Token!",
                "Token B004 is now being called at Counter 3 — Document Services.",
                false, now.minusMinutes(10));

        createNotification(org, cust6, null, qStud, NotificationChannel.IN_APP, NotificationEventType.TOKEN_CALLED,
                NotificationStatus.DELIVERED, cust6.getEmail(), "Now Serving Your Token!",
                "Token S003 is now being called at University Counter 1 — Student Services.",
                false, now.minusMinutes(12));

        log.info("[DemoDataSeeder] Successfully seeded production-grade demo dataset for CityCare Multi-Service Center!");
    }

    private User createUser(String email, String encodedPassword, String fullName, String phone, String language) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setPasswordHash(encodedPassword);
        u.setFullName(fullName);
        u.setPhone(phone);
        u.setPreferredLanguage(language);
        u.setStatus("ACTIVE");
        u.setCreatedAt(ZonedDateTime.now().minusDays(30));
        u.setUpdatedAt(ZonedDateTime.now().minusDays(30));
        return userRepository.save(u);
    }

    private User createCustomer(String email, String encodedPassword, String fullName, String phone, String language, Organization org, Role role) {
        User u = createUser(email, encodedPassword, fullName, phone, language);
        userRoleRepository.save(new UserRole(u, role, org));

        UserNotificationPreference pref = new UserNotificationPreference(u);
        pref.setInAppEnabled(true);
        pref.setEmailEnabled(true);
        pref.setPushEnabled(true);
        pref.setSmsEnabled(false);
        pref.setPositionNearThreshold(3);
        userNotificationPreferenceRepository.save(pref);

        return u;
    }

    private QueueToken createToken(
            Queue queue,
            User customer,
            String tokenNumber,
            TokenStatus status,
            TokenPriority priority,
            ZonedDateTime createdAt,
            ZonedDateTime calledAt,
            ZonedDateTime completedAt,
            ZonedDateTime heldAt,
            ZonedDateTime skippedAt,
            ZonedDateTime cancelledAt,
            UUID counterId,
            UUID staffUserId
    ) {
        QueueToken t = new QueueToken();
        t.setId(UUID.randomUUID());
        t.setQueue(queue);
        t.setCustomer(customer);
        t.setTokenNumber(tokenNumber);
        t.setStatus(status);
        t.setPriority(priority != null ? priority : TokenPriority.NORMAL);
        t.setCreatedAt(createdAt);
        t.setUpdatedAt(completedAt != null ? completedAt : (calledAt != null ? calledAt : createdAt));
        t.setCalledAt(calledAt);
        t.setCompletedAt(completedAt);
        t.setHeldAt(heldAt);
        t.setSkippedAt(skippedAt);
        t.setCancelledAt(cancelledAt);
        t.setServedByCounterId(counterId);
        t.setServedByUserId(staffUserId);
        return queueTokenRepository.save(t);
    }

    private void createNotification(
            Organization org,
            User user,
            QueueToken token,
            Queue queue,
            NotificationChannel channel,
            NotificationEventType eventType,
            NotificationStatus status,
            String recipient,
            String title,
            String message,
            boolean isRead,
            ZonedDateTime timestamp
    ) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setOrganization(org);
        n.setUser(user);
        n.setToken(token);
        n.setQueue(queue);
        n.setChannel(channel);
        n.setEventType(eventType);
        n.setStatus(status);
        n.setRecipient(recipient);
        n.setTitle(title);
        n.setMessage(message);
        n.setRead(isRead);
        n.setIdempotencyKey(UUID.randomUUID().toString());
        n.setCreatedAt(timestamp);
        n.setUpdatedAt(timestamp);
        n.setSentAt(timestamp);
        if (isRead) {
            n.setReadAt(timestamp.plusMinutes(5));
        }
        notificationRepository.save(n);
    }

    private void ensureDocumentVerificationFlagsSet() {
        try {
            List<Service> services = serviceRepository.findAll();
            for (Service s : services) {
                if ("DOC-VERIF".equalsIgnoreCase(s.getCode()) || "DOC-VERIF-U".equalsIgnoreCase(s.getCode()) ||
                        (s.getName() != null && s.getName().toLowerCase().contains("document"))) {
                    if (!Boolean.TRUE.equals(s.getRequiresDocumentVerification())) {
                        s.setRequiresDocumentVerification(true);
                        serviceRepository.save(s);
                        log.info("[DemoDataSeeder] Enabled document verification on service: {}", s.getName());
                    }
                }
            }
            List<Queue> queues = queueRepository.findAll();
            for (Queue q : queues) {
                if ((q.getName() != null && q.getName().toLowerCase().contains("document")) ||
                        "B".equals(q.getTokenPrefix()) || "D".equals(q.getTokenPrefix())) {
                    if (!Boolean.TRUE.equals(q.getRequiresDocumentVerification())) {
                        q.setRequiresDocumentVerification(true);
                        queueRepository.save(q);
                        log.info("[DemoDataSeeder] Enabled document verification on queue: {}", q.getName());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[DemoDataSeeder] Could not update document verification flags on existing records: {}", ex.getMessage());
        }
    }
}
