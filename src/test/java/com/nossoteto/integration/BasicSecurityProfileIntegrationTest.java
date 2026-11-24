package com.nossoteto.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("basic-security")
class BasicSecurityProfileIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void basicCredentialsAuthenticateAndPrincipalIsTheUserId() throws Exception {
        String email = "basic-" + UUID.randomUUID() + "@nossoteto.com";
        MvcResult reg = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"B\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/users/me").with(httpBasic(email, "senha123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(get("/api/users/me").with(httpBasic(email, "errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"nosso-teto\""))
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void tokenEndpointDoesNotExistInBasicProfile() throws Exception {
        mockMvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password"))
                .andExpect(status().isUnauthorized());
    }
}
