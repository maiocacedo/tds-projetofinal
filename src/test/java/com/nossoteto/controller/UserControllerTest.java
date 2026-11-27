package com.nossoteto.controller;

import com.nossoteto.dto.PointHistoryItemDto;
import com.nossoteto.dto.PointsSummaryDto;
import com.nossoteto.dto.UserMeResponse;
import com.nossoteto.dto.UserPointsResponse;
import com.nossoteto.dto.UserProfileDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ConflictException;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.service.PointService;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private PointService pointService;

    private UsernamePasswordAuthenticationToken auth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    }

    @Test
    void getMeShouldReturnCurrentUser() throws Exception {
        UserProfileDto profileDto = new UserProfileDto();
        profileDto.setBudget(new BigDecimal("1400.00"));
        profileDto.setHasPets(true);
        profileDto.setIsSmoker(false);
        profileDto.setOrganizationLevel(4);
        profileDto.setVisitorTolerance(3);

        UserMeResponse response = new UserMeResponse(
                "user-1",
                "Alice",
                "alice@test.com",
                "house-1",
                true,
                "Solar",
                profileDto,
                true,
                List.of("ROLE_MANAGE_HOUSE", "ROLE_MANAGE_PROFILE"),
                new PointsSummaryDto(45, 20),
                3,
                false
        );

        when(userService.getCurrentUser("user-1")).thenReturn(response);

        mockMvc.perform(get("/api/users/me").principal(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@test.com"))
                .andExpect(jsonPath("$.houseId").value("house-1"))
                .andExpect(jsonPath("$.isHouseAdmin").value(true))
                .andExpect(jsonPath("$.houseName").value("Solar"))
                .andExpect(jsonPath("$.profileComplete").value(true))
                .andExpect(jsonPath("$.permissions[0]").value("ROLE_MANAGE_HOUSE"))
                .andExpect(jsonPath("$.permissions[1]").value("ROLE_MANAGE_PROFILE"))
                .andExpect(jsonPath("$.points.total").value(45))
                .andExpect(jsonPath("$.points.thisMonth").value(20))
                .andExpect(jsonPath("$.residenceMonths").value(3))
                .andExpect(jsonPath("$.pendingWelcome").value(false))
                .andExpect(jsonPath("$.profile.budget").value(1400.00))
                .andExpect(jsonPath("$.profile.hasPets").value(true))
                .andExpect(jsonPath("$.profile.organizationLevel").value(4));
    }

    @Test
    void getMeShouldSerializeNullResidenceMonthsAndIncompleteProfile() throws Exception {
        UserMeResponse response = new UserMeResponse(
                "user-2",
                "Bob",
                "bob@test.com",
                null,
                false,
                null,
                null,
                false,
                List.of("ROLE_MANAGE_PROFILE"),
                new PointsSummaryDto(0, 0),
                null,
                false
        );

        when(userService.getCurrentUser("user-2")).thenReturn(response);

        mockMvc.perform(get("/api/users/me").principal(auth("user-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isHouseAdmin").value(false))
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andExpect(jsonPath("$.points.total").value(0))
                .andExpect(jsonPath("$.points.thisMonth").value(0))
                .andExpect(jsonPath("$.residenceMonths").value(nullValue()));
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

    @Test
    void getMyPointsShouldReturnSummaryAndHistory() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 20, 15, 30, 0);
        UserPointsResponse response = new UserPointsResponse(
                35,
                25,
                List.of(
                        new PointHistoryItemDto("p-2", PointReason.CHORE_ON_TIME, 20, createdAt, "Lavar louça"),
                        new PointHistoryItemDto("p-1", PointReason.GROCERY_BOUGHT, 5, createdAt.minusDays(1), "Café")
                )
        );
        when(pointService.getUserPoints("user-1")).thenReturn(response);

        mockMvc.perform(get("/api/users/me/points").principal(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(35))
                .andExpect(jsonPath("$.thisMonth").value(25))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].id").value("p-2"))
                .andExpect(jsonPath("$.history[0].reason").value("CHORE_ON_TIME"))
                .andExpect(jsonPath("$.history[0].amount").value(20))
                .andExpect(jsonPath("$.history[0].createdAt").value("2026-11-20T15:30:00"))
                .andExpect(jsonPath("$.history[0].description").value("Lavar louça"));
    }

    @Test
    void conflictExceptionShouldReturn409WithErrorBody() throws Exception {
        when(userService.getCurrentUser("user-1"))
                .thenThrow(new ConflictException("resource conflict"));

        mockMvc.perform(get("/api/users/me").principal(auth("user-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("resource conflict"));
    }

    // ---------- B5: boas-vindas ----------

    @Test
    void getMeShouldSerializePendingWelcome() throws Exception {
        UserMeResponse response = new UserMeResponse(
                "user-3", "Carol", "carol@test.com", "house-1", false, "Solar", null, false,
                List.of("ROLE_MANAGE_PROFILE"), new PointsSummaryDto(0, 0), 0, true);

        when(userService.getCurrentUser("user-3")).thenReturn(response);

        mockMvc.perform(get("/api/users/me").principal(auth("user-3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingWelcome").value(true));
    }

    @Test
    void welcomeAckShouldReturn204AndDelegateToService() throws Exception {
        mockMvc.perform(post("/api/users/me/welcome-ack").principal(auth("user-1")))
                .andExpect(status().isNoContent());

        verify(userService).acknowledgeWelcome("user-1");
    }
}
