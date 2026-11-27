package com.nossoteto.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B7: multi-admin, mandatory heir on leave, removal by voting, and removal of DELETE /members/{memberId}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HouseManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String registerUser(String name) throws Exception {
        String email = "b7-" + UUID.randomUUID() + "@nossoteto.com";
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

    private void addResidentToHouse(String houseId, String candidateId, List<String> currentVoterIds) throws Exception {
        MvcResult reqRes = mockMvc.perform(post("/api/matches/requests")
                        .with(TestTokens.jwtForUser(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\":\"" + houseId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String requestId = objectMapper.readTree(reqRes.getResponse().getContentAsString()).get("id").asText();

        for (String voterId : currentVoterIds) {
            MvcResult voteRes = mockMvc.perform(post("/api/matches/requests/" + requestId + "/vote")
                            .with(TestTokens.jwtForUser(voterId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"vote\":\"APPROVE\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            if ("ACCEPTED".equals(objectMapper.readTree(voteRes.getResponse().getContentAsString()).get("status").asText())) {
                return;
            }
        }
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value != null ? value : 0;
    }

    // ---------- 401 / 403 of new endpoints ----------

    @Test
    void newEndpointsShouldReturn401WithoutTokenAnd403WithoutRequiredRoleOrScope() throws Exception {
        mockMvc.perform(put("/api/houses/h1/members/u2/admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isAdmin\":true}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/houses/h1/removals")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":\"u2\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/houses/h1/removals"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/houses/h1/removals/r1/vote")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"APPROVE\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/houses/h1/removals/r1"))
                .andExpect(status().isUnauthorized());

        // PUT /members/{m}/admin -> ROLE_MANAGE_HOUSE + SCOPE_write
        mockMvc.perform(put("/api/houses/h1/members/u2/admin")
                        .with(TestTokens.jwtWith("b7-user", "SCOPE_read", "SCOPE_write"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isAdmin\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/houses/h1/members/u2/admin")
                        .with(TestTokens.jwtWith("b7-user", "ROLE_MANAGE_HOUSE", "SCOPE_read"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isAdmin\":true}"))
                .andExpect(status().isForbidden());

        // POST /removals -> ROLE_MANAGE_HOUSE + SCOPE_write
        mockMvc.perform(post("/api/houses/h1/removals")
                        .with(TestTokens.jwtWith("b7-user", "SCOPE_read", "SCOPE_write"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":\"u2\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/houses/h1/removals")
                        .with(TestTokens.jwtWith("b7-user", "ROLE_MANAGE_HOUSE", "SCOPE_read"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":\"u2\"}"))
                .andExpect(status().isForbidden());

        // GET /removals -> ROLE_MANAGE_HOUSE + SCOPE_read
        mockMvc.perform(get("/api/houses/h1/removals")
                        .with(TestTokens.jwtWith("b7-user", "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/houses/h1/removals")
                        .with(TestTokens.jwtWith("b7-user", "ROLE_MANAGE_HOUSE", "SCOPE_write")))
                .andExpect(status().isForbidden());

        // POST /removals/{r}/vote -> SCOPE_write only (no ROLE_MANAGE_HOUSE required)
        mockMvc.perform(post("/api/houses/h1/removals/r1/vote")
                        .with(TestTokens.jwtWith("b7-user", "ROLE_MANAGE_HOUSE", "SCOPE_read"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vote\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());

        // DELETE /removals/{r} -> ROLE_MANAGE_HOUSE + SCOPE_write
        mockMvc.perform(delete("/api/houses/h1/removals/r1")
                        .with(TestTokens.jwtWith("b7-user", "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/houses/h1/removals/r1")
                        .with(TestTokens.jwtWith("b7-user", "ROLE_MANAGE_HOUSE", "SCOPE_read")))
                .andExpect(status().isForbidden());
    }

    // ---------- DELETE /members/{memberId} removed ----------

    @Test
    void directDeleteMemberEndpointShouldReturn404Or405AndKeepMemberInHouse() throws Exception {
        String adminId = registerUser("Admin DeleteCheck");
        String memberId = registerUser("Member DeleteCheck");
        String houseId = createHouse(adminId, "{\"name\":\"Casa Sem Delete\",\"vacancies\":3}");
        addResidentToHouse(houseId, memberId, List.of(adminId));

        mockMvc.perform(delete("/api/houses/" + houseId + "/members/" + memberId)
                        .with(TestTokens.jwtForUser(adminId)))
                .andExpect(status().is(anyOf(is(404), is(405))));

        assertEquals(houseId, userRepository.findById(memberId).orElseThrow().getHouseId());
    }

    // ---------- Multi-admin ----------

    @Test
    void multiAdminShouldPromoteDemoteAndProtectLastAdmin() throws Exception {
        String adminA = registerUser("Admin A");
        String memberB = registerUser("Member B");
        String memberC = registerUser("Member C");
        String houseId = createHouse(adminA, "{\"name\":\"Casa Multi Admin\",\"vacancies\":5}");
        addResidentToHouse(houseId, memberB, List.of(adminA));
        addResidentToHouse(houseId, memberC, List.of(adminA, memberB));

        // Non-admin cannot promote
        mockMvc.perform(put("/api/houses/" + houseId + "/members/" + memberC + "/admin")
                        .with(TestTokens.jwtForUser(memberB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAdmin\": true}"))
                .andExpect(status().isForbidden());

        // Missing isAdmin -> 400
        mockMvc.perform(put("/api/houses/" + houseId + "/members/" + memberB + "/admin")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // Admin A promotes Member B
        mockMvc.perform(put("/api/houses/" + houseId + "/members/" + memberB + "/admin")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAdmin\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberB))
                .andExpect(jsonPath("$.isHouseAdmin").value(true));
        assertTrue(userRepository.findById(memberB).orElseThrow().getIsHouseAdmin());

        // Admin A demotes self (allowed because B is also admin)
        mockMvc.perform(put("/api/houses/" + houseId + "/members/" + adminA + "/admin")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAdmin\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(adminA))
                .andExpect(jsonPath("$.isHouseAdmin").value(false));
        assertFalse(userRepository.findById(adminA).orElseThrow().getIsHouseAdmin());

        // B is now the last admin -> demoting B must fail with 400
        mockMvc.perform(put("/api/houses/" + houseId + "/members/" + memberB + "/admin")
                        .with(TestTokens.jwtForUser(memberB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAdmin\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("house must keep at least one admin"));
        assertTrue(userRepository.findById(memberB).orElseThrow().getIsHouseAdmin());
    }

    // ---------- Leave with mandatory heir + cancel OPEN removals ----------

    @Test
    void leaveHouseShouldRequireHeirForLastAdminAndCancelOpenRemovals() throws Exception {
        String adminA = registerUser("Admin Leave");
        String memberB = registerUser("Member B Leave");
        String memberC = registerUser("Member C Leave");
        String outsider = registerUser("Outsider Leave");
        String houseId = createHouse(adminA, "{\"name\":\"Casa Leave\",\"vacancies\":5}");
        addResidentToHouse(houseId, memberB, List.of(adminA));
        addResidentToHouse(houseId, memberC, List.of(adminA, memberB));

        // Last admin without body -> 400 new admin required, no writes
        mockMvc.perform(post("/api/houses/" + houseId + "/leave")
                        .with(TestTokens.jwtForUser(adminA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("new admin required"));
        assertEquals(houseId, userRepository.findById(adminA).orElseThrow().getHouseId());

        // Last admin passing self or outsider -> 400 new admin required
        mockMvc.perform(post("/api/houses/" + houseId + "/leave")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newAdminId\":\"" + adminA + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("new admin required"));

        mockMvc.perform(post("/api/houses/" + houseId + "/leave")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newAdminId\":\"" + outsider + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("new admin required"));

        // Open removal against B; B leaves on their own -> removal becomes CANCELLED
        MvcResult remBRes = mockMvc.perform(post("/api/houses/" + houseId + "/removals")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + memberB + "\",\"reason\":\"Atraso\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String remBId = objectMapper.readTree(remBRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/houses/" + houseId + "/leave")
                        .with(TestTokens.jwtForUser(memberB)))
                .andExpect(status().isNoContent());

        assertNull(userRepository.findById(memberB).orElseThrow().getHouseId());
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM house_removal_requests WHERE id = ?", String.class, remBId));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT resolved_at FROM house_removal_requests WHERE id = ?", Object.class, remBId));
        mockMvc.perform(get("/api/houses/" + houseId + "/removals").with(TestTokens.jwtForUser(adminA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Open removal proposed by A against C; A leaves naming C as heir -> C becomes admin and A's removal is CANCELLED
        MvcResult remCRes = mockMvc.perform(post("/api/houses/" + houseId + "/removals")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + memberC + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String remCId = objectMapper.readTree(remCRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/houses/" + houseId + "/leave")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newAdminId\":\"" + memberC + "\"}"))
                .andExpect(status().isNoContent());

        assertNull(userRepository.findById(adminA).orElseThrow().getHouseId());
        assertTrue(userRepository.findById(memberC).orElseThrow().getIsHouseAdmin());
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM house_removal_requests WHERE id = ?", String.class, remCId));

        // Last resident C leaves without body -> 204 and house closes
        mockMvc.perform(post("/api/houses/" + houseId + "/leave")
                        .with(TestTokens.jwtForUser(memberC)))
                .andExpect(status().isNoContent());
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "SELECT is_accepting_matches FROM houses WHERE id = ?", Boolean.class, houseId));
    }

    // ---------- Removal voting flow (swap, hidden direction, target 403, cancel) ----------

    @Test
    void removalVotingShouldHideVoteDirectionForbidTargetAllowSwapAndRestrictCancelToProposer() throws Exception {
        String adminA = registerUser("Alice Rem");
        String memberB = registerUser("Bruno Rem");
        String memberC = registerUser("Carla Rem");
        String houseId = createHouse(adminA, "{\"name\":\"Casa Remocao\",\"vacancies\":5,\"votingRule\":\"UNANIMITY\"}");
        addResidentToHouse(houseId, memberB, List.of(adminA));
        addResidentToHouse(houseId, memberC, List.of(adminA, memberB));

        // Non-admin cannot propose
        mockMvc.perform(post("/api/houses/" + houseId + "/removals")
                        .with(TestTokens.jwtForUser(memberB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + memberC + "\"}"))
                .andExpect(status().isForbidden());

        // Admin cannot propose self
        mockMvc.perform(post("/api/houses/" + houseId + "/removals")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + adminA + "\"}"))
                .andExpect(status().isBadRequest());

        // Admin proposes removing B
        MvcResult created = mockMvc.perform(post("/api/houses/" + houseId + "/removals")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + memberB + "\",\"reason\":\"Regras descumpridas\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetUserId").value(memberB))
                .andExpect(jsonPath("$.targetUserName").value("Bruno Rem"))
                .andExpect(jsonPath("$.proposedById").value(adminA))
                .andExpect(jsonPath("$.proposedByName").value("Alice Rem"))
                .andExpect(jsonPath("$.reason").value("Regras descumpridas"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.votesCast").value(0))
                .andExpect(jsonPath("$.votersTotal").value(2))
                .andReturn();
        String removalId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // Duplicate open removal for same member -> 409
        mockMvc.perform(post("/api/houses/" + houseId + "/removals")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + memberB + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("removal already open for this member"));

        // Target cannot vote -> 403
        mockMvc.perform(post("/api/houses/" + houseId + "/removals/" + removalId + "/vote")
                        .with(TestTokens.jwtForUser(memberB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"REJECT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("target member cannot vote on own removal"));

        // Admin votes APPROVE -> stays OPEN (1 of 2)
        mockMvc.perform(post("/api/houses/" + houseId + "/removals/" + removalId + "/vote")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.votesCast").value(1))
                .andExpect(jsonPath("$.votersTotal").value(2))
                .andExpect(jsonPath("$.voters[0].id").value(adminA))
                .andExpect(jsonPath("$.voters[0].name").value("Alice Rem"))
                .andExpect(jsonPath("$.voters[0].vote").doesNotExist())
                .andExpect(jsonPath("$.myVote").value("APPROVE"));

        // Member C lists open removals -> sees A voted without direction, myVote is null
        mockMvc.perform(get("/api/houses/" + houseId + "/removals")
                        .with(TestTokens.jwtForUser(memberC)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].voters.length()").value(1))
                .andExpect(jsonPath("$[0].voters[0].id").value(adminA))
                .andExpect(jsonPath("$[0].voters[0].vote").doesNotExist())
                .andExpect(jsonPath("$[0].myVote").value(nullValue()));

        // Another user (C) cannot cancel A's removal -> 403
        mockMvc.perform(delete("/api/houses/" + houseId + "/removals/" + removalId)
                        .with(TestTokens.jwtForUser(memberC)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("only the owner can perform this action"));

        // A swaps vote to REJECT -> upsert (still 1 row), UNANIMITY rejects
        mockMvc.perform(post("/api/houses/" + houseId + "/removals/" + removalId + "/vote")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"REJECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.myVote").value("REJECT"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        assertEquals(1, count("SELECT COUNT(*) FROM house_removal_votes WHERE removal_id = ? AND voter_id = ?",
                removalId, adminA));
        assertEquals(houseId, userRepository.findById(memberB).orElseThrow().getHouseId());

        // New removal can be proposed after rejection and cancelled by proposer
        MvcResult secondRes = mockMvc.perform(post("/api/houses/" + houseId + "/removals")
                        .with(TestTokens.jwtForUser(adminA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + memberB + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String secondId = objectMapper.readTree(secondRes.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/houses/" + houseId + "/removals/" + secondId)
                        .with(TestTokens.jwtForUser(adminA)))
                .andExpect(status().isNoContent());
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM house_removal_requests WHERE id = ?", String.class, secondId));

        // Cancelling again -> 400
        mockMvc.perform(delete("/api/houses/" + houseId + "/removals/" + secondId)
                        .with(TestTokens.jwtForUser(adminA)))
                .andExpect(status().isBadRequest());
    }

    // ---------- Removing the sole admin promotes deterministic successor ----------

    @Test
    void removingSoleAdminByVoteShouldPromoteDeterministicSuccessorAndPreserveDebts() throws Exception {
        String userA = registerUser("Admin A Sole");
        String userB = registerUser("Admin B Sole");
        String userC = registerUser("Member C Sole");
        String houseId = createHouse(userA, "{\"name\":\"Casa Sole Admin\",\"vacancies\":5,\"votingRule\":\"MAJORITY\"}");
        addResidentToHouse(houseId, userB, List.of(userA));
        addResidentToHouse(houseId, userC, List.of(userA, userB));

        // Promote B so both A and B are admins
        mockMvc.perform(put("/api/houses/" + houseId + "/members/" + userB + "/admin")
                        .with(TestTokens.jwtForUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAdmin\": true}"))
                .andExpect(status().isOk());

        // Create an expense so B has an open split that must remain after removal
        mockMvc.perform(post("/api/houses/" + houseId + "/expenses")
                        .with(TestTokens.jwtForUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Internet\",\"totalAmount\":300.00,\"dueDate\":\"2026-12-10\"}"))
                .andExpect(status().isCreated());

        // A proposes removing B
        MvcResult remRes = mockMvc.perform(post("/api/houses/" + houseId + "/removals")
                        .with(TestTokens.jwtForUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + userB + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String removalId = objectMapper.readTree(remRes.getResponse().getContentAsString()).get("id").asText();

        // A demotes self -> now B is the SOLE admin of the house!
        mockMvc.perform(put("/api/houses/" + houseId + "/members/" + userA + "/admin")
                        .with(TestTokens.jwtForUser(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isAdmin\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isHouseAdmin").value(false));

        // A votes APPROVE using ONLY SCOPE_write (no ROLE_MANAGE_HOUSE required for voting!) -> 1 of 2, OPEN
        mockMvc.perform(post("/api/houses/" + houseId + "/removals/" + removalId + "/vote")
                        .with(TestTokens.jwtWith(userA, "SCOPE_write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.votesCast").value(1))
                .andExpect(jsonPath("$.votersTotal").value(2));

        // C votes APPROVE -> 2 of 2, APPROVED -> B detached, deterministic successor promoted!
        mockMvc.perform(post("/api/houses/" + houseId + "/removals/" + removalId + "/vote")
                        .with(TestTokens.jwtWith(userC, "SCOPE_write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        User reloadedB = userRepository.findById(userB).orElseThrow();
        assertNull(reloadedB.getHouseId());
        assertFalse(reloadedB.getIsHouseAdmin());
        assertEquals(0, count(
                "SELECT COUNT(*) FROM residence_history WHERE user_id = ? AND house_id = ? AND left_at IS NULL",
                userB, houseId));

        // Deterministic successor by ID among remaining {userA, userC} must now be admin
        List<String> remainingOrdered = List.of(userA, userC).stream().sorted().toList();
        String expectedSuccessor = remainingOrdered.stream()
                .filter(id -> id.compareTo(userB) > 0)
                .findFirst()
                .orElse(remainingOrdered.get(0));
        String otherRemaining = expectedSuccessor.equals(userA) ? userC : userA;

        assertTrue(userRepository.findById(expectedSuccessor).orElseThrow().getIsHouseAdmin(),
                "deterministic successor should be promoted when sole admin is removed");
        assertFalse(userRepository.findById(otherRemaining).orElseThrow().getIsHouseAdmin());

        // B's unpaid expense split remains in the house
        assertEquals(1, count("SELECT COUNT(*) FROM expense_splits WHERE user_id = ? AND is_paid = FALSE", userB));
    }
}
