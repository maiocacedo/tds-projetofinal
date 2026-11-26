package com.nossoteto.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.auth.demo-password-reset=true")
class PasswordResetDemoEnabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullDemoResetFlowChangesPasswordRevokesRefreshTokensAndBlocksReuse() throws Exception {
        String email = "reset-demo-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bruno\",\"email\":\"" + email + "\",\"password\":\"senhaAntiga1\"}"))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", "nosso-teto-web")
                        .param("grant_type", "password")
                        .param("username", email)
                        .param("password", "senhaAntiga1"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie oldRefreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertNotNull(oldRefreshCookie);

        MvcResult resetRequestResult = mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.demoToken", not(emptyOrNullString())))
                .andReturn();
        String demoToken = objectMapper.readTree(resetRequestResult.getResponse().getContentAsString())
                .get("demoToken")
                .asText();

        // Short password fails without burning the token
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + demoToken + "\",\"newPassword\":\"1234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password must have at least 8 characters"));

        // Valid confirm returns 204 No Content
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + demoToken + "\",\"newPassword\":\"novaSenha456\"}"))
                .andExpect(status().isNoContent());

        // Login with NEW password succeeds (200)
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", "nosso-teto-web")
                        .param("grant_type", "password")
                        .param("username", email)
                        .param("password", "novaSenha456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", not(emptyOrNullString())));

        // Login with OLD password fails (400 invalid_grant)
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", "nosso-teto-web")
                        .param("grant_type", "password")
                        .param("username", email)
                        .param("password", "senhaAntiga1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        // Old refresh token fails (400 invalid_grant)
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", "nosso-teto-web")
                        .param("grant_type", "refresh_token")
                        .cookie(oldRefreshCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        // Reusing demoToken fails (400 "invalid or expired token")
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + demoToken + "\",\"newPassword\":\"outraSenha789\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid or expired token"));
    }

    @Test
    void requestResetForUnknownEmailReturns202AndEmptyObjectEvenWhenDemoFlagIsTrue() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-existe-" + UUID.randomUUID() + "@nossoteto.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{}", true))
                .andExpect(jsonPath("$.demoToken").doesNotExist());
    }
}
