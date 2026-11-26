package com.nossoteto.integration;

import com.nossoteto.dto.PointsSummaryDto;
import com.nossoteto.model.PointLedgerEntry;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.model.enums.PointSourceType;
import com.nossoteto.repository.PointLedgerRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import com.nossoteto.service.HouseAccessValidator;
import com.nossoteto.service.PointService;
import com.nossoteto.support.TestTokens;
import com.nossoteto.util.AppTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PointLedgerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointLedgerRepository pointLedgerRepository;

    @Autowired
    private PointService pointService;

    @Autowired
    private HouseAccessValidator houseAccessValidator;

    private String registerUser(String name) throws Exception {
        String email = "points-" + UUID.randomUUID() + "@nossoteto.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"senhaSegura123\"}"))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail(email).orElseThrow();
        return user.getId();
    }

    @Test
    void getMyPointsShouldEnforce401And403AndReturn200WithOrderedHistory() throws Exception {
        String userId = registerUser("Ana Pontos");

        // 401 without authentication
        mockMvc.perform(get("/api/users/me/points"))
                .andExpect(status().isUnauthorized());

        // 403 without ROLE_MANAGE_PROFILE
        mockMvc.perform(get("/api/users/me/points")
                        .with(TestTokens.jwtWith(userId, "SCOPE_read", "SCOPE_write")))
                .andExpect(status().isForbidden());

        // 403 with ROLE_MANAGE_PROFILE but wrong scope (SCOPE_write only)
        mockMvc.perform(get("/api/users/me/points")
                        .with(TestTokens.jwtWith(userId, Permissions.MANAGE_PROFILE, "SCOPE_write")))
                .andExpect(status().isForbidden());

        // Seed entries across previous and current America/Sao_Paulo month for deterministic totals and ordering
        LocalDateTime monthStartUtc = AppTime.monthStartUtc(Clock.systemUTC());

        PointLedgerEntry previousMonth = new PointLedgerEntry();
        previousMonth.setId(UUID.randomUUID().toString());
        previousMonth.setUserId(userId);
        previousMonth.setHouseId("house-a");
        previousMonth.setReason(PointReason.CHORE_ON_TIME);
        previousMonth.setAmount(20);
        previousMonth.setSourceType(PointSourceType.CHORE);
        previousMonth.setSourceId("chore-" + UUID.randomUUID());
        previousMonth.setCycleKey("prev-cycle");
        previousMonth.setDescription("Limpar sala");
        previousMonth.setCreatedAt(monthStartUtc.minusMinutes(1));
        pointLedgerRepository.save(previousMonth);

        PointLedgerEntry older = new PointLedgerEntry();
        older.setId(UUID.randomUUID().toString());
        older.setUserId(userId);
        older.setHouseId("house-a");
        older.setReason(PointReason.EXPENSE_ON_TIME);
        older.setAmount(10);
        older.setSourceType(PointSourceType.EXPENSE_SPLIT);
        older.setSourceId("split-" + UUID.randomUUID());
        older.setCycleKey("");
        older.setDescription("Conta de luz");
        older.setCreatedAt(monthStartUtc.plusMinutes(1));
        pointLedgerRepository.save(older);

        PointLedgerEntry newer = new PointLedgerEntry();
        newer.setId(UUID.randomUUID().toString());
        newer.setUserId(userId);
        newer.setHouseId("house-a");
        newer.setReason(PointReason.GROCERY_BOUGHT);
        newer.setAmount(5);
        newer.setSourceType(PointSourceType.GROCERY);
        newer.setSourceId("grocery-" + UUID.randomUUID());
        newer.setCycleKey("");
        newer.setDescription("Arroz");
        newer.setCreatedAt(monthStartUtc.plusMinutes(5));
        pointLedgerRepository.save(newer);

        // 200 with standard user JWT
        mockMvc.perform(get("/api/users/me/points")
                        .with(TestTokens.jwtForUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(35))
                .andExpect(jsonPath("$.thisMonth").value(15))
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.history[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.history[0].reason").value("GROCERY_BOUGHT"))
                .andExpect(jsonPath("$.history[0].amount").value(5))
                .andExpect(jsonPath("$.history[0].description").value("Arroz"))
                .andExpect(jsonPath("$.history[1].id").value(older.getId()))
                .andExpect(jsonPath("$.history[1].reason").value("EXPENSE_ON_TIME"))
                .andExpect(jsonPath("$.history[1].amount").value(10))
                .andExpect(jsonPath("$.history[2].id").value(previousMonth.getId()))
                .andExpect(jsonPath("$.history[2].reason").value("CHORE_ON_TIME"))
                .andExpect(jsonPath("$.history[2].amount").value(20));
    }

    @Test
    void creditIfAbsentShouldBeIdempotentAndDatabaseUniqueConstraintShouldRejectDuplicateOrigin() throws Exception {
        String userId = registerUser("Bruno Idempotente");
        String sourceId = "grocery-" + UUID.randomUUID();

        assertTrue(pointService.creditIfAbsent(
                userId, "house-b", PointReason.GROCERY_BOUGHT, 5,
                PointSourceType.GROCERY, sourceId, null, "Café"));

        // Second call with empty string cycleKey matches normalized null cycleKey -> returns false
        assertFalse(pointService.creditIfAbsent(
                userId, "house-b", PointReason.GROCERY_BOUGHT, 5,
                PointSourceType.GROCERY, sourceId, "", "Café duplicado"));

        assertEquals(1, pointLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId).size());

        // Real DB UNIQUE constraint uk_point_ledger_origin rejects direct duplicate insert
        PointLedgerEntry duplicate = new PointLedgerEntry();
        duplicate.setId(UUID.randomUUID().toString());
        duplicate.setUserId(userId);
        duplicate.setHouseId("house-b");
        duplicate.setReason(PointReason.GROCERY_BOUGHT);
        duplicate.setAmount(5);
        duplicate.setSourceType(PointSourceType.GROCERY);
        duplicate.setSourceId(sourceId);
        duplicate.setCycleKey("");
        duplicate.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));

        assertThrows(DataIntegrityViolationException.class,
                () -> pointLedgerRepository.saveAndFlush(duplicate));
    }

    @Test
    void removeByOriginShouldDeleteEntryWithoutInsertingNegativePoints() throws Exception {
        String userId = registerUser("Carla Desfazer");
        String sourceId = "split-" + UUID.randomUUID();

        assertTrue(pointService.creditIfAbsent(
                userId, "house-c", PointReason.EXPENSE_ON_TIME, 10,
                PointSourceType.EXPENSE_SPLIT, sourceId, null, "Aluguel"));

        assertEquals(10, pointService.getUserPointsSummary(userId).total());

        pointService.removeByOrigin(PointSourceType.EXPENSE_SPLIT, sourceId, null);

        PointsSummaryDto afterRemoval = pointService.getUserPointsSummary(userId);
        assertEquals(0, afterRemoval.total());
        assertEquals(0, afterRemoval.thisMonth());

        List<PointLedgerEntry> remaining = pointLedgerRepository.findByUserIdOrderByCreatedAtDesc(userId);
        assertTrue(remaining.isEmpty());
        assertTrue(remaining.stream().noneMatch(e -> e.getAmount() < 0));
    }

    @Test
    void monthlyTotalsShouldUseAmericaSaoPauloTimezoneAtUtcMonthBoundary() throws Exception {
        String userId = registerUser("Diego Fuso");
        String houseId = UUID.randomUUID().toString();

        // 2026-12-01T02:00:00Z == 2026-11-30T23:00:00-03:00 (still November in America/Sao_Paulo)
        Clock lateNovemberSaoPaulo = Clock.fixed(Instant.parse("2026-12-01T02:00:00Z"), ZoneOffset.UTC);
        PointService novService = new PointService(pointLedgerRepository, houseAccessValidator, lateNovemberSaoPaulo);

        assertTrue(novService.creditIfAbsent(
                userId, houseId, PointReason.CHORE_ON_TIME, 20,
                PointSourceType.CHORE, "chore-" + UUID.randomUUID(), "2026-W48", "Limpar cozinha"));

        PointsSummaryDto novemberSummary = novService.getUserPointsSummary(userId);
        assertEquals(20, novemberSummary.total());
        assertEquals(20, novemberSummary.thisMonth());
        assertEquals(20, novService.getHouseMonthlyPoints(houseId));

        // 2026-12-01T03:00:00Z == 2026-12-01T00:00:00-03:00 (December in America/Sao_Paulo)
        Clock startOfDecemberSaoPaulo = Clock.fixed(Instant.parse("2026-12-01T03:00:00Z"), ZoneOffset.UTC);
        PointService decService = new PointService(pointLedgerRepository, houseAccessValidator, startOfDecemberSaoPaulo);

        PointsSummaryDto decemberSummary = decService.getUserPointsSummary(userId);
        assertEquals(20, decemberSummary.total());
        assertEquals(0, decemberSummary.thisMonth());
        assertEquals(0, decService.getHouseMonthlyPoints(houseId));
    }
}
