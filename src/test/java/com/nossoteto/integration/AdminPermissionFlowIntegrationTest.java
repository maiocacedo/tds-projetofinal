package com.nossoteto.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nossoteto.model.User;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.AdminBootstrap;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPermissionFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AdminBootstrap adminBootstrap;
    @Autowired private UserRepository userRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", email).param("password", password))
                .andExpect(status().isOk()).andReturn();
    }

    private String access(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("access_token").asText();
    }

    @Test
    void adminRevokesPermissionAndItAppliesOnNextRefresh() throws Exception {
        String email = "victim-" + UUID.randomUUID() + "@nossoteto.com";
        MvcResult reg = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"V\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated()).andReturn();
        String userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asText();

        MvcResult userLogin = login(email, "senha123");
        Cookie userCookie = userLogin.getResponse().getCookie("refresh_token");
        String adminToken = access(login("admin@test.local", "admin-test-pass"));

        MvcResult listResult = mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(listResult.getResponse().getContentAsString().contains("passwordHash"));

        mockMvc.perform(put("/api/admin/users/" + userId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"ROLE_MANAGE_PROFILE\",\"ROLE_SEARCH_HOUSES\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", not(hasItem("ROLE_MANAGE_HOUSE"))));

        MvcResult refreshed = mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "refresh_token")
                        .cookie(userCookie))
                .andExpect(status().isOk()).andReturn();

        mockMvc.perform(post("/api/houses").header("Authorization", "Bearer " + access(refreshed))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Insufficient permission"));
    }

    @Test
    void regularUserCannotUseAdminEndpoints() throws Exception {
        String email = "plain-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"P\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"));
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + access(login(email, "senha123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Insufficient permission"));
    }

    @Test
    void adminSelfDemotionUnknownPermissionAndUnknownUserReturnExpectedErrors() throws Exception {
        MvcResult adminLogin = login("admin@test.local", "admin-test-pass");
        String adminToken = access(adminLogin);
        String adminId = objectMapper.readTree(adminLogin.getResponse().getContentAsString()).get("user_id").asText();

        mockMvc.perform(put("/api/admin/users/" + adminId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"ROLE_MANAGE_PROFILE\"]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/users/" + adminId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"ROLE_ADMIN_USERS\",\"ROLE_DOES_NOT_EXIST\"]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/users/non-existent-id/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[]}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/admin/users/" + adminId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminBootstrapIsIdempotentDoesNotResetPasswordAndSkipsWhenPasswordBlank() throws Exception {
        User existing = userRepository.findByEmail("admin@test.local").orElseThrow();
        String originalHash = existing.getPasswordHash();

        adminBootstrap.run(new DefaultApplicationArguments());
        adminBootstrap.run(new DefaultApplicationArguments());

        User afterSecondRun = userRepository.findByEmail("admin@test.local").orElseThrow();
        assertEquals(existing.getId(), afterSecondRun.getId());
        assertEquals(originalHash, afterSecondRun.getPasswordHash());
        assertEquals(9, userRepository.findPermissionNamesByUserId(afterSecondRun.getId()).size());
        assertNotNull(access(login("admin@test.local", "admin-test-pass")));

        String skippedEmail = "skipped-" + UUID.randomUUID() + "@test.local";
        new AdminBootstrap(userRepository, permissionRepository, passwordEncoder, skippedEmail, "")
                .run(new DefaultApplicationArguments());
        assertTrue(userRepository.findByEmail(skippedEmail).isEmpty());
    }

    @Test
    void adminBootstrapRefusesToPromoteExistingNonAdminAccount() throws Exception {
        String email = "squatter-" + UUID.randomUUID() + "@nossoteto.com";
        MvcResult reg = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Squatter\",\"email\":\"" + email + "\",\"senha\":\"senha-atacante\"}"))
                .andExpect(status().isCreated()).andReturn();
        String userId = objectMapper.readTree(reg.getResponse().getContentAsString()).get("id").asText();
        Set<String> permissionsBefore = new HashSet<>(userRepository.findPermissionNamesByUserId(userId));
        String hashBefore = userRepository.findByEmail(email).orElseThrow().getPasswordHash();

        new AdminBootstrap(userRepository, permissionRepository, passwordEncoder, email, "pw-do-operador")
                .run(new DefaultApplicationArguments());

        List<String> permissionsAfter = userRepository.findPermissionNamesByUserId(userId);
        assertFalse(permissionsAfter.contains("ROLE_ADMIN_USERS"));
        assertEquals(permissionsBefore, new HashSet<>(permissionsAfter));
        assertEquals(hashBefore, userRepository.findByEmail(email).orElseThrow().getPasswordHash());

        mockMvc.perform(post("/oauth/token").with(httpBasic("nosso-teto-web", "web-test-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", email).param("password", "pw-do-operador"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        assertNotNull(access(login(email, "senha-atacante")));
    }

    @Test
    void adminBootstrapCreatesNewAdminWhenEmailDoesNotExist() throws Exception {
        String freshEmail = "fresh-admin-" + UUID.randomUUID() + "@nossoteto.com";
        new AdminBootstrap(userRepository, permissionRepository, passwordEncoder, freshEmail, "fresh-admin-pass")
                .run(new DefaultApplicationArguments());

        User created = userRepository.findByEmail(freshEmail).orElseThrow();
        List<String> perms = userRepository.findPermissionNamesByUserId(created.getId());
        assertEquals(9, perms.size());
        assertTrue(perms.contains("ROLE_ADMIN_USERS"));
        assertNotNull(access(login(freshEmail, "fresh-admin-pass")));
    }
}
