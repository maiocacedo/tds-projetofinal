package com.nossoteto.controller;

import com.nossoteto.model.Permission;
import com.nossoteto.model.User;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import com.nossoteto.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthService.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PermissionRepository permissionRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    void registerShouldReturn201WithEnglishFields() throws Exception {
        Permission manageProfile = new Permission(1, Permissions.MANAGE_PROFILE, "Manage profile");
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.empty());
        when(permissionRepository.findByNameNot(Permissions.ADMIN_USERS)).thenReturn(List.of(manageProfile));
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String json = """
                {
                  "name": "Ana Silva",
                  "email": "ana@test.com",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Ana Silva"))
                .andExpect(jsonPath("$.email").value("ana@test.com"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("Ana Silva", captor.getValue().getName());
        assertEquals("ana@test.com", captor.getValue().getEmail());
        assertEquals("hashed-password", captor.getValue().getPasswordHash());
    }

    @Test
    void registerShouldSupportPortugueseAliasesAndReturn201() throws Exception {
        when(userRepository.findByEmail("carlos@test.com")).thenReturn(Optional.empty());
        when(permissionRepository.findByNameNot(Permissions.ADMIN_USERS)).thenReturn(List.of());
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pt");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String ptJson = """
                {
                  "nome": "Carlos Souza",
                  "email": "carlos@test.com",
                  "senha": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ptJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Carlos Souza"))
                .andExpect(jsonPath("$.email").value("carlos@test.com"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("Carlos Souza", captor.getValue().getName());
        assertEquals("carlos@test.com", captor.getValue().getEmail());
        assertEquals("hashed-pt", captor.getValue().getPasswordHash());
    }

    @Test
    void registerShouldReturn400WhenNameIsRequired() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name is required"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerShouldReturn400WhenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"a@b..c\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid email"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerShouldReturn400WhenPasswordIsTooShort() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@test.com\",\"password\":\"1234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password must have at least 8 characters"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerShouldReturn400WhenEmailAlreadyExists() throws Exception {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(new User()));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already exists"));

        verify(userRepository, never()).save(any());
    }
}
