package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.model.enums.PointSourceType;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.service.PointService;
import com.nossoteto.support.TestTokens;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B5: vagas atômicas, pausa independente, minhas solicitações, desistência, card do candidato,
 * quem votou / meu voto, voto trocável e boas-vindas (pendingWelcome + welcome-ack).
 * Sem @Transactional na classe: o rollback do 409 precisa ser observado de verdade no H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MatchVacanciesAndVotingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointService pointService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------- helpers ----------

    private String registerUser(String name) throws Exception {
        String email = "b5-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"senhaSegura123\"}"))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail(email).orElseThrow();
        return user.getId();
    }

    private String createHouse(String adminId, String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/houses")
                        .with(TestTokens.jwtForUser(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String requestMatch(String candidateId, String houseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/matches/requests")
                        .with(TestTokens.jwtForUser(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\":\"" + houseId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private MvcResult vote(String voterId, String requestId, String vote) throws Exception {
        return mockMvc.perform(post("/api/matches/requests/" + requestId + "/vote")
                        .with(TestTokens.jwtForUser(voterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"" + vote + "\"}"))
                .andReturn();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value != null ? value : 0;
    }

    private int vacanciesOf(String houseId) {
        return count("SELECT vacancies FROM houses WHERE id = ?", houseId);
    }

    // ---------- migração V8 ----------

    @Test
    void flywayV8ShouldDefaultVacanciesToOneAndPendingWelcomeToFalseForExistingRows() {
        String dbName = "v8_defaults_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("7").load().migrate();

        JdbcTemplate isolated = new JdbcTemplate(dataSource);
        String houseId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        isolated.update("INSERT INTO houses (id, name, is_accepting_matches) VALUES (?, ?, ?)",
                houseId, "Casa Antiga", true);
        isolated.update("INSERT INTO users (id, name, email, password_hash) VALUES (?, ?, ?, ?)",
                userId, "Usuário Antigo", "antigo-v8@test.com", "hash");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        Map<String, Object> house = isolated.queryForMap("SELECT vacancies FROM houses WHERE id = ?", houseId);
        assertEquals(1, ((Number) house.get("VACANCIES")).intValue());
        Map<String, Object> user = isolated.queryForMap("SELECT pending_welcome FROM users WHERE id = ?", userId);
        assertEquals(Boolean.FALSE, user.get("PENDING_WELCOME"));
    }

    // ---------- 401 / 403 dos endpoints novos ----------

    @Test
    void newEndpointsShouldReturn401WithoutTokenAnd403WithoutPermissionOrScope() throws Exception {
        mockMvc.perform(get("/api/matches/requests/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/matches/requests/r1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/users/me/welcome-ack")).andExpect(status().isUnauthorized());

        // GET /mine: ROLE_MATCH + SCOPE_read
        mockMvc.perform(get("/api/matches/requests/mine")
                        .with(TestTokens.jwtWith("b5-user", "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/matches/requests/mine")
                        .with(TestTokens.jwtWith("b5-user", "ROLE_MATCH", "SCOPE_write")))
                .andExpect(status().isForbidden());

        // DELETE /{id}: ROLE_MATCH + SCOPE_write
        mockMvc.perform(delete("/api/matches/requests/r1")
                        .with(TestTokens.jwtWith("b5-user", "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/matches/requests/r1")
                        .with(TestTokens.jwtWith("b5-user", "ROLE_MATCH", "SCOPE_read")))
                .andExpect(status().isForbidden());

        // POST /me/welcome-ack: only SCOPE_write (no ROLE_MANAGE_PROFILE needed; 204 case below)
        mockMvc.perform(post("/api/users/me/welcome-ack")
                        .with(TestTokens.jwtWith("b5-user", "SCOPE_read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/me/welcome-ack")
                        .with(TestTokens.jwtWith("b5-user", "ROLE_MANAGE_PROFILE", "SCOPE_read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void welcomeAckShouldNeedOnlyScopeWrite() throws Exception {
        String userId = registerUser("Welcome Só Write");
        userRepository.findById(userId).ifPresent(u -> {
            u.setPendingWelcome(true);
            userRepository.save(u);
        });

        mockMvc.perform(post("/api/users/me/welcome-ack")
                        .with(TestTokens.jwtWith(userId, "SCOPE_write")))
                .andExpect(status().isNoContent());

        assertFalse(userRepository.findById(userId).orElseThrow().getPendingWelcome());
    }

    // ---------- vagas e pausa independentes ----------

    @Test
    void pausedHouseOrHouseWithoutVacanciesShouldRefuseRequestsAndBeHiddenFromSearch() throws Exception {
        String adminId = registerUser("Admin Vagas");
        String candidateId = registerUser("Candidato Vagas");

        // "vagas" alias, vacancies == 0 at creation
        String houseId = createHouse(adminId, "{\"name\":\"Casa Lotada\",\"vagas\":0}");
        mockMvc.perform(get("/api/houses/" + houseId).with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vacancies").value(0))
                .andExpect(jsonPath("$.isAcceptingMatches").value(true));

        mockMvc.perform(post("/api/matches/requests")
                        .with(TestTokens.jwtForUser(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\":\"" + houseId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("house is not accepting requests"));
        assertFalse(listedInSearch(candidateId, houseId));

        // Vagas reabertas, mas casa pausada -> continua recusando
        mockMvc.perform(put("/api/houses/" + houseId)
                        .with(TestTokens.jwtForUser(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vacancies\":2,\"isAcceptingMatches\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vacancies").value(2))
                .andExpect(jsonPath("$.isAcceptingMatches").value(false));

        mockMvc.perform(post("/api/matches/requests")
                        .with(TestTokens.jwtForUser(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\":\"" + houseId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("house is not accepting requests"));
        assertFalse(listedInSearch(candidateId, houseId));

        // Despausada com vagas -> aparece na busca
        mockMvc.perform(put("/api/houses/" + houseId)
                        .with(TestTokens.jwtForUser(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAcceptingMatches\":true}"))
                .andExpect(status().isOk());
        assertTrue(listedInSearch(candidateId, houseId));

        // Zerar vagas NÃO pausa a casa sozinho
        mockMvc.perform(put("/api/houses/" + houseId)
                        .with(TestTokens.jwtForUser(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vacancies\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vacancies").value(0))
                .andExpect(jsonPath("$.isAcceptingMatches").value(true));
        assertFalse(listedInSearch(candidateId, houseId));

        // Vagas negativas -> 400 no update e na criação
        mockMvc.perform(put("/api/houses/" + houseId)
                        .with(TestTokens.jwtForUser(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vacancies\":-1}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, vacanciesOf(houseId));

        mockMvc.perform(post("/api/houses")
                        .with(TestTokens.jwtForUser(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Casa Negativa\",\"vacancies\":-3}"))
                .andExpect(status().isBadRequest());

        // Sem informar vagas -> default 1
        String defaultHouseId = createHouse(candidateId, "{\"name\":\"Casa Default\"}");
        assertEquals(1, vacanciesOf(defaultHouseId));
    }

    private boolean listedInSearch(String viewerId, String houseId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/houses").with(TestTokens.jwtForUser(viewerId)))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (houseId.equals(node.get("id").asText())) {
                return true;
            }
        }
        return false;
    }

    // ---------- decremento atômico + 409 com rollback real ----------

    @Test
    void singleVacancyShouldAcceptFirstCandidateAndReturn409WithRealRollbackForSecond() throws Exception {
        String adminId = registerUser("Admin Uma Vaga");
        String firstId = registerUser("Primeiro Candidato");
        String secondId = registerUser("Segundo Candidato");

        String houseId = createHouse(adminId, "{\"name\":\"Casa Uma Vaga\",\"votingRule\":\"UNANIMITY\"}");
        assertEquals(1, vacanciesOf(houseId));

        String firstRequest = requestMatch(firstId, houseId);
        String secondRequest = requestMatch(secondId, houseId);

        // 1º aceito: vaga consumida, boas-vindas pendentes, histórico aberto
        MvcResult accepted = vote(adminId, firstRequest, "APPROVE");
        assertEquals(200, accepted.getResponse().getStatus());
        assertEquals("ACCEPTED",
                objectMapper.readTree(accepted.getResponse().getContentAsString()).get("status").asText());
        assertEquals(0, vacanciesOf(houseId));
        assertEquals(1, count(
                "SELECT COUNT(*) FROM residence_history WHERE user_id = ? AND house_id = ? AND left_at IS NULL",
                firstId, houseId));
        mockMvc.perform(get("/api/users/me").with(TestTokens.jwtForUser(firstId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseId").value(houseId))
                .andExpect(jsonPath("$.pendingWelcome").value(true));

        // Casa continua "aceitando", mas sem vagas: some da busca
        assertFalse(listedInSearch(secondId, houseId));

        // 2º pedido: agora há 2 moradores (UNANIMITY). O voto do admin fica PENDING e é persistido.
        MvcResult adminVote = vote(adminId, secondRequest, "APPROVE");
        assertEquals(200, adminVote.getResponse().getStatus());
        assertEquals("PENDING",
                objectMapper.readTree(adminVote.getResponse().getContentAsString()).get("status").asText());

        // O voto que fecharia a aprovação esbarra na falta de vaga -> 409 e rollback total
        mockMvc.perform(post("/api/matches/requests/" + secondRequest + "/vote")
                        .with(TestTokens.jwtForUser(firstId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("house has no available vacancies"));

        assertEquals(0, count("SELECT COUNT(*) FROM match_votes WHERE request_id = ? AND voter_id = ?",
                secondRequest, firstId), "the vote that triggered the 409 must be rolled back");
        assertEquals(1, count("SELECT COUNT(*) FROM match_votes WHERE request_id = ? AND voter_id = ?",
                secondRequest, adminId));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM match_requests WHERE id = ?", String.class, secondRequest));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT house_id FROM users WHERE id = ?", String.class, secondId));
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "SELECT pending_welcome FROM users WHERE id = ?", Boolean.class, secondId));
        assertEquals(0, count("SELECT COUNT(*) FROM residence_history WHERE user_id = ?", secondId));
        assertEquals(0, vacanciesOf(houseId));
    }

    // ---------- minhas solicitações + desistir ----------

    @Test
    void mineShouldListAllMyRequestsNewestFirstAndDeleteShouldWithdrawOnlyOwnPendingRequest() throws Exception {
        String adminA = registerUser("Admin A");
        String residentA = registerUser("Morador A");
        String adminB = registerUser("Admin B");
        String candidateId = registerUser("Candidata Mine");

        String houseA = createHouse(adminA, "{\"name\":\"Casa A Mine\",\"vacancies\":3,\"votingRule\":\"UNANIMITY\"}");
        String houseB = createHouse(adminB, "{\"name\":\"Casa B Mine\",\"vacancies\":1}");

        // Casa A passa a ter 2 moradores
        String residentRequest = requestMatch(residentA, houseA);
        assertEquals(200, vote(adminA, residentRequest, "APPROVE").getResponse().getStatus());

        String requestA = requestMatch(candidateId, houseA);
        String requestB = requestMatch(candidateId, houseB);
        jdbcTemplate.update("UPDATE match_requests SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 10, 0)), requestA);
        jdbcTemplate.update("UPDATE match_requests SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 1, 2, 10, 0)), requestB);

        // 1 de 2 moradores votou em A
        assertEquals(200, vote(adminA, requestA, "APPROVE").getResponse().getStatus());

        mockMvc.perform(get("/api/matches/requests/mine").with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(requestB))
                .andExpect(jsonPath("$[0].houseId").value(houseB))
                .andExpect(jsonPath("$[0].houseName").value("Casa B Mine"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].votesCast").value(0))
                .andExpect(jsonPath("$[0].votersTotal").value(1))
                .andExpect(jsonPath("$[0].moveDeadline").value(nullValue()))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].id").value(requestA))
                .andExpect(jsonPath("$[1].houseName").value("Casa A Mine"))
                .andExpect(jsonPath("$[1].votesCast").value(1))
                .andExpect(jsonPath("$[1].votersTotal").value(2));

        // 404 -> 403 -> 204 -> 400
        mockMvc.perform(delete("/api/matches/requests/" + UUID.randomUUID())
                        .with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/matches/requests/" + requestB)
                        .with(TestTokens.jwtForUser(adminB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the owner can perform this action"));
        mockMvc.perform(delete("/api/matches/requests/" + requestB)
                        .with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/matches/requests/" + requestB)
                        .with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("request is not pending"));

        assertEquals("WITHDRAWN", jdbcTemplate.queryForObject(
                "SELECT status FROM match_requests WHERE id = ?", String.class, requestB));
        assertEquals(1, vacanciesOf(houseB), "withdrawing must not touch vacancies");

        mockMvc.perform(get("/api/matches/requests/mine").with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestB))
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$[1].status").value("PENDING"));

        // A casa não vê mais o pedido retirado
        mockMvc.perform(get("/api/houses/" + houseB + "/matches").with(TestTokens.jwtForUser(adminB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- card do candidato, quem votou, meu voto, voto trocável ----------

    @Test
    void houseMatchesShouldExposeCandidateCardVotersWithoutDirectionMyVoteAndAllowVoteSwap() throws Exception {
        String adminId = registerUser("Admin Card");
        String residentId = registerUser("Morador Card");
        String candidateId = registerUser("Candidato Card");
        String incompleteId = registerUser("Candidato Incompleto");

        String houseId = createHouse(adminId, """
                {
                  "name": "Casa Card",
                  "vacancies": 4,
                  "rentAmount": 1000.00,
                  "allowsPets": true,
                  "allowsSmokers": false,
                  "expectedOrganizationLevel": 4,
                  "visitorTolerance": 3,
                  "votingRule": "UNANIMITY"
                }
                """);
        String residentRequest = requestMatch(residentId, houseId);
        assertEquals(200, vote(adminId, residentRequest, "APPROVE").getResponse().getStatus());

        mockMvc.perform(put("/api/users/me/profile")
                        .with(TestTokens.jwtForUser(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "budget": 1500.00,
                                  "hasPets": false,
                                  "isSmoker": false,
                                  "organizationLevel": 4,
                                  "visitorTolerance": 3
                                }
                                """))
                .andExpect(status().isOk());
        pointService.creditIfAbsent(candidateId, null, PointReason.GROCERY_BOUGHT, 5,
                PointSourceType.GROCERY, "g-" + UUID.randomUUID(), "", "Pão");

        String candidateRequest = requestMatch(candidateId, houseId);
        jdbcTemplate.update("UPDATE match_requests SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 9, 0)), candidateRequest);
        String incompleteRequest = requestMatch(incompleteId, houseId);
        jdbcTemplate.update("UPDATE match_requests SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 10, 0)), incompleteRequest);

        // Admin aprova (1 de 2): fica PENDING
        MvcResult firstVote = vote(adminId, candidateRequest, "APPROVE");
        assertEquals("PENDING",
                objectMapper.readTree(firstVote.getResponse().getContentAsString()).get("status").asText());

        mockMvc.perform(get("/api/houses/" + houseId + "/matches").with(TestTokens.jwtForUser(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(candidateRequest))
                // campos existentes preservados
                .andExpect(jsonPath("$[0].userName").value("Candidato Card"))
                .andExpect(jsonPath("$[0].userEmail").exists())
                .andExpect(jsonPath("$[0].userProfile").exists())
                .andExpect(jsonPath("$[0].compatibilityScore").value(100))
                // card do candidato
                .andExpect(jsonPath("$[0].candidate.id").value(candidateId))
                .andExpect(jsonPath("$[0].candidate.name").value("Candidato Card"))
                .andExpect(jsonPath("$[0].candidate.compatibilityScore").value(100))
                .andExpect(jsonPath("$[0].candidate.points").value(5))
                .andExpect(jsonPath("$[0].candidate.residenceMonths").value(nullValue()))
                // quem votou, sem direção
                .andExpect(jsonPath("$[0].voters.length()").value(1))
                .andExpect(jsonPath("$[0].voters[0].id").value(adminId))
                .andExpect(jsonPath("$[0].voters[0].name").value("Admin Card"))
                .andExpect(jsonPath("$[0].voters[0].vote").doesNotExist())
                .andExpect(jsonPath("$[0].myVote").value("APPROVE"))
                // ficha incompleta -> compatibilidade null
                .andExpect(jsonPath("$[1].id").value(incompleteRequest))
                .andExpect(jsonPath("$[1].candidate.compatibilityScore").value(nullValue()))
                .andExpect(jsonPath("$[1].candidate.points").value(0))
                .andExpect(jsonPath("$[1].voters.length()").value(0))
                .andExpect(jsonPath("$[1].myVote").value(nullValue()));

        // Quem ainda não votou vê myVote null, mas sabe quem já votou
        mockMvc.perform(get("/api/houses/" + houseId + "/matches").with(TestTokens.jwtForUser(residentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].myVote").value(nullValue()))
                .andExpect(jsonPath("$[0].voters[0].id").value(adminId));

        // Voto trocável enquanto PENDING: APPROVE -> REJECT reavalia (UNANIMITY rejeita)
        MvcResult swapped = vote(adminId, candidateRequest, "REJECT");
        assertEquals(200, swapped.getResponse().getStatus());
        assertEquals("REJECTED",
                objectMapper.readTree(swapped.getResponse().getContentAsString()).get("status").asText());
        assertEquals(1, count("SELECT COUNT(*) FROM match_votes WHERE request_id = ? AND voter_id = ?",
                candidateRequest, adminId), "swapping a vote must upsert, not duplicate");
        assertEquals("REJECT", jdbcTemplate.queryForObject(
                "SELECT vote FROM match_votes WHERE request_id = ? AND voter_id = ?",
                String.class, candidateRequest, adminId));
    }

    @Test
    void voteSwapFromRejectToApproveShouldReevaluateUnderMajority() throws Exception {
        String adminId = registerUser("Admin Maioria");
        String residentOne = registerUser("Morador Um");
        String residentTwo = registerUser("Morador Dois");
        String candidateId = registerUser("Candidato Maioria");

        String houseId = createHouse(adminId, "{\"name\":\"Casa Maioria\",\"vacancies\":3,\"votingRule\":\"MAJORITY\"}");
        // MAJORITY: com 1 morador, 1 aprovação basta; com 2, precisa de 2.
        assertEquals(200, vote(adminId, requestMatch(residentOne, houseId), "APPROVE").getResponse().getStatus());
        String secondResident = requestMatch(residentTwo, houseId);
        vote(adminId, secondResident, "APPROVE");
        assertEquals("ACCEPTED", objectMapper.readTree(
                vote(residentOne, secondResident, "APPROVE").getResponse().getContentAsString())
                .get("status").asText());

        // 3 moradores. 1 REJECT ainda não rejeita (precisa de 2).
        String candidateRequest = requestMatch(candidateId, houseId);
        assertEquals("PENDING", objectMapper.readTree(
                vote(adminId, candidateRequest, "REJECT").getResponse().getContentAsString())
                .get("status").asText());
        // Troca REJECT -> APPROVE: 1 aprovação de 3 ainda é PENDING
        assertEquals("PENDING", objectMapper.readTree(
                vote(adminId, candidateRequest, "APPROVE").getResponse().getContentAsString())
                .get("status").asText());
        // 2 aprovações de 3 -> aceito; sem a troca seria 1x1 e seguiria pendente
        assertEquals("ACCEPTED", objectMapper.readTree(
                vote(residentOne, candidateRequest, "APPROVE").getResponse().getContentAsString())
                .get("status").asText());
        assertEquals(0, vacanciesOf(houseId));
    }

    // ---------- boas-vindas ----------

    @Test
    void welcomeAckAndCancelOthersShouldClearPendingWelcome() throws Exception {
        String adminA = registerUser("Admin Welcome A");
        String adminB = registerUser("Admin Welcome B");
        String adminC = registerUser("Admin Welcome C");
        String acker = registerUser("Recebe Boas-vindas");
        String canceller = registerUser("Cancela Outros");

        String houseA = createHouse(adminA, "{\"name\":\"Casa Welcome A\"}");
        String houseB = createHouse(adminB, "{\"name\":\"Casa Welcome B\"}");
        String houseC = createHouse(adminC, "{\"name\":\"Casa Welcome C\"}");

        mockMvc.perform(get("/api/users/me").with(TestTokens.jwtForUser(acker)))
                .andExpect(jsonPath("$.pendingWelcome").value(false));

        assertEquals(200, vote(adminA, requestMatch(acker, houseA), "APPROVE").getResponse().getStatus());
        mockMvc.perform(get("/api/users/me").with(TestTokens.jwtForUser(acker)))
                .andExpect(jsonPath("$.pendingWelcome").value(true));

        mockMvc.perform(post("/api/users/me/welcome-ack").with(TestTokens.jwtForUser(acker)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/me").with(TestTokens.jwtForUser(acker)))
                .andExpect(jsonPath("$.pendingWelcome").value(false));

        // cancel-others também limpa as boas-vindas
        String toAccept = requestMatch(canceller, houseB);
        requestMatch(canceller, houseC);
        MvcResult accepted = vote(adminB, toAccept, "APPROVE");
        assertTrue(objectMapper.readTree(accepted.getResponse().getContentAsString())
                .get("hasOtherPendingRequests").asBoolean());
        mockMvc.perform(get("/api/users/me").with(TestTokens.jwtForUser(canceller)))
                .andExpect(jsonPath("$.pendingWelcome").value(true));

        mockMvc.perform(post("/api/matches/requests/cancel-others").with(TestTokens.jwtForUser(canceller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledCount").value(1));
        mockMvc.perform(get("/api/users/me").with(TestTokens.jwtForUser(canceller)))
                .andExpect(jsonPath("$.pendingWelcome").value(false));
    }
}
