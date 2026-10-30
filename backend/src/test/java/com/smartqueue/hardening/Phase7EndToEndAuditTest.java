package com.smartqueue.hardening;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.auth.api.dto.LoginRequestDto;
import com.smartqueue.auth.api.dto.RegisterRequestDto;
import com.smartqueue.branch.api.dto.CreateBranchRequest;
import com.smartqueue.counter.api.dto.AssignQueuesRequest;
import com.smartqueue.counter.api.dto.CreateCounterRequest;
import com.smartqueue.queue.api.dto.CreateQueueRequest;
import com.smartqueue.service.api.dto.CreateServiceRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class Phase7EndToEndAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String getAccessToken(String email, String password) throws Exception {
        LoginRequestDto loginDto = new LoginRequestDto();
        loginDto.setEmail(email);
        loginDto.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        return objectMapper.readTree(json).path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("End-to-End: Full Lifecycle Verification (Org -> Discovery -> Token -> Serve -> History -> Analytics -> CSV)")
    void testCompleteProductLifecycle() throws Exception {
        // 1. Organization Admin Registration & Login
        String adminEmail = "e2e_admin_" + UUID.randomUUID() + "@enterprise.test";
        String password = "Password123!";

        RegisterRequestDto orgAdminReg = new RegisterRequestDto();
        orgAdminReg.setEmail(adminEmail);
        orgAdminReg.setPassword(password);
        orgAdminReg.setFullName("Enterprise Admin");
        orgAdminReg.setRole(RegisterRequestDto.RoleCode.ORGANIZATION_ADMIN);

        MvcResult adminRegRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgAdminReg)))
                .andExpect(status().isCreated())
                .andReturn();

        String adminUserId = objectMapper.readTree(adminRegRes.getResponse().getContentAsString())
                .path("data").path("user").path("id").asText();
        String orgId = objectMapper.readTree(adminRegRes.getResponse().getContentAsString())
                .path("data").path("user").path("organizationId").asText();
        String orgSlug = "org-" + adminUserId.substring(0, 8);

        String adminToken = getAccessToken(adminEmail, password);

        // 2. Create Branch
        CreateBranchRequest branchReq = new CreateBranchRequest();
        branchReq.setName("Main Downtown Branch");
        branchReq.setCode("BR-DT1");
        MvcResult branchRes = mockMvc.perform(post("/api/branches")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branchReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String branchId = objectMapper.readTree(branchRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 3. Create Service
        CreateServiceRequest serviceReq = new CreateServiceRequest();
        serviceReq.setName("Express Support");
        serviceReq.setCode("SRV-EXP");
        serviceReq.setBranchId(UUID.fromString(branchId));
        MvcResult serviceRes = mockMvc.perform(post("/api/services")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(serviceReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = objectMapper.readTree(serviceRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 4. Create Queue
        CreateQueueRequest queueReq = new CreateQueueRequest();
        queueReq.setName("Express Queue A");
        queueReq.setTokenPrefix("E");
        queueReq.setBranchId(UUID.fromString(branchId));
        queueReq.setServiceId(UUID.fromString(serviceId));
        queueReq.setCapacity(0);
        queueReq.setDefaultServiceTimeMinutes(5);

        MvcResult queueRes = mockMvc.perform(post("/api/queues")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queueReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String queueId = objectMapper.readTree(queueRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Open Queue
        mockMvc.perform(post("/api/queues/" + queueId + "/open")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk());

        // 5. Create Counter and Assign Queue
        CreateCounterRequest counterReq = new CreateCounterRequest();
        counterReq.setName("Counter Express 1");
        counterReq.setCode("C-1");
        counterReq.setBranchId(UUID.fromString(branchId));

        MvcResult counterRes = mockMvc.perform(post("/api/counters")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(counterReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String counterId = objectMapper.readTree(counterRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        AssignQueuesRequest assignQueuesReq = new AssignQueuesRequest();
        assignQueuesReq.setQueueIds(List.of(UUID.fromString(queueId)));
        mockMvc.perform(post("/api/counters/" + counterId + "/assign-queues")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignQueuesReq)))
                .andExpect(status().isOk());

        // 6. Customer Registration & Login
        String custEmail = "e2e_cust_" + UUID.randomUUID() + "@user.test";
        RegisterRequestDto custReg = new RegisterRequestDto();
        custReg.setEmail(custEmail);
        custReg.setPassword(password);
        custReg.setFullName("Jane Doe");
        custReg.setRole(RegisterRequestDto.RoleCode.CUSTOMER);
        custReg.setOrganizationCode(orgSlug);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReg)))
                .andExpect(status().isCreated());

        String custToken = getAccessToken(custEmail, password);

        // 7. Customer checks QR Code details
        mockMvc.perform(get("/api/qr/queue/" + queueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value("QUEUE"))
                .andExpect(jsonPath("$.data.targetId").value(queueId));

        // 8. Customer Joins Queue
        MvcResult joinRes = mockMvc.perform(post("/api/queues/" + queueId + "/tokens")
                        .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenNumber").value("E001"))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andReturn();

        String tokenId = objectMapper.readTree(joinRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 9. Customer checks Position & Estimate
        mockMvc.perform(get("/api/tokens/" + tokenId + "/estimate")
                        .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.estimatedWaitMinutes").value(0));

        // 10. Staff calls token via Counter
        mockMvc.perform(post("/api/counters/" + counterId + "/next")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tokenId))
                .andExpect(jsonPath("$.data.status").value("CALLED"));

        // 11. Staff puts token on HOLD and RESUMES
        mockMvc.perform(post("/api/tokens/" + tokenId + "/hold")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HOLD"));

        mockMvc.perform(post("/api/tokens/" + tokenId + "/resume")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING"));

        // Re-call resumed token via Counter
        mockMvc.perform(post("/api/counters/" + counterId + "/next")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(tokenId))
                .andExpect(jsonPath("$.data.status").value("CALLED"));

        // 12. Staff completes token
        mockMvc.perform(post("/api/tokens/" + tokenId + "/complete")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 13. Customer checks History
        mockMvc.perform(get("/api/customers/me/tokens/history")
                        .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].tokenNumber").value("E001"))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"));

        // 14. Admin checks Analytics Overview
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTokens").value(1))
                .andExpect(jsonPath("$.data.completedTokens").value(1));

        // 15. Admin exports CSV Report
        mockMvc.perform(get("/api/reports/daily")
                        .param("format", "csv")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Organization-Id", orgId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")));
    }
}
