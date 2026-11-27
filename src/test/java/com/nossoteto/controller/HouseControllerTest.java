package com.nossoteto.controller;

import com.nossoteto.dto.CreateHouseRequest;
import com.nossoteto.dto.CreateRemovalRequestDto;
import com.nossoteto.dto.HouseMemberSummaryDto;
import com.nossoteto.dto.HouseResponse;
import com.nossoteto.dto.RemovalRequestResponse;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.dto.VoterSummaryDto;
import com.nossoteto.model.enums.RemovalStatus;
import com.nossoteto.model.enums.VotingRule;
import com.nossoteto.service.HouseMembershipService;
import com.nossoteto.service.HouseRemovalService;
import com.nossoteto.service.HouseService;
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

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HouseController.class)
@AutoConfigureMockMvc(addFilters = false)
class HouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HouseService houseService;

    @MockBean
    private HouseMembershipService houseMembershipService;

    @MockBean
    private HouseRemovalService houseRemovalService;

    private UsernamePasswordAuthenticationToken auth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    }

    @Test
    void createHouseShouldSupportPortugueseFieldAliasesAndReturn201() throws Exception {
        String ptJson = """
                {
                  "nome": "República Solar",
                  "endereco": "Rua das Flores, 123",
                  "descricao": "Casa ensolarada",
                  "regrasBasicas": "Lavar a louça",
                  "valorAluguel": 1350.00,
                  "aceitaPets": true,
                  "aceitaFumantes": false,
                  "nivelOrganizacaoEsperado": 4,
                  "toleranciaVisitas": 3,
                  "mobiliaExistente": true,
                  "regraVotacao": "MAJORITY"
                }
                """;

        HouseResponse response = new HouseResponse();
        response.setId("house-1");
        response.setName("República Solar");
        response.setVotingRule(VotingRule.MAJORITY);

        when(houseService.createHouse(eq("user-1"), any(CreateHouseRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/houses")
                        .principal(auth("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ptJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("house-1"))
                .andExpect(jsonPath("$.name").value("República Solar"))
                .andExpect(jsonPath("$.votingRule").value("MAJORITY"));

        ArgumentCaptor<CreateHouseRequest> captor = ArgumentCaptor.forClass(CreateHouseRequest.class);
        verify(houseService).createHouse(eq("user-1"), captor.capture());
        CreateHouseRequest captured = captor.getValue();
        assertEquals("República Solar", captured.getName());
        assertEquals("Rua das Flores, 123", captured.getAddress());
        assertEquals("Casa ensolarada", captured.getDescription());
        assertEquals("Lavar a louça", captured.getBasicRules());
        assertEquals(new BigDecimal("1350.00"), captured.getRentAmount());
        assertTrue(captured.getAllowsPets());
        assertFalse(captured.getAllowsSmokers());
        assertEquals(4, captured.getExpectedOrganizationLevel());
        assertEquals(3, captured.getVisitorTolerance());
        assertTrue(captured.getHasFurniture());
        assertEquals(VotingRule.MAJORITY, captured.getVotingRule());
    }

    @Test
    void listHousesShouldSupportPortugueseAndEnglishQueryParams() throws Exception {
        HouseResponse h1 = new HouseResponse();
        h1.setId("house-1");
        h1.setName("República Solar");
        h1.setCompatibilityScore(90);

        when(houseService.listOpenHouses(
                eq("user-1"),
                eq(new BigDecimal("1500.00")),
                eq(true),
                eq(false)
        )).thenReturn(List.of(h1));

        mockMvc.perform(get("/api/houses")
                        .principal(auth("user-1"))
                        .param("orcamentoMaximo", "1500.00")
                        .param("aceitaPets", "true")
                        .param("aceitaFumantes", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("house-1"))
                .andExpect(jsonPath("$[0].compatibilityScore").value(90));
    }

    @Test
    void getHouseByIdShouldReturnHouseDetails() throws Exception {
        HouseResponse response = new HouseResponse();
        response.setId("house-1");
        response.setName("República Solar");
        response.setCompatibilityScore(85);
        response.setMembers(List.of(new HouseMemberSummaryDto("user-2", "Bob", "bob@test.com", true)));

        when(houseService.getHouseDetails("user-1", "house-1")).thenReturn(response);

        mockMvc.perform(get("/api/houses/house-1")
                        .principal(auth("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("house-1"))
                .andExpect(jsonPath("$.compatibilityScore").value(85))
                .andExpect(jsonPath("$.members[0].name").value("Bob"));
    }

    @Test
    void hiddenMemberEmailShouldBeOmittedFromJson() throws Exception {
        HouseResponse response = new HouseResponse();
        response.setId("house-1");
        response.setMembers(List.of(new HouseMemberSummaryDto("user-2", "Bob", null, true)));

        when(houseService.getHouseDetails("visitor", "house-1")).thenReturn(response);

        mockMvc.perform(get("/api/houses/house-1").principal(auth("visitor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].name").value("Bob"))
                .andExpect(jsonPath("$.members[0].email").doesNotExist());
    }

    @Test
    void updateHouseShouldAcceptPortugueseAliasesAndDelegateToService() throws Exception {
        HouseResponse response = new HouseResponse();
        response.setId("house-1");
        response.setIsAcceptingMatches(false);

        when(houseService.updateHouse(eq("admin-1"), eq("house-1"), any(CreateHouseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/houses/house-1")
                        .principal(auth("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceitandoMatches\": false, \"regraVotacao\": \"MAJORITY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAcceptingMatches").value(false));

        ArgumentCaptor<CreateHouseRequest> captor = ArgumentCaptor.forClass(CreateHouseRequest.class);
        verify(houseService).updateHouse(eq("admin-1"), eq("house-1"), captor.capture());
        assertFalse(captor.getValue().getIsAcceptingMatches());
        assertEquals(VotingRule.MAJORITY, captor.getValue().getVotingRule());
        assertNull(captor.getValue().getName());
    }

    @Test
    void updateMemberAdminStatusShouldAcceptIsAdminAndReturnIsHouseAdmin() throws Exception {
        HouseMemberSummaryDto dto = new HouseMemberSummaryDto("user-2", "Bob", "bob@test.com", true);
        when(houseMembershipService.updateMemberAdminStatus("admin-1", "house-1", "user-2", true))
                .thenReturn(dto);

        mockMvc.perform(put("/api/houses/house-1/members/user-2/admin")
                        .principal(auth("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAdmin\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-2"))
                .andExpect(jsonPath("$.isHouseAdmin").value(true));

        verify(houseMembershipService).updateMemberAdminStatus("admin-1", "house-1", "user-2", true);
    }

    @Test
    void leaveHouseWithoutBodyShouldReturn204() throws Exception {
        mockMvc.perform(post("/api/houses/house-1/leave").principal(auth("user-1")))
                .andExpect(status().isNoContent());

        verify(houseMembershipService).leaveHouse("user-1", "house-1", null);
    }

    @Test
    void leaveHouseWithNewAdminIdShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/api/houses/house-1/leave")
                        .principal(auth("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newAdminId\": \"user-2\"}"))
                .andExpect(status().isNoContent());

        verify(houseMembershipService).leaveHouse("user-1", "house-1", "user-2");
    }

    @Test
    void createRemovalShouldReturn201() throws Exception {
        RemovalRequestResponse resp = new RemovalRequestResponse(
                "rem-1", "house-1", "user-2", "Bob", "admin-1", "Alice",
                "Motivo", RemovalStatus.OPEN, LocalDateTime.of(2026, 11, 27, 12, 0), null,
                List.of(), null, 0, 2);
        when(houseRemovalService.createRemovalRequest(eq("admin-1"), eq("house-1"), any(CreateRemovalRequestDto.class)))
                .thenReturn(resp);

        mockMvc.perform(post("/api/houses/house-1/removals")
                        .principal(auth("admin-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\": \"user-2\", \"reason\": \"Motivo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("rem-1"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.votersTotal").value(2));
    }

    @Test
    void listRemovalsShouldReturn200WithVotersWithoutDirection() throws Exception {
        RemovalRequestResponse resp = new RemovalRequestResponse(
                "rem-1", "house-1", "user-2", "Bob", "admin-1", "Alice",
                null, RemovalStatus.OPEN, LocalDateTime.of(2026, 11, 27, 12, 0), null,
                List.of(new VoterSummaryDto("admin-1", "Alice")), "APPROVE", 1, 2);
        when(houseRemovalService.listOpenRemovals("admin-1", "house-1")).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/houses/house-1/removals").principal(auth("admin-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("rem-1"))
                .andExpect(jsonPath("$[0].voters[0].id").value("admin-1"))
                .andExpect(jsonPath("$[0].voters[0].vote").doesNotExist())
                .andExpect(jsonPath("$[0].myVote").value("APPROVE"));
    }

    @Test
    void voteOnRemovalShouldReturn200() throws Exception {
        RemovalRequestResponse resp = new RemovalRequestResponse(
                "rem-1", "house-1", "user-2", "Bob", "admin-1", "Alice",
                null, RemovalStatus.OPEN, LocalDateTime.of(2026, 11, 27, 12, 0), null,
                List.of(new VoterSummaryDto("user-3", "Carla")), "APPROVE", 1, 2);
        when(houseRemovalService.voteOnRemoval(eq("user-3"), eq("house-1"), eq("rem-1"), any(VoteRequestDto.class)))
                .thenReturn(resp);

        mockMvc.perform(post("/api/houses/house-1/removals/rem-1/vote")
                        .principal(auth("user-3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\": \"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.myVote").value("APPROVE"));
    }

    @Test
    void cancelRemovalShouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/houses/house-1/removals/rem-1").principal(auth("admin-1")))
                .andExpect(status().isNoContent());

        verify(houseRemovalService).cancelRemovalRequest("admin-1", "house-1", "rem-1");
    }

    @Test
    void directDeleteMemberEndpointShouldNoLongerExist() throws Exception {
        mockMvc.perform(delete("/api/houses/house-1/members/user-2").principal(auth("admin-1")))
                .andExpect(status().is(anyOf(is(404), is(405))));
    }
}
