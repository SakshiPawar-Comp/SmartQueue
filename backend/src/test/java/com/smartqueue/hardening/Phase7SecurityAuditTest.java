package com.smartqueue.hardening;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.auth.api.dto.LoginRequestDto;
import com.smartqueue.auth.api.dto.RegisterRequestDto;
import com.smartqueue.branch.api.dto.CreateBranchRequest;
import com.smartqueue.branch.api.dto.UpdateBranchRequest;
import jakarta.servlet.http.Cookie;
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

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class Phase7SecurityAuditTest {

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

    private String registerOrgAdmin(String email, String password, String orgName) throws Exception {
        RegisterRequestDto reg = new RegisterRequestDto();
        reg.setEmail(email);
        reg.setPassword(password);
        reg.setFullName("Admin " + orgName);
        reg.setRole(RegisterRequestDto.RoleCode.ORGANIZATION_ADMIN);

        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();

        String json = res.getResponse().getContentAsString();
        return objectMapper.readTree(json).path("data").path("user").path("organizationId").asText();
    }

    private String registerCustomer(String email, String password) throws Exception {
        String adminEmail = "orgowner_" + UUID.randomUUID() + "@org.com";
        RegisterRequestDto orgAdminReg = new RegisterRequestDto();
        orgAdminReg.setEmail(adminEmail);
        orgAdminReg.setPassword(password);
        orgAdminReg.setFullName("Org Owner");
        orgAdminReg.setRole(RegisterRequestDto.RoleCode.ORGANIZATION_ADMIN);

        MvcResult adminRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgAdminReg)))
                .andExpect(status().isCreated())
                .andReturn();

        String orgAdminId = objectMapper.readTree(adminRes.getResponse().getContentAsString())
                .path("data").path("user").path("id").asText();
        String orgSlug = "org-" + orgAdminId.substring(0, 8);

        RegisterRequestDto custReg = new RegisterRequestDto();
        custReg.setEmail(email);
        custReg.setPassword(password);
        custReg.setFullName("Customer " + email);
        custReg.setRole(RegisterRequestDto.RoleCode.CUSTOMER);
        custReg.setOrganizationCode(orgSlug);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReg)))
                .andExpect(status().isCreated());

        return getAccessToken(email, password);
    }

    @Test
    @DisplayName("Security: Refresh Token Reuse Detection & Revocation on Logout")
    void testRefreshTokenReplayAttackAndLogoutRevocation() throws Exception {
        String email = "p7sec_" + UUID.randomUUID() + "@smartqueue.test";
        String password = "Password123!";
        registerOrgAdmin(email, password, "SecOrg");

        // Login to get initial refresh cookie
        LoginRequestDto loginDto = new LoginRequestDto();
        loginDto.setEmail(email);
        loginDto.setPassword(password);

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("sq_rt"))
                .andReturn();

        Cookie oldCookie = loginRes.getResponse().getCookie("sq_rt");

        // Refresh once -> rotates token
        MvcResult refreshRes = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(oldCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("sq_rt"))
                .andReturn();

        Cookie newCookie = refreshRes.getResponse().getCookie("sq_rt");

        // Attempt replay attack with old rotated token -> must be rejected with 401
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(oldCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSE"));

        // Logout with current token -> revokes session (returns 204 No Content)
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(newCookie))
                .andExpect(status().isNoContent());

        // Attempt to refresh after logout -> must be rejected with 401
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(newCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Cross-Tenant IDOR and Horizontal Access Isolation")
    void testCrossTenantIdorRejection() throws Exception {
        String orgAAdminEmail = "adminA_" + UUID.randomUUID() + "@orgA.com";
        String orgBAdminEmail = "adminB_" + UUID.randomUUID() + "@orgB.com";
        String password = "Password123!";

        String orgAId = registerOrgAdmin(orgAAdminEmail, password, "Org A");
        String orgBId = registerOrgAdmin(orgBAdminEmail, password, "Org B");

        String tokenA = getAccessToken(orgAAdminEmail, password);
        String tokenB = getAccessToken(orgBAdminEmail, password);

        // Org A creates a branch
        CreateBranchRequest branchReq = new CreateBranchRequest();
        branchReq.setName("Main Branch A");
        branchReq.setCode("BR-A1");
        MvcResult brRes = mockMvc.perform(post("/api/branches")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("X-Organization-Id", orgAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branchReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String branchAId = objectMapper.readTree(brRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Org B attempts to update Org A's branch -> must be rejected with 403 Forbidden
        UpdateBranchRequest updateReq = new UpdateBranchRequest();
        updateReq.setName("Hacked Branch Name");
        mockMvc.perform(put("/api/branches/" + branchAId)
                        .header("Authorization", "Bearer " + tokenB)
                        .header("X-Organization-Id", orgBId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: RBAC Enforcement for Protected Endpoints")
    void testRbacRoleRestrictions() throws Exception {
        String customerEmail = "cust_" + UUID.randomUUID() + "@test.com";
        String customerToken = registerCustomer(customerEmail, "Password123!");

        // Customer attempts to create branch -> 403
        CreateBranchRequest branchReq = new CreateBranchRequest();
        branchReq.setName("Customer Branch");
        branchReq.setCode("CB-1");

        mockMvc.perform(post("/api/branches")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branchReq)))
                .andExpect(status().isForbidden());

        // Customer attempts to access admin overview -> 403
        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        // Customer attempts to download daily operational report -> 403
        mockMvc.perform(get("/api/reports/daily")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: Unauthenticated Access to Private APIs is Blocked")
    void testUnauthenticatedAccessBlocked() throws Exception {
        mockMvc.perform(get("/api/customers/me/profile"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isForbidden());
    }
}
