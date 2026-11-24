package com.nossoteto.integration;

import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PermissionSeedIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void migrationSeedsTheNinePermissionCatalog() {
        assertEquals(9, permissionRepository.count());
    }

    @Test
    void registeredUserReceivesDefaultPermissionsWithoutAdmin() throws Exception {
        String email = "perm-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Perm\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated());

        String userId = userRepository.findByEmail(email).orElseThrow().getId();
        assertEquals(Permissions.DEFAULT_USER, new HashSet<>(userRepository.findPermissionNamesByUserId(userId)));
    }
}
