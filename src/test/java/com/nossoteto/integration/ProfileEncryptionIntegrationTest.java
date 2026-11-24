package com.nossoteto.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileEncryptionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void sensitiveFieldsAreEncryptedAtRestAndPlainThroughTheApi() throws Exception {
        String email = "crypto-" + UUID.randomUUID() + "@nossoteto.com";
        MvcResult reg = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"C\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asText();
        MvcResult login = mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", email).param("password", "senha123"))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("access_token").asText();

        String longAllergies = "Amendoim, ".repeat(40);
        mockMvc.perform(put("/api/users/me/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alergias\":\"" + longAllergies + "\",\"biografia\":\"Gosto de jazz\"}"))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("SELECT allergies, bio FROM user_profiles WHERE user_id = ?", userId);
        assertTrue(row.get("allergies").toString().startsWith("v1:"));
        assertFalse(row.get("allergies").toString().contains("Amendoim"));
        assertTrue(row.get("bio").toString().startsWith("v1:"));
        assertFalse(row.get("bio").toString().contains("jazz"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gosto de jazz")));

        jdbc.update("UPDATE user_profiles SET bio = ? WHERE user_id = ?", "texto legado", userId);
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("texto legado")));

        mockMvc.perform(put("/api/users/me/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alergias\":\"Lactose\",\"biografia\":\"texto legado\"}"))
                .andExpect(status().isOk());

        Map<String, Object> reencryptedRow = jdbc.queryForMap("SELECT allergies, bio FROM user_profiles WHERE user_id = ?", userId);
        assertTrue(reencryptedRow.get("bio").toString().startsWith("v1:"));
        assertFalse(reencryptedRow.get("bio").toString().contains("texto legado"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("texto legado")));
    }

    @Test
    void largeBioExceedingMySqlTextCiphertextLimitCanBeSavedAndReadBack() throws Exception {
        String email = "crypto-large-" + UUID.randomUUID() + "@nossoteto.com";
        MvcResult reg = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"LargeBio\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asText();
        MvcResult login = mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", email).param("password", "senha123"))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("access_token").asText();

        String largeBio = "a".repeat(60_000);
        mockMvc.perform(put("/api/users/me/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"biografia\":\"" + largeBio + "\"}"))
                .andExpect(status().isOk());

        String storedBio = jdbc.queryForObject(
                "SELECT bio FROM user_profiles WHERE user_id = ?", String.class, userId);
        assertNotNull(storedBio);
        assertTrue(storedBio.startsWith("v1:"));
        assertTrue(storedBio.length() > 65_535,
                "Ciphertext for 60,000-char bio must exceed MySQL TEXT 65,535-byte limit");

        MvcResult me = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String returnedBio = objectMapper.readTree(me.getResponse().getContentAsString())
                .path("profile").path("bio").asText();
        assertEquals(60_000, returnedBio.length());
        assertEquals(largeBio, returnedBio);
    }
}
