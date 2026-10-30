package com.smartqueue.notification;

import com.smartqueue.organization.domain.Organization;
import com.smartqueue.organization.infrastructure.OrganizationRepository;
import com.smartqueue.queue.domain.Queue;
import com.smartqueue.queue.domain.QueueStatus;
import com.smartqueue.queue.infrastructure.QueueRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QrCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private QueueRepository queueRepository;

    private Organization organization;
    private Queue queue;

    @BeforeEach
    void setUp() {
        organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("Apex Hospital");
        organization.setSlug("apex-hosp-" + UUID.randomUUID().toString().substring(0, 8));
        organization.setStatus("ACTIVE");
        organization.setCreatedAt(ZonedDateTime.now());
        organization.setUpdatedAt(ZonedDateTime.now());
        organization = organizationRepository.save(organization);

        queue = new Queue();
        queue.setId(UUID.randomUUID());
        queue.setOrganizationId(organization.getId());
        queue.setName("Cardiology Clinic");
        queue.setTokenPrefix("C");
        queue.setCurrentTokenNumber("C000");
        queue.setStatus(QueueStatus.ACTIVE);
        queue.setCreatedAt(ZonedDateTime.now());
        queue.setUpdatedAt(ZonedDateTime.now());
        queue = queueRepository.save(queue);
    }

    @Test
    @DisplayName("QR Code: GET /api/qr/queue/{queueId} returns JSON with data URL and deep link")
    void testGetQueueQrInfo() throws Exception {
        mockMvc.perform(get("/api/qr/queue/" + queue.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrDataUri", startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.data.deepLinkUrl", containsString("/join?queueId=" + queue.getId())));
    }

    @Test
    @DisplayName("QR Code Image: GET /api/qr/queue/{queueId}/image returns PNG byte stream")
    void testGetQueueQrImage() throws Exception {
        mockMvc.perform(get("/api/qr/queue/" + queue.getId() + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    @DisplayName("QR Code LAN IP: GET /api/qr/lan-ip returns host IP")
    void testGetLanIp() throws Exception {
        mockMvc.perform(get("/api/qr/lan-ip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ip", notNullValue()));
    }
}
