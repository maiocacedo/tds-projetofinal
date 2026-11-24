package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    private String email;

    @BeforeEach
    void register() throws Exception {
        email = "refresh-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"R\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated());
    }

    private MvcResult passwordLogin() throws Exception {
        return mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", email).param("password", "senha123"))
                .andExpect(status().isOk()).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions refresh(Cookie cookie) throws Exception {
        var req = post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "refresh_token");
        if (cookie != null) req.cookie(cookie);
        return mockMvc.perform(req);
    }

    @Test
    void passwordGrantSetsHardenedCookieAndNeverReturnsRefreshInBody() throws Exception {
        MvcResult r = passwordLogin();
        String setCookie = r.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith("refresh_token="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        assertTrue(setCookie.contains("Path=/oauth/token"));
        assertFalse(r.getResponse().getContentAsString().contains("refresh"));
    }

    @Test
    void refreshRotatesAndOldCookieIsReuse() throws Exception {
        Cookie first = passwordLogin().getResponse().getCookie("refresh_token");
        MvcResult rotated = refresh(first).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.access_token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andReturn();
        Cookie second = rotated.getResponse().getCookie("refresh_token");
        assertNotEquals(first.getValue(), second.getValue());
        assertFalse(rotated.getResponse().getContentAsString().contains("refresh"));

        refresh(first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        // family revoked: the legitimately rotated token is dead too
        refresh(second).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void refreshWithoutCookieIsInvalidGrant() throws Exception {
        refresh(null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void refreshTokenInBodyWithoutCookieIsIgnoredAndRejected() throws Exception {
        Cookie cookie = passwordLogin().getResponse().getCookie("refresh_token");
        mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", cookie.getValue()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void refreshByDifferentClientIsInvalidGrantAndDoesNotRevokeOwner() throws Exception {
        Cookie cookie = passwordLogin().getResponse().getCookie("refresh_token");
        mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-mobile", "mobile-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        // Owner client can still use its legitimately issued token.
        refresh(cookie).andExpect(status().isOk());
    }

    @Test
    void refreshPreservesOriginalScopeAndRereadsPermissionsFromDatabase() throws Exception {
        MvcResult login = mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", email)
                        .param("password", "senha123")
                        .param("scope", "read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("read"))
                .andReturn();
        Cookie cookie = login.getResponse().getCookie("refresh_token");

        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.getPermissions().removeIf(p -> Permissions.MANAGE_EXPENSES.equals(p.getName()));
            userRepository.save(user);
        });

        MvcResult refreshed = mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("scope", "read write")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("read"))
                .andReturn();

        JsonNode body = objectMapper.readTree(refreshed.getResponse().getContentAsString());
        Jwt jwt = jwtDecoder.decode(body.get("access_token").asText());
        assertEquals("read", jwt.getClaimAsString("scope"));
        assertEquals("nosso-teto-web", jwt.getClaimAsString("client_id"));
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        assertTrue(authorities.contains(Permissions.MANAGE_PROFILE));
        assertFalse(authorities.contains(Permissions.MANAGE_EXPENSES));
    }

    @Test
    void logoutRevokesFamilyAndClearsCookie() throws Exception {
        Cookie cookie = passwordLogin().getResponse().getCookie("refresh_token");
        MvcResult out = mockMvc.perform(delete("/oauth/token/revoke")
                        .with(httpBasic("nosso-teto-web", "web-test-secret")).cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertTrue(out.getResponse().getHeader("Set-Cookie").contains("Max-Age=0"));
        refresh(cookie).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void logoutIsIdempotentAndRequiresClientAuth() throws Exception {
        mockMvc.perform(delete("/oauth/token/revoke").with(httpBasic("nosso-teto-web", "web-test-secret")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/oauth/token/revoke"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void logoutByDifferentClientDoesNotRevokeOwnersFamily() throws Exception {
        Cookie cookie = passwordLogin().getResponse().getCookie("refresh_token");
        mockMvc.perform(delete("/oauth/token/revoke")
                        .with(httpBasic("nosso-teto-mobile", "mobile-test-secret"))
                        .cookie(cookie))
                .andExpect(status().isNoContent());

        // Owner client's session must remain intact.
        refresh(cookie).andExpect(status().isOk());
    }

    @Test
    void refreshAndRevokeRejectQueryStringWithInvalidRequest() throws Exception {
        Cookie cookie = passwordLogin().getResponse().getCookie("refresh_token");
        mockMvc.perform(post("/oauth/token?grant_type=refresh_token")
                        .with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        mockMvc.perform(delete("/oauth/token/revoke?token=abc")
                        .with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }
}
