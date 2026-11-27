package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.User;
import com.nossoteto.repository.MatchRequestRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.service.MatchService;
import com.nossoteto.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B6: mudança de casa. Um morador pede vaga em outra casa; a aprovação reserva a vaga por 72 h
 * (AWAITING_MOVE). Confirmar sai da casa antiga e entra na nova numa transação só; recusar,
 * retirar e expirar devolvem a vaga exatamente uma vez.
 * Sem @Transactional na classe: o rollback do confirm-move precisa ser observado de verdade no H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MoveFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchRequestRepository matchRequestRepository;

    @Autowired
    private MatchService matchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------- helpers ----------

    private String registerUser(String name) throws Exception {
        String email = "b6-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"senhaSegura123\"}"))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail(email).orElseThrow();
        return user.getId();
    }

    private String createHouse(String adminId, String name, int vacancies) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/houses")
                        .with(TestTokens.jwtForUser(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"vacancies\":" + vacancies
                                + ",\"votingRule\":\"UNANIMITY\"}"))
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
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void approve(String voterId, String requestId) throws Exception {
        mockMvc.perform(post("/api/matches/requests/" + requestId + "/vote")
                        .with(TestTokens.jwtForUser(voterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_MOVE"));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value != null ? value : 0;
    }

    private int vacanciesOf(String houseId) {
        return count("SELECT vacancies FROM houses WHERE id = ?", houseId);
    }

    private String statusOf(String requestId) {
        return jdbcTemplate.queryForObject("SELECT status FROM match_requests WHERE id = ?", String.class, requestId);
    }

    private String houseOf(String userId) {
        return jdbcTemplate.queryForObject("SELECT house_id FROM users WHERE id = ?", String.class, userId);
    }

    private Boolean isAdmin(String userId) {
        return jdbcTemplate.queryForObject("SELECT is_house_admin FROM users WHERE id = ?", Boolean.class, userId);
    }

    private void putIntoHouse(String userId, String houseId) {
        jdbcTemplate.update("UPDATE users SET house_id = ?, is_house_admin = FALSE WHERE id = ?", houseId, userId);
    }

    private void expireDeadline(String requestId) {
        // Far in the past: no timezone offset between JDBC and Hibernate can put it back in the future.
        jdbcTemplate.update("UPDATE match_requests SET move_deadline = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2000, 1, 1, 0, 0)), requestId);
    }

    /** Old house: mover (only admin) + one non-admin resident. New house: its own admin, 2 vacancies. */
    private record Scenario(String moverId, String heirId, String oldHouseId,
                            String targetAdminId, String targetHouseId) {}

    private Scenario scenario(String label) throws Exception {
        String moverId = registerUser("Mover " + label);
        String heirId = registerUser("Herdeiro " + label);
        String targetAdminId = registerUser("Admin Destino " + label);
        String oldHouseId = createHouse(moverId, "Casa Antiga " + label, 2);
        putIntoHouse(heirId, oldHouseId);
        String targetHouseId = createHouse(targetAdminId, "Casa Nova " + label, 2);
        return new Scenario(moverId, heirId, oldHouseId, targetAdminId, targetHouseId);
    }

    // ---------- pedido e aprovação ----------

    @Test
    void residentCanRequestAnotherHouseAndApprovalReservesTheVacancyFor72Hours() throws Exception {
        Scenario s = scenario("Reserva");

        // Their own house is still refused.
        mockMvc.perform(post("/api/matches/requests")
                        .with(TestTokens.jwtForUser(s.moverId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\":\"" + s.oldHouseId() + "\"}"))
                .andExpect(status().isBadRequest());

        String requestId = requestMatch(s.moverId(), s.targetHouseId());
        approve(s.targetAdminId(), requestId);

        assertEquals("AWAITING_MOVE", statusOf(requestId));
        assertEquals(1, vacanciesOf(s.targetHouseId()));
        // Nothing moves until the resident confirms.
        assertEquals(s.oldHouseId(), houseOf(s.moverId()));
        assertTrue(isAdmin(s.moverId()));
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "SELECT pending_welcome FROM users WHERE id = ?", Boolean.class, s.moverId()));

        MatchRequest stored = matchRequestRepository.findById(requestId).orElseThrow();
        LocalDateTime expected = LocalDateTime.now(ZoneOffset.UTC).plusHours(72);
        assertNotNull(stored.getMoveDeadline());
        assertTrue(Duration.between(stored.getMoveDeadline(), expected).abs().toMinutes() <= 5,
                "moveDeadline should be ~72 h from now (UTC): " + stored.getMoveDeadline());

        // While AWAITING_MOVE the same request cannot be opened again.
        mockMvc.perform(post("/api/matches/requests")
                        .with(TestTokens.jwtForUser(s.moverId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\":\"" + s.targetHouseId() + "\"}"))
                .andExpect(status().isBadRequest());

        MvcResult mine = mockMvc.perform(get("/api/matches/requests/mine")
                        .with(TestTokens.jwtForUser(s.moverId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].status").value("AWAITING_MOVE"))
                .andReturn();
        JsonNode mineDeadline = objectMapper.readTree(mine.getResponse().getContentAsString()).get(0).get("moveDeadline");
        assertFalse(mineDeadline.isNull());
        assertTrue(Duration.between(LocalDateTime.parse(mineDeadline.asText()), expected).abs().toMinutes() <= 5);

        // The target admin sees the reservation (so they can retract it), deadline included.
        MvcResult houseList = mockMvc.perform(get("/api/houses/" + s.targetHouseId() + "/matches")
                        .with(TestTokens.jwtForUser(s.targetAdminId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].status").value("AWAITING_MOVE"))
                .andReturn();
        JsonNode houseDeadline = objectMapper.readTree(houseList.getResponse().getContentAsString()).get(0).get("moveDeadline");
        assertFalse(houseDeadline.isNull());
    }

    // ---------- confirmar mudança ----------

    @Test
    void confirmMoveWithoutHeirShouldRollBackEverythingInTheDatabase() throws Exception {
        Scenario s = scenario("Rollback");
        String requestId = requestMatch(s.moverId(), s.targetHouseId());
        approve(s.targetAdminId(), requestId);
        int historyBefore = count("SELECT COUNT(*) FROM residence_history WHERE user_id = ?", s.moverId());

        // An unrelated overdue reservation: confirm-move expires it first (a real write, +1 vacancy)
        // inside the same transaction, so the failed confirmation must undo that write too.
        Scenario unrelated = scenario("Rollback Vizinho");
        String unrelatedRequestId = requestMatch(unrelated.moverId(), unrelated.targetHouseId());
        approve(unrelated.targetAdminId(), unrelatedRequestId);
        assertEquals(1, vacanciesOf(unrelated.targetHouseId()));
        expireDeadline(unrelatedRequestId);

        mockMvc.perform(post("/api/matches/requests/" + requestId + "/confirm-move")
                        .with(TestTokens.jwtForUser(s.moverId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("new admin required"));

        assertEquals("AWAITING_MOVE", statusOf(unrelatedRequestId), "the expiry write must be rolled back");
        assertEquals(1, vacanciesOf(unrelated.targetHouseId()), "the returned vacancy must be rolled back");

        assertEquals(s.oldHouseId(), houseOf(s.moverId()));
        assertTrue(isAdmin(s.moverId()));
        assertFalse(isAdmin(s.heirId()));
        assertEquals("AWAITING_MOVE", statusOf(requestId));
        assertEquals(1, vacanciesOf(s.targetHouseId()));
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE house_id = ? AND id = ?",
                s.targetHouseId(), s.moverId()));
        assertEquals(historyBefore, count("SELECT COUNT(*) FROM residence_history WHERE user_id = ?", s.moverId()));
        assertEquals(1, count("SELECT COUNT(*) FROM residence_history WHERE user_id = ? AND house_id = ? "
                + "AND left_at IS NULL", s.moverId(), s.oldHouseId()));
    }

    @Test
    void confirmMoveWithHeirShouldMoveInOneTransactionAndCancelOtherReservations() throws Exception {
        Scenario s = scenario("Confirma");
        String otherAdminId = registerUser("Admin Outra Casa");
        String otherHouseId = createHouse(otherAdminId, "Casa Outra Confirma", 2);

        String requestId = requestMatch(s.moverId(), s.targetHouseId());
        String otherRequestId = requestMatch(s.moverId(), otherHouseId);
        approve(s.targetAdminId(), requestId);
        approve(otherAdminId, otherRequestId);
        assertEquals(1, vacanciesOf(s.targetHouseId()));
        assertEquals(1, vacanciesOf(otherHouseId));

        // Another user cannot confirm someone else's move.
        mockMvc.perform(post("/api/matches/requests/" + requestId + "/confirm-move")
                        .with(TestTokens.jwtForUser(s.heirId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newAdminId\":\"" + s.heirId() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the owner can perform this action"));

        mockMvc.perform(post("/api/matches/requests/" + requestId + "/confirm-move")
                        .with(TestTokens.jwtForUser(s.moverId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newAdminId\":\"" + s.heirId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(s.moverId()))
                .andExpect(jsonPath("$.houseId").value(s.targetHouseId()))
                .andExpect(jsonPath("$.isHouseAdmin").value(false))
                .andExpect(jsonPath("$.pendingWelcome").value(false));

        assertEquals(s.targetHouseId(), houseOf(s.moverId()));
        assertFalse(isAdmin(s.moverId()));
        assertTrue(isAdmin(s.heirId()), "heir must be promoted in the old house");
        assertEquals(s.oldHouseId(), houseOf(s.heirId()));
        assertEquals("ACCEPTED", statusOf(requestId));
        assertEquals(1, vacanciesOf(s.targetHouseId()), "the confirmed reservation keeps its vacancy");

        // Residence history: left the old house, entered the new one.
        assertEquals(0, count("SELECT COUNT(*) FROM residence_history WHERE user_id = ? AND house_id = ? "
                + "AND left_at IS NULL", s.moverId(), s.oldHouseId()));
        assertEquals(1, count("SELECT COUNT(*) FROM residence_history WHERE user_id = ? AND house_id = ? "
                + "AND left_at IS NULL", s.moverId(), s.targetHouseId()));

        // The other reservation is cancelled and its vacancy given back exactly once.
        assertEquals("CANCELLED", statusOf(otherRequestId));
        assertEquals(2, vacanciesOf(otherHouseId));

        // Already confirmed: a second confirmation is a conflict.
        mockMvc.perform(post("/api/matches/requests/" + requestId + "/confirm-move")
                        .with(TestTokens.jwtForUser(s.moverId())))
                .andExpect(status().isConflict());
    }

    // ---------- recusar e retirar ----------

    @Test
    void declineMoveShouldWithdrawAndGiveTheVacancyBack() throws Exception {
        Scenario s = scenario("Recusa");
        String requestId = requestMatch(s.moverId(), s.targetHouseId());
        approve(s.targetAdminId(), requestId);

        mockMvc.perform(post("/api/matches/requests/" + requestId + "/decline-move")
                        .with(TestTokens.jwtForUser(s.targetAdminId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the owner can perform this action"));

        mockMvc.perform(post("/api/matches/requests/" + requestId + "/decline-move")
                        .with(TestTokens.jwtForUser(s.moverId())))
                .andExpect(status().isNoContent());

        assertEquals("WITHDRAWN", statusOf(requestId));
        assertEquals(2, vacanciesOf(s.targetHouseId()));
        assertEquals(s.oldHouseId(), houseOf(s.moverId()));

        mockMvc.perform(post("/api/matches/requests/" + requestId + "/decline-move")
                        .with(TestTokens.jwtForUser(s.moverId())))
                .andExpect(status().isConflict());
        assertEquals(2, vacanciesOf(s.targetHouseId()));
    }

    @Test
    void retractShouldBeTargetAdminOnlyAndGiveTheVacancyBack() throws Exception {
        Scenario s = scenario("Retira");
        String targetResidentId = registerUser("Morador Destino Retira");
        putIntoHouse(targetResidentId, s.targetHouseId());
        String requestId = requestMatch(s.moverId(), s.targetHouseId());
        mockMvc.perform(post("/api/matches/requests/" + requestId + "/vote")
                        .with(TestTokens.jwtForUser(s.targetAdminId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"APPROVE\"}"))
                .andExpect(status().isOk());
        approve(targetResidentId, requestId);
        assertEquals(1, vacanciesOf(s.targetHouseId()));

        // A resident of the target house who is not admin, and the mover, cannot retract.
        mockMvc.perform(post("/api/matches/requests/" + requestId + "/retract")
                        .with(TestTokens.jwtForUser(targetResidentId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/matches/requests/" + requestId + "/retract")
                        .with(TestTokens.jwtForUser(s.moverId())))
                .andExpect(status().isForbidden());
        assertEquals("AWAITING_MOVE", statusOf(requestId));

        mockMvc.perform(post("/api/matches/requests/" + requestId + "/retract")
                        .with(TestTokens.jwtForUser(s.targetAdminId())))
                .andExpect(status().isNoContent());

        assertEquals("CANCELLED", statusOf(requestId));
        assertEquals(2, vacanciesOf(s.targetHouseId()));

        mockMvc.perform(post("/api/matches/requests/" + requestId + "/retract")
                        .with(TestTokens.jwtForUser(s.targetAdminId())))
                .andExpect(status().isConflict());
        assertEquals(2, vacanciesOf(s.targetHouseId()));
    }

    // ---------- expiração ----------

    @Test
    void overdueReservationShouldExpireOnReadAndTheJobMustNotGiveTheVacancyBackTwice() throws Exception {
        Scenario s = scenario("Expira Leitura");
        String requestId = requestMatch(s.moverId(), s.targetHouseId());
        approve(s.targetAdminId(), requestId);
        assertEquals(1, vacanciesOf(s.targetHouseId()));
        expireDeadline(requestId);

        mockMvc.perform(get("/api/matches/requests/mine")
                        .with(TestTokens.jwtForUser(s.moverId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].status").value("EXPIRED"));

        assertEquals("EXPIRED", statusOf(requestId));
        assertEquals(2, vacanciesOf(s.targetHouseId()));

        matchService.expireOverdueMoves();
        matchService.expireOverdueMoves();
        assertEquals(2, vacanciesOf(s.targetHouseId()), "expiration must give the vacancy back exactly once");

        // Expired: it can no longer be confirmed, and the mover stays where they were.
        mockMvc.perform(post("/api/matches/requests/" + requestId + "/confirm-move")
                        .with(TestTokens.jwtForUser(s.moverId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newAdminId\":\"" + s.heirId() + "\"}"))
                .andExpect(status().isConflict());
        assertEquals(s.oldHouseId(), houseOf(s.moverId()));
    }

    @Test
    void scheduledJobShouldExpireOverdueReservationsIdempotently() throws Exception {
        Scenario s = scenario("Expira Job");
        String requestId = requestMatch(s.moverId(), s.targetHouseId());
        approve(s.targetAdminId(), requestId);
        assertEquals(1, vacanciesOf(s.targetHouseId()));
        expireDeadline(requestId);

        matchService.expireOverdueMoves();
        assertEquals("EXPIRED", statusOf(requestId));
        assertEquals(2, vacanciesOf(s.targetHouseId()));

        matchService.expireOverdueMoves();
        mockMvc.perform(get("/api/houses/" + s.targetHouseId() + "/matches")
                        .with(TestTokens.jwtForUser(s.targetAdminId())))
                .andExpect(status().isOk());
        assertEquals(2, vacanciesOf(s.targetHouseId()));
    }

    // ---------- 401 / 403 dos endpoints novos ----------

    @Test
    void newEndpointsShouldReturn401WithoutTokenAnd403WithoutScopeOrPermission() throws Exception {
        for (String action : new String[]{"confirm-move", "decline-move", "retract"}) {
            mockMvc.perform(post("/api/matches/requests/r1/" + action)).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/matches/requests/r1/" + action)
                            .with(TestTokens.jwtWith("b6-user", "ROLE_MATCH", "SCOPE_read")))
                    .andExpect(status().isForbidden());
        }

        // confirm-move and decline-move need only SCOPE_write: the request reaches the service (404).
        String userId = registerUser("Só Write");
        mockMvc.perform(post("/api/matches/requests/missing/confirm-move")
                        .with(TestTokens.jwtWith(userId, "SCOPE_write")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/matches/requests/missing/decline-move")
                        .with(TestTokens.jwtWith(userId, "SCOPE_write")))
                .andExpect(status().isNotFound());

        // retract also needs ROLE_MATCH.
        mockMvc.perform(post("/api/matches/requests/missing/retract")
                        .with(TestTokens.jwtWith(userId, "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/matches/requests/missing/retract")
                        .with(TestTokens.jwtWith(userId, "ROLE_MATCH", "SCOPE_write")))
                .andExpect(status().isNotFound());
    }
}
