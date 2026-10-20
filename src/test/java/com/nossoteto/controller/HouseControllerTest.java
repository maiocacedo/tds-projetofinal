package com.nossoteto.controller;

import com.nossoteto.dto.CreateHouseRequest;
import com.nossoteto.dto.HouseMemberSummaryDto;
import com.nossoteto.dto.HouseResponse;
import com.nossoteto.model.enums.VotingRule;
import com.nossoteto.security.JwtAuthenticationFilter;
import com.nossoteto.security.JwtTokenProvider;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

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
}
