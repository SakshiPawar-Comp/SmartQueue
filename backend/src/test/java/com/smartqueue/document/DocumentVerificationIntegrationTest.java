package com.smartqueue.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.auth.domain.Role;
import com.smartqueue.auth.domain.User;
import com.smartqueue.auth.domain.UserRole;
import com.smartqueue.auth.infrastructure.RoleRepository;
import com.smartqueue.auth.infrastructure.UserRepository;
import com.smartqueue.auth.infrastructure.UserRoleRepository;
import com.smartqueue.common.security.JwtProvider;
import com.smartqueue.common.tenant.TenantContext;
import com.smartqueue.document.api.dto.ReviewDocumentRequest;
import com.smartqueue.document.api.dto.UploadDocumentRequest;
import com.smartqueue.document.domain.DocumentVerificationStatus;
import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.application.DocumentVerificationRequiredException;
import com.smartqueue.queue.application.QueueService;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.domain.QueueToken;
import com.smartqueue.queue.domain.TokenStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
import com.smartqueue.queue.infrastructure.QueueTokenRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DocumentVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private QueueTokenRepository queueTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private QueueService queueService;

    private Organization organization;
    private Queue queue;
    private QueueToken token;
    private User customer;
    private User staff;
    private String customerJwt;
    private String staffJwt;

    @BeforeEach
    void setUp() {
        organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("City Health Center");
        organization.setSlug("city-health-" + UUID.randomUUID().toString().substring(0, 8));
        organization.setStatus("ACTIVE");
        organization.setCreatedAt(ZonedDateTime.now());
        organization.setUpdatedAt(ZonedDateTime.now());
        organization = organizationRepository.save(organization);

        TenantContext.setCurrentTenant(organization.getId());

        customer = new User();
        customer.setId(UUID.randomUUID());
        customer.setEmail("customer-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        customer.setPasswordHash("hashedpassword");
        customer.setFullName("Jane Doe");
        customer.setStatus("ACTIVE");
        customer.setCreatedAt(ZonedDateTime.now());
        customer.setUpdatedAt(ZonedDateTime.now());
        customer = userRepository.save(customer);
        assignRole(customer, "CUSTOMER", organization);
        customerJwt = jwtProvider.generateAccessToken(customer.getEmail(), customer.getId().toString());

        staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setEmail("staff-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        staff.setPasswordHash("hashedpassword");
        staff.setFullName("Dr. Robert Smith");
        staff.setStatus("ACTIVE");
        staff.setCreatedAt(ZonedDateTime.now());
        staff.setUpdatedAt(ZonedDateTime.now());
        staff = userRepository.save(staff);
        assignRole(staff, "ORGANIZATION_ADMIN", organization);
        staffJwt = jwtProvider.generateAccessToken(staff.getEmail(), staff.getId().toString());

        queue = new Queue();
        queue.setId(UUID.randomUUID());
        queue.setOrganizationId(organization.getId());
        queue.setName("Specialist Clinic");
        queue.setTokenPrefix("SC");
        queue.setCurrentTokenNumber("SC000");
        queue.setStatus(QueueStatus.ACTIVE);
        queue.setRequiresDocumentVerification(true); // requires document verification!
        queue.setCreatedAt(ZonedDateTime.now());
        queue.setUpdatedAt(ZonedDateTime.now());
        queue = queueRepository.save(queue);

        token = new QueueToken(queue, "SC001", customer);
        token.setStatus(TokenStatus.WAITING);
        token = queueTokenRepository.save(token);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void assignRole(User user, String roleCode, Organization org) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleCode));
        UserRole ur = new UserRole();
        ur.setId(UUID.randomUUID());
        ur.setUser(user);
        ur.setRole(role);
        ur.setOrganization(org);
        userRoleRepository.save(ur);
    }

    @Test
    @DisplayName("Gatekeeping: Cannot call token when document verification is required and missing")
    void testGatekeepingBlocksCallWhenDocumentsMissing() {
        assertThatThrownBy(() -> queueService.callNext(queue.getId(), null, staff))
                .isInstanceOf(DocumentVerificationRequiredException.class)
                .hasMessageContaining("AI Document Verification is required");

        QueueToken unchanged = queueTokenRepository.findById(token.getId());
        assertThat(unchanged).isNotNull();
        assertThat(unchanged.getStatus()).isEqualTo(TokenStatus.WAITING);
    }

    @Test
    @DisplayName("Upload document via REST API, verify AI analysis, and allow calling next")
    void testUploadAndVerificationAllowsCallNext() throws Exception {
        UploadDocumentRequest uploadReq = new UploadDocumentRequest();
        uploadReq.setDocumentType("GOVERNMENT_ID");
        uploadReq.setFileName("passport_photo.pdf");
        uploadReq.setFileContentType("application/pdf");
        uploadReq.setFileData("JVBERi0xLjQKJcTl8uXrp/Og0MTGcGd...");

        mockMvc.perform(post("/api/documents/tokens/" + token.getId() + "/upload")
                        .header("Authorization", "Bearer " + customerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.fileName").value("passport_photo.pdf"));

        // Now call next should SUCCEED
        TenantContext.setCurrentTenant(organization.getId());
        QueueToken calledToken = queueService.callNext(queue.getId(), null, staff);
        assertThat(calledToken.getId()).isEqualTo(token.getId());
        assertThat(calledToken.getStatus()).isEqualTo(TokenStatus.CALLED);
    }

    @Test
    @DisplayName("Staff manual override allows approving rejected document and proceeding")
    void testStaffManualOverride() throws Exception {
        UploadDocumentRequest uploadReq = new UploadDocumentRequest();
        uploadReq.setDocumentType("GOVERNMENT_ID");
        uploadReq.setFileName("invalid_script.exe");
        uploadReq.setFileData("MZ9000...");

        String responseJson = mockMvc.perform(post("/api/documents/tokens/" + token.getId() + "/upload")
                        .header("Authorization", "Bearer " + customerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"))
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(responseJson).path("data").path("id").asText();

        // Still blocked
        TenantContext.setCurrentTenant(organization.getId());
        assertThatThrownBy(() -> queueService.callNext(queue.getId(), null, staff))
                .isInstanceOf(DocumentVerificationRequiredException.class);

        // Staff manually reviews and approves
        ReviewDocumentRequest reviewReq = new ReviewDocumentRequest();
        reviewReq.setStatus(DocumentVerificationStatus.MANUALLY_APPROVED);
        reviewReq.setNotes("Verified original physical passport at reception desk.");

        mockMvc.perform(post("/api/documents/" + docId + "/review")
                        .header("Authorization", "Bearer " + staffJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("MANUALLY_APPROVED"));

        // Call next should now SUCCEED
        TenantContext.setCurrentTenant(organization.getId());
        QueueToken called = queueService.callNext(queue.getId(), null, staff);
        assertThat(called.getStatus()).isEqualTo(TokenStatus.CALLED);
    }

    @Test
    @DisplayName("GET /api/documents/tokens/{tokenId}/status reports verification readiness")
    void testGetTokenVerificationStatus() throws Exception {
        mockMvc.perform(get("/api/documents/tokens/" + token.getId() + "/status")
                        .header("Authorization", "Bearer " + customerJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isEligibleForService").value(false))
                .andExpect(jsonPath("$.data.status").value("MISSING"));
    }
}
