package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NossoTetoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void goldenPaths_EndToEndIntegrationFlow() throws Exception {
        // 1. Register User A (admin), User B (candidate), User C (intruder / second house owner)
        String userAId = registerUser("Alice Admin", "alice.e2e@nossoteto.com", "senha123");
        String userBId = registerUser("Bruno Candidato", "bruno.e2e@nossoteto.com", "senha123");
        String userCId = registerUser("Carlos Intruso", "carlos.e2e@nossoteto.com", "senha123");

        // 2. Login all three and obtain JWT tokens
        String tokenA = loginUser("alice.e2e@nossoteto.com", "senha123");
        String tokenB = loginUser("bruno.e2e@nossoteto.com", "senha123");
        String tokenC = loginUser("carlos.e2e@nossoteto.com", "senha123");

        // 3. User A creates House A; User C creates House C; User B updates profile
        String createHouseABody = """
                {
                  "nome": "República Sol Nascente",
                  "endereco": "Rua A, 100",
                  "descricao": "Casa organizada e tranquila",
                  "regrasBasicas": "Sem barulho após 22h",
                  "valorAluguel": 1200.00,
                  "aceitaPets": true,
                  "aceitaFumantes": false,
                  "nivelOrganizacaoEsperado": 4,
                  "toleranciaVisitas": 3,
                  "regraVotacao": "UNANIMITY"
                }
                """;
        MvcResult houseAResult = mockMvc.perform(post("/api/houses")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createHouseABody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("República Sol Nascente"))
                .andReturn();
        String houseAId = objectMapper.readTree(houseAResult.getResponse().getContentAsString()).get("id").asText();

        String createHouseCBody = """
                {
                  "nome": "República Lua Cheia",
                  "endereco": "Rua C, 200",
                  "descricao": "Outra casa com vaga",
                  "valorAluguel": 1100.00,
                  "aceitaPets": true,
                  "aceitaFumantes": false,
                  "nivelOrganizacaoEsperado": 4,
                  "toleranciaVisitas": 3
                }
                """;
        MvcResult houseCResult = mockMvc.perform(post("/api/houses")
                        .header("Authorization", "Bearer " + tokenC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createHouseCBody))
                .andExpect(status().isCreated())
                .andReturn();
        String houseCId = objectMapper.readTree(houseCResult.getResponse().getContentAsString()).get("id").asText();

        String updateProfileBBody = """
                {
                  "orcamentoMensal": 1500.00,
                  "possuiPets": true,
                  "fumante": false,
                  "profissao": "Engenheiro de Software",
                  "biografia": "Gosto de cozinhar",
                  "alergias": "Nenhuma",
                  "nivelOrganizacao": 4,
                  "toleranciaVisitas": 3,
                  "mobiliaPropria": true
                }
                """;
        mockMvc.perform(put("/api/users/me/profile")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProfileBBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profession").value("Engenheiro de Software"));

        // 4. User B lists houses, verifies compatibility score (100 with House A), and requests match to House A and House C
        MvcResult listHousesResult = mockMvc.perform(get("/api/houses")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode housesArray = objectMapper.readTree(listHousesResult.getResponse().getContentAsString());
        boolean foundHouseAWithScore100 = false;
        for (JsonNode node : housesArray) {
            if (houseAId.equals(node.get("id").asText())) {
                assertEquals(100, node.get("compatibilityScore").asInt());
                foundHouseAWithScore100 = true;
            }
        }
        assertTrue(foundHouseAWithScore100, "House A should be listed with compatibilityScore 100");

        MvcResult matchAResult = mockMvc.perform(post("/api/matches/requests")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\": \"" + houseAId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String matchRequestAId = objectMapper.readTree(matchAResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/matches/requests")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\": \"" + houseCId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // User C already lives in House C, so they cannot ask to join House A without leaving first
        mockMvc.perform(post("/api/matches/requests")
                        .header("Authorization", "Bearer " + tokenC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\": \"" + houseAId + "\"}"))
                .andExpect(status().isBadRequest());

        // 5. User A votes APROVAR -> User B is accepted into House A (hasOtherPendingRequests == true), and User B cancels others
        mockMvc.perform(post("/api/matches/requests/" + matchRequestAId + "/vote")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voto\": \"APROVAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.hasOtherPendingRequests").value(true));

        mockMvc.perform(post("/api/matches/requests/cancel-others")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledCount").value(1));

        // 6. User A creates an Expense with anexoBase64 -> splits 50/50 between User A and User B
        byte[] sampleReceipt = "COMPROVANTE-ENERGIA-300".getBytes(StandardCharsets.UTF_8);
        String base64Receipt = Base64.getEncoder().encodeToString(sampleReceipt);

        String createExpenseBody = """
                {
                  "titulo": "Conta de Energia",
                  "valorTotal": 300.00,
                  "vencimento": "2026-11-10",
                  "anexoBase64": "%s"
                }
                """.formatted(base64Receipt);

        MvcResult expenseResult = mockMvc.perform(post("/api/houses/" + houseAId + "/expenses")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createExpenseBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splitType").value("EQUAL"))
                .andExpect(jsonPath("$.hasAttachment").value(true))
                .andExpect(jsonPath("$.splits.length()").value(2))
                .andReturn();
        String expenseId = objectMapper.readTree(expenseResult.getResponse().getContentAsString()).get("id").asText();

        // 7. User C (intruder) attempts to download the attachment -> 403 Forbidden; User B downloads -> 200 OK
        mockMvc.perform(get("/api/houses/" + houseAId + "/expenses/" + expenseId + "/attachment")
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isForbidden());

        MvcResult attachmentResult = mockMvc.perform(get("/api/houses/" + houseAId + "/expenses/" + expenseId + "/attachment")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(sampleReceipt, attachmentResult.getResponse().getContentAsByteArray());

        // 8. User A proposes a custom split (60% User A / 40% User B), User A and User B vote APROVAR -> CUSTOM_APPROVED, User B pays
        String proposeSplitBody = """
                {
                  "tipoDivisao": "PORCENTAGEM",
                  "divisoes": [
                    { "userId": "%s", "valorOuPorcentagem": 60 },
                    { "userId": "%s", "valorOuPorcentagem": 40 }
                  ]
                }
                """.formatted(userAId, userBId);

        mockMvc.perform(post("/api/houses/" + houseAId + "/expenses/" + expenseId + "/propose-split")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(proposeSplitBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitType").value("CUSTOM_PENDING"));

        mockMvc.perform(post("/api/houses/" + houseAId + "/expenses/" + expenseId + "/vote-split")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voto\": \"APROVAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitType").value("CUSTOM_PENDING"));

        mockMvc.perform(post("/api/houses/" + houseAId + "/expenses/" + expenseId + "/vote-split")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voto\": \"APROVAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitType").value("CUSTOM_APPROVED"));

        mockMvc.perform(put("/api/houses/" + houseAId + "/expenses/" + expenseId + "/pay")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());

        // 9. User A creates a ROTATIONAL chore. Only the admin (User A) may close the weekly cycle:
        //    the unfinished chore becomes DELAYED and stays with User A; once completed, the next cycle hands it to User B
        String createChoreBody = """
                {
                  "descricao": "Limpar Cozinha",
                  "tipo": "ROTATIVA",
                  "responsavelId": "%s"
                }
                """.formatted(userAId);

        MvcResult choreResult = mockMvc.perform(post("/api/houses/" + houseAId + "/chores")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createChoreBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currentAssigneeId").value(userAId))
                .andReturn();
        String choreId = objectMapper.readTree(choreResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/houses/" + houseAId + "/chores/rotate")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/houses/" + houseAId + "/chores/rotate")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DELAYED"))
                .andExpect(jsonPath("$[0].currentAssigneeId").value(userAId));

        mockMvc.perform(put("/api/houses/" + houseAId + "/chores/" + choreId + "/complete")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post("/api/houses/" + houseAId + "/chores/rotate")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].currentAssigneeId").value(userBId));

        // 10. User B adds a grocery item, assigns it, and User A checks GET /api/houses/{houseId}/dashboard
        MvcResult groceryResult = mockMvc.perform(post("/api/houses/" + houseAId + "/groceries")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeItem\": \"Sabão em pó\", \"quantidade\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemName").value("Sabão em pó"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andReturn();
        String itemId = objectMapper.readTree(groceryResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/houses/" + houseAId + "/groceries/" + itemId + "/assign")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(userBId));

        mockMvc.perform(get("/api/houses/" + houseAId + "/dashboard")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseId").value(houseAId))
                .andExpect(jsonPath("$.houseName").value("República Sol Nascente"))
                .andExpect(jsonPath("$.totalPendingAmountForUser").value(180.00))
                .andExpect(jsonPath("$.pendingSplitsForUser.length()").value(1))
                .andExpect(jsonPath("$.houseChores.length()").value(1))
                .andExpect(jsonPath("$.pendingGroceries.length()").value(1))
                .andExpect(jsonPath("$.pendingMatchRequestsCount").value(0));

        // 11. User B leaves House A: B's chore and grocery assignment are handed back, and B loses access to the house
        mockMvc.perform(post("/api/houses/" + houseAId + "/leave")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/houses/" + houseAId + "/chores")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentAssigneeId").value(userAId));

        mockMvc.perform(get("/api/houses/" + houseAId + "/groceries")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assigneeId").doesNotExist());

        mockMvc.perform(get("/api/houses/" + houseAId + "/dashboard")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    private String registerUser(String name, String email, String password) throws Exception {
        String payload = """
                {
                  "nome": "%s",
                  "email": "%s",
                  "senha": "%s"
                }
                """.formatted(name, email, password);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String loginUser(String email, String password) throws Exception {
        String payload = """
                {
                  "email": "%s",
                  "senha": "%s"
                }
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
