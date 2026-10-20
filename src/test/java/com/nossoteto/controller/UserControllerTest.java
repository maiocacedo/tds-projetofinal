package com.nossoteto.controller;

import com.nossoteto.dto.UserMeResponse;
import com.nossoteto.dto.UserProfileDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.security.JwtAuthenticationFilter;
import com.nossoteto.security.JwtTokenProvider;
import com.nossoteto.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UsernamePasswordAuthenticationToken auth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    }

    @Test
    void getMeShouldReturnCurrentUser() throws Exception {
        UserProfileDto profileDto = new UserProfileDto();
        profileDto.setBudget(new BigDecimal("1400.00"));
        profileDto.setHasPets(true);
        profileDto.setOrganizationLevel(4);

        UserMeResponse response = new UserMeResponse(
                "user-1", "Alice", "alice@test.com", "house-1", true, "Solar", profileDto);

        when(userService.getCurrentUser("user-1")).thenReturn(response);

        mockMvc.perform(get("/api/users/me").principal(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.houseId").value("house-1"))
                .andExpect(jsonPath("$.houseName").value("Solar"))
                .andExpect(jsonPath("$.profile.budget").value(1400.00))
                .andExpect(jsonPath("$.profile.hasPets").value(true))
                .andExpect(jsonPath("$.profile.organizationLevel").value(4));
    }

    @Test
    void updateProfileShouldSupportPortugueseFieldAliases() throws Exception {
        String ptJson = """
                {
                  "orcamentoMensal": 1500.50,
                  "possuiPets": true,
                  "fumante": false,
                  "profissao": "Engenheira",
                  "biografia": "Gosto de silêncio",
                  "alergias": "Gatos",
                  "nivelOrganizacao": 5,
                  "toleranciaVisitas": 3,
                  "mobiliaPropria": true
                }
                """;

        when(userService.updateProfile(eq("user-1"), any(UserProfileDto.class)))
                .thenAnswer(inv -> inv.getArgument(1));

        mockMvc.perform(put("/api/users/me/profile")
                        .principal(auth("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ptJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget").value(1500.50))
                .andExpect(jsonPath("$.hasPets").value(true))
                .andExpect(jsonPath("$.isSmoker").value(false))
                .andExpect(jsonPath("$.profession").value("Engenheira"))
                .andExpect(jsonPath("$.bio").value("Gosto de silêncio"))
                .andExpect(jsonPath("$.allergies").value("Gatos"))
                .andExpect(jsonPath("$.organizationLevel").value(5))
                .andExpect(jsonPath("$.visitorTolerance").value(3))
                .andExpect(jsonPath("$.hasFurniture").value(true));

        ArgumentCaptor<UserProfileDto> captor = ArgumentCaptor.forClass(UserProfileDto.class);
        verify(userService).updateProfile(eq("user-1"), captor.capture());
        UserProfileDto captured = captor.getValue();
        assertEquals(new BigDecimal("1500.50"), captured.getBudget());
        assertTrue(captured.getHasPets());
        assertFalse(captured.getIsSmoker());
        assertEquals("Engenheira", captured.getProfession());
        assertEquals("Gosto de silêncio", captured.getBio());
        assertEquals("Gatos", captured.getAllergies());
        assertEquals(5, captured.getOrganizationLevel());
        assertEquals(3, captured.getVisitorTolerance());
        assertTrue(captured.getHasFurniture());
    }

    @Test
    void updateProfileShouldSupportEnglishFieldNamesAndReturn400WhenInvalid() throws Exception {
        String enJson = """
                {
                  "budget": 1000.00,
                  "hasPets": false,
                  "isSmoker": true,
                  "organizationLevel": 6,
                  "visitorTolerance": 3
                }
                """;

        when(userService.updateProfile(eq("user-1"), any(UserProfileDto.class)))
                .thenThrow(new BusinessRuleException("organizationLevel must be between 1 and 5"));

        mockMvc.perform(put("/api/users/me/profile")
                        .principal(auth("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("organizationLevel must be between 1 and 5"));
    }
}
