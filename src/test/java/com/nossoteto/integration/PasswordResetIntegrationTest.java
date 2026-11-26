package com.nossoteto.integration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requestPasswordResetWithoutAuthReturns202AndEmptyObjectWhenDemoFlagIsFalse() throws Exception {
        String email = "reset-default-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"senha123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{}", true))
                .andExpect(jsonPath("$.demoToken").doesNotExist());
    }

    @Test
    void requestPasswordResetForUnknownEmailWithoutAuthReturns202AndEmptyObject() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-existe@nossoteto.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{}", true))
                .andExpect(jsonPath("$.demoToken").doesNotExist());
    }

    @Test
    void confirmPasswordResetWithInvalidTokenWithoutAuthReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-invalido\",\"newPassword\":\"novaSenha123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid or expired token"));
    }

    @Test
    void confirmPasswordResetWithShortPasswordWithoutAuthReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"qualquer-token\",\"newPassword\":\"1234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password must have at least 8 characters"));
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("basic-security")
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    class BasicSecurityProfilePasswordResetTest {

        @Autowired
        private MockMvc basicMockMvc;

        @Autowired
        private Environment environment;

        @Test
        void passwordResetEndpointsArePublicInBasicSecurityProfile() throws Exception {
            assertTrue(environment.acceptsProfiles(Profiles.of("basic-security")));

            basicMockMvc.perform(post("/api/auth/password-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"alguem@nossoteto.com\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(content().json("{}", true));

            basicMockMvc.perform(post("/api/auth/password-reset/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"token-invalido\",\"newPassword\":\"novaSenha123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid or expired token"));
        }
    }
}
