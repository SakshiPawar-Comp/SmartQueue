package com.smartqueue.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartqueue.auth.api.dto.LoginRequestDto;
import com.smartqueue.auth.api.dto.RegisterRequestDto;
import com.smartqueue.common.security.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    private static final String COOKIE_NAME = "sq_rt";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProvider jwtProvider;

    private RegisterRequestDto registerRequest(String email, String password, String fullName) {
        RegisterRequestDto r = new RegisterRequestDto();
        r.setEmail(email);
        r.setPassword(password);
        r.setFullName(fullName);
        r.setRole(RegisterRequestDto.RoleCode.ORGANIZATION_ADMIN);
        return r;
    }

    private LoginRequestDto loginRequest(String email, String password) {
        LoginRequestDto r = new LoginRequestDto();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    private MvcResult registerUser(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest(email, password, "User " + email))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(body);
        return node.get("data").get("accessToken").asText();
    }

    private String extractRefreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        if (setCookie == null) {
            return null;
        }
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(COOKIE_NAME + "=")) {
                return trimmed.substring(COOKIE_NAME.length() + 1);
            }
        }
        return null;
    }

    private Cookie buildRefreshCookie(String value) {
        Cookie cookie = new Cookie(COOKIE_NAME, value);
        cookie.setPath("/api/auth/refresh");
        return cookie;
    }

    // --- Register ---

    @Test
    void register_succeeds() throws Exception {
        MvcResult result = registerUser("test@example.com", "password123");

        String accessToken = extractAccessToken(result);
        String refreshCookie = extractRefreshCookie(result);

        org.junit.jupiter.api.Assertions.assertNotNull(accessToken);
        org.junit.jupiter.api.Assertions.assertNotNull(refreshCookie);
    }

    @Test
    void register_duplicateEmail_fails() throws Exception {
        registerUser("dup@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest("dup@example.com", "password123", "Dup"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void register_validationError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest("bad", "short", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- Login ---

    @Test
    void login_succeeds() throws Exception {
        registerUser("login@example.com", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest("login@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("login@example.com"))
                .andReturn();

        String refreshCookie = extractRefreshCookie(result);
        org.junit.jupiter.api.Assertions.assertNotNull(refreshCookie);
    }

    @Test
    void login_invalidPassword_fails() throws Exception {
        registerUser("wrong@example.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest("wrong@example.com", "badpassword"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_nonexistentUser_fails() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest("ghost@example.com", "password123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    // --- Refresh ---

    @Test
    void refresh_withValidCookie_succeeds() throws Exception {
        MvcResult registerResult = registerUser("refresh@example.com", "password123");
        String oldRefreshCookie = extractRefreshCookie(registerResult);
        org.junit.jupiter.api.Assertions.assertNotNull(oldRefreshCookie);

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(buildRefreshCookie(oldRefreshCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String newRefreshCookie = extractRefreshCookie(refreshResult);
        org.junit.jupiter.api.Assertions.assertNotNull(newRefreshCookie);
        org.junit.jupiter.api.Assertions.assertNotEquals(oldRefreshCookie, newRefreshCookie);
    }

    @Test
    void refresh_withoutCookie_fails() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withRevokedToken_revokesAll() throws Exception {
        MvcResult registerResult = registerUser("revoke@example.com", "password123");
        String firstRefreshCookie = extractRefreshCookie(registerResult);

        // First refresh - should succeed
        MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(buildRefreshCookie(firstRefreshCookie)))
                .andExpect(status().isOk())
                .andReturn();
        String secondRefreshCookie = extractRefreshCookie(firstRefresh);

        // Second refresh with same old token - reuse detected
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(buildRefreshCookie(firstRefreshCookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_REUSE"));

        // Second refresh token should also be revoked now
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(buildRefreshCookie(secondRefreshCookie)))
                .andExpect(status().isUnauthorized());
    }

    // --- Logout ---

    @Test
    void logout_clearsCookie() throws Exception {
        MvcResult registerResult = registerUser("logout@example.com", "password123");
        String refreshCookie = extractRefreshCookie(registerResult);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(buildRefreshCookie(refreshCookie)))
                .andExpect(status().isNoContent());

        // Verify old refresh token is revoked - refresh should fail
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(buildRefreshCookie(refreshCookie)))
                .andExpect(status().isUnauthorized());
    }

    // --- /me ---

    @Test
    void me_withValidToken_succeeds() throws Exception {
        MvcResult registerResult = registerUser("me@example.com", "password123");
        String accessToken = extractAccessToken(registerResult);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("User me@example.com"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void me_withoutToken_fails() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    // --- Cookie security ---

    @Test
    void refresh_token_notInJson() throws Exception {
        MvcResult result = registerUser("json@example.com", "password123");

        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("refreshToken"),
                "JSON response must not contain refreshToken field");
    }

    @Test
    void cookie_hasHttpOnlyAndSameSite() throws Exception {
        MvcResult result = registerUser("cookie@example.com", "password123");

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("HttpOnly"),
                "Cookie must be HttpOnly");
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("SameSite=Strict"),
                "Cookie must have SameSite=Strict");
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains(COOKIE_NAME + "="),
                "Cookie must have correct name");
    }
}
