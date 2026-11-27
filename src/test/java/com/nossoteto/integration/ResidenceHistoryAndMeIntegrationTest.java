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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResidenceHistoryAndMeIntegrationTest {

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

    private String registerUser(String name) throws Exception {
        String email = "b4b-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"senhaSegura123\"}"))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail(email).orElseThrow();
        return user.getId();
    }

    @Test
    void flywayV7BackfillShouldSeedOpenResidenceHistoryForExistingResidents() {
        String dbName = "v7_backfill_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );

        Flyway flywayToV6 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("6")
                .load();
        flywayToV6.migrate();

        JdbcTemplate isolatedJdbc = new JdbcTemplate(dataSource);
        String houseId = UUID.randomUUID().toString();
        String residentId = UUID.randomUUID().toString();
        String residentNoCreatedAtId = UUID.randomUUID().toString();
        String candidateId = UUID.randomUUID().toString();
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 15, 10, 30, 0);

        isolatedJdbc.update(
                "INSERT INTO houses (id, name, address, description, is_accepting_matches) VALUES (?, ?, ?, ?, ?)",
                houseId, "República Backfill", "Rua Teste, 1", "Desc", true
        );
        isolatedJdbc.update(
                "INSERT INTO users (id, name, email, password_hash, house_id, is_house_admin, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                residentId, "Morador Antigo", "antigo@test.com", "hash", houseId, true, Timestamp.valueOf(createdAt)
        );
        isolatedJdbc.update(
                "INSERT INTO users (id, name, email, password_hash, house_id, is_house_admin, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL)",
                residentNoCreatedAtId, "Morador Sem Data", "semdata@test.com", "hash", houseId, false
        );
        isolatedJdbc.update(
                "INSERT INTO users (id, name, email, password_hash, house_id, is_house_admin, created_at) "
                        + "VALUES (?, ?, ?, ?, NULL, ?, ?)",
                candidateId, "Candidato Sem Casa", "cand@test.com", "hash", false, Timestamp.valueOf(createdAt)
        );

        Flyway flywayLatest = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flywayLatest.migrate();

        List<Map<String, Object>> rows = isolatedJdbc.queryForList(
                "SELECT id, user_id, house_id, joined_at, left_at FROM residence_history ORDER BY user_id"
        );
        assertEquals(2, rows.size());

        Map<String, Object> residentRow = isolatedJdbc.queryForMap(
                "SELECT id, user_id, house_id, joined_at, left_at FROM residence_history WHERE user_id = ?",
                residentId
        );
        assertEquals("seed-" + residentId, residentRow.get("ID"));
        assertEquals(41, ((String) residentRow.get("ID")).length());
        assertEquals(houseId, residentRow.get("HOUSE_ID"));
        assertEquals(Timestamp.valueOf(createdAt), residentRow.get("JOINED_AT"));
        assertNull(residentRow.get("LEFT_AT"));

        Map<String, Object> noCreatedAtRow = isolatedJdbc.queryForMap(
                "SELECT id, joined_at, left_at FROM residence_history WHERE user_id = ?",
                residentNoCreatedAtId
        );
        assertEquals("seed-" + residentNoCreatedAtId, noCreatedAtRow.get("ID"));
        assertNotNull(noCreatedAtRow.get("JOINED_AT"));
        assertNull(noCreatedAtRow.get("LEFT_AT"));
    }

    @Test
    void getMeShouldReturnEnrichedProfileCompletePermissionsPointsAndResidenceMonths() throws Exception {
        String userId = registerUser("Helena Moradora");

        // Before filling profile, joining a house or earning points
        MvcResult initialMe = mockMvc.perform(get("/api/users/me")
                        .with(TestTokens.jwtForUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.name").value("Helena Moradora"))
                .andExpect(jsonPath("$.isHouseAdmin").value(false))
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.points.total").value(0))
                .andExpect(jsonPath("$.points.thisMonth").value(0))
                .andExpect(jsonPath("$.residenceMonths").value(nullValue()))
                .andReturn();

        JsonNode initialPermissions = objectMapper.readTree(initialMe.getResponse().getContentAsString())
                .get("permissions");
        assertTrue(initialPermissions.size() > 0);
        for (int i = 1; i < initialPermissions.size(); i++) {
            assertTrue(
                    initialPermissions.get(i - 1).asText().compareTo(initialPermissions.get(i).asText()) <= 0,
                    "permissions must be sorted alphabetically"
            );
        }

        // Partial profile (missing visitorTolerance) -> still incomplete
        mockMvc.perform(put("/api/users/me/profile")
                        .with(TestTokens.jwtForUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "budget": 1400.00,
                                  "hasPets": false,
                                  "isSmoker": false,
                                  "organizationLevel": 4
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me")
                        .with(TestTokens.jwtForUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileComplete").value(false));

        // Complete all 5 match fields
        mockMvc.perform(put("/api/users/me/profile")
                        .with(TestTokens.jwtForUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "budget": 1400.00,
                                  "hasPets": false,
                                  "isSmoker": false,
                                  "organizationLevel": 4,
                                  "visitorTolerance": 3
                                }
                                """))
                .andExpect(status().isOk());

        // Credit points
        pointService.creditIfAbsent(
                userId, null, PointReason.GROCERY_BOUGHT, 5,
                PointSourceType.GROCERY, "g-" + UUID.randomUUID(), "", "Pão");
        pointService.creditIfAbsent(
                userId, null, PointReason.EXPENSE_ON_TIME, 10,
                PointSourceType.EXPENSE_SPLIT, "e-" + UUID.randomUUID(), "", "Luz");

        // Create a house -> records entry in residence_history -> residenceMonths == 0
        MvcResult houseResult = mockMvc.perform(post("/api/houses")
                        .with(TestTokens.jwtForUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Casa Helena",
                                  "rentAmount": 1200.00,
                                  "allowsPets": true,
                                  "allowsSmokers": false,
                                  "expectedOrganizationLevel": 4,
                                  "visitorTolerance": 3
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String houseId = objectMapper.readTree(houseResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/users/me")
                        .with(TestTokens.jwtForUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseId").value(houseId))
                .andExpect(jsonPath("$.houseName").value("Casa Helena"))
                .andExpect(jsonPath("$.isHouseAdmin").value(true))
                .andExpect(jsonPath("$.profileComplete").value(true))
                .andExpect(jsonPath("$.points.total").value(15))
                .andExpect(jsonPath("$.points.thisMonth").value(15))
                .andExpect(jsonPath("$.residenceMonths").value(0));

        // Leave house -> closes residence_history entry
        mockMvc.perform(post("/api/houses/" + houseId + "/leave")
                        .with(TestTokens.jwtForUser(userId)))
                .andExpect(status().isNoContent());

        Integer openStays = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM residence_history WHERE user_id = ? AND left_at IS NULL",
                Integer.class,
                userId
        );
        assertEquals(0, openStays);

        Integer totalStays = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM residence_history WHERE user_id = ?",
                Integer.class,
                userId
        );
        assertEquals(1, totalStays);

        // Sum days across multiple houses: 40 days closed in Casa Helena + 25 days open in Casa Nova = 65 days -> 2 months
        MvcResult house2Result = mockMvc.perform(post("/api/houses")
                        .with(TestTokens.jwtForUser(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Casa Helena 2\",\"rentAmount\":1100.00}"))
                .andExpect(status().isCreated())
                .andReturn();
        String house2Id = objectMapper.readTree(house2Result.getResponse().getContentAsString()).get("id").asText();

        LocalDateTime nowUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
        jdbcTemplate.update(
                "UPDATE residence_history SET joined_at = ?, left_at = ? WHERE user_id = ? AND house_id = ?",
                Timestamp.valueOf(nowUtc.minusDays(80)),
                Timestamp.valueOf(nowUtc.minusDays(40)),
                userId,
                houseId
        );
        jdbcTemplate.update(
                "UPDATE residence_history SET joined_at = ?, left_at = NULL WHERE user_id = ? AND house_id = ?",
                Timestamp.valueOf(nowUtc.minusDays(25)),
                userId,
                house2Id
        );

        mockMvc.perform(get("/api/users/me")
                        .with(TestTokens.jwtForUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.residenceMonths").value(2));
    }

    @Test
    void housesShouldReturnNullCompatibilityWhenProfileIncompleteAndMatchAcceptShouldRecordResidenceEntry()
            throws Exception {
        String adminId = registerUser("Igor Admin");
        String candidateId = registerUser("Julia Candidata");

        MvcResult houseResult = mockMvc.perform(post("/api/houses")
                        .with(TestTokens.jwtForUser(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Casa Compatibilidade",
                                  "rentAmount": 1000.00,
                                  "allowsPets": true,
                                  "allowsSmokers": false,
                                  "expectedOrganizationLevel": 4,
                                  "visitorTolerance": 3,
                                  "votingRule": "UNANIMITY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String houseId = objectMapper.readTree(houseResult.getResponse().getContentAsString()).get("id").asText();

        // Candidate with no profile -> compatibilityScore must be null in both list and details
        MvcResult listBefore = mockMvc.perform(get("/api/houses")
                        .with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listNodes = objectMapper.readTree(listBefore.getResponse().getContentAsString());
        boolean foundNullInList = false;
        for (JsonNode node : listNodes) {
            if (houseId.equals(node.get("id").asText())) {
                assertTrue(node.get("compatibilityScore").isNull(),
                        "Expected compatibilityScore to be null for incomplete profile");
                foundNullInList = true;
            }
        }
        assertTrue(foundNullInList);

        mockMvc.perform(get("/api/houses/" + houseId)
                        .with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(houseId))
                .andExpect(jsonPath("$.compatibilityScore").value(nullValue()));

        // Partial profile (missing budget) -> still null
        mockMvc.perform(put("/api/users/me/profile")
                        .with(TestTokens.jwtForUser(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hasPets": false,
                                  "isSmoker": false,
                                  "organizationLevel": 4,
                                  "visitorTolerance": 3
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/houses/" + houseId)
                        .with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compatibilityScore").value(nullValue()));

        // Complete profile -> score becomes 100
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

        mockMvc.perform(get("/api/houses/" + houseId)
                        .with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compatibilityScore").value(100));

        // Candidate requests match and Admin approves -> entry recorded in residence_history
        MvcResult reqResult = mockMvc.perform(post("/api/matches/requests")
                        .with(TestTokens.jwtForUser(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\":\"" + houseId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String requestId = objectMapper.readTree(reqResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/matches/requests/" + requestId + "/vote")
                        .with(TestTokens.jwtForUser(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        Integer openStays = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM residence_history WHERE user_id = ? AND house_id = ? AND left_at IS NULL",
                Integer.class,
                candidateId,
                houseId
        );
        assertEquals(1, openStays);

        mockMvc.perform(get("/api/users/me")
                        .with(TestTokens.jwtForUser(candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.houseId").value(houseId))
                .andExpect(jsonPath("$.residenceMonths").value(0));
    }
}
