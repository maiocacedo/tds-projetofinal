package com.nossoteto.service;

import com.nossoteto.dto.PointsSummaryDto;
import com.nossoteto.dto.UserPointsResponse;
import com.nossoteto.exception.ConflictException;
import com.nossoteto.exception.GlobalExceptionHandler;
import com.nossoteto.model.PointLedgerEntry;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.model.enums.PointSourceType;
import com.nossoteto.repository.PointLedgerRepository;
import com.nossoteto.util.AppTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private PointLedgerRepository pointLedgerRepository;

    @Mock
    private HouseAccessValidator houseAccessValidator;

    private Clock fixedClock;
    private PointService pointService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-12-01T02:00:00Z"), ZoneOffset.UTC);
        pointService = new PointService(pointLedgerRepository, houseAccessValidator, fixedClock);
    }

    @Test
    void creditIfAbsentShouldSaveNewEntryAndNormalizeNullCycleKey() {
        when(pointLedgerRepository.existsBySourceTypeAndSourceIdAndCycleKey(
                PointSourceType.GROCERY, "grocery-1", "")).thenReturn(false);

        boolean credited = pointService.creditIfAbsent(
                "user-1",
                "house-1",
                PointReason.GROCERY_BOUGHT,
                5,
                PointSourceType.GROCERY,
                "grocery-1",
                null,
                "Comprou café");

        assertTrue(credited);
        ArgumentCaptor<PointLedgerEntry> captor = ArgumentCaptor.forClass(PointLedgerEntry.class);
        verify(pointLedgerRepository).save(captor.capture());

        PointLedgerEntry saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals("user-1", saved.getUserId());
        assertEquals("house-1", saved.getHouseId());
        assertEquals(PointReason.GROCERY_BOUGHT, saved.getReason());
        assertEquals(5, saved.getAmount());
        assertEquals(PointSourceType.GROCERY, saved.getSourceType());
        assertEquals("grocery-1", saved.getSourceId());
        assertEquals("", saved.getCycleKey());
        assertEquals("Comprou café", saved.getDescription());
        assertEquals(LocalDateTime.of(2026, 12, 1, 2, 0, 0), saved.getCreatedAt());
    }

    @Test
    void creditIfAbsentShouldReturnFalseWhenOriginAlreadyExists() {
        when(pointLedgerRepository.existsBySourceTypeAndSourceIdAndCycleKey(
                PointSourceType.CHORE, "chore-1", "2026-W48")).thenReturn(true);

        boolean credited = pointService.creditIfAbsent(
                "user-1",
                "house-1",
                PointReason.CHORE_ON_TIME,
                20,
                PointSourceType.CHORE,
                "chore-1",
                "2026-W48",
                "Lavar louça");

        assertFalse(credited);
        verify(pointLedgerRepository, never()).save(any());
    }

    @Test
    void creditIfAbsentShouldRejectZeroAndNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> pointService.creditIfAbsent(
                "user-1", "house-1", PointReason.GROCERY_BOUGHT, 0,
                PointSourceType.GROCERY, "g1", null, "Zero"));

        assertThrows(IllegalArgumentException.class, () -> pointService.creditIfAbsent(
                "user-1", "house-1", PointReason.GROCERY_BOUGHT, -5,
                PointSourceType.GROCERY, "g2", "", "Negativo"));

        verifyNoInteractions(pointLedgerRepository);
    }

    @Test
    void removeByOriginShouldNormalizeNullCycleKeyAndDeleteWithoutNegativeEntry() {
        pointService.removeByOrigin(PointSourceType.EXPENSE_SPLIT, "split-1", null);

        verify(pointLedgerRepository).deleteBySourceTypeAndSourceIdAndCycleKey(
                PointSourceType.EXPENSE_SPLIT, "split-1", "");
        verify(pointLedgerRepository, never()).save(any());
    }

    @Test
    void getUserPointsSummaryShouldUseSaoPauloMonthBoundsForUtcCreditAtDec1st0200Z() {
        // 2026-12-01T02:00:00Z is 2026-11-30T23:00:00 in America/Sao_Paulo (still November)
        assertEquals(LocalDate.of(2026, 11, 30), AppTime.today(fixedClock));
        assertEquals(AppTime.APP_ZONE, AppTime.nowInAppZone(fixedClock).getZone());

        LocalDateTime expectedStartUtc = LocalDateTime.of(2026, 11, 1, 3, 0, 0);
        LocalDateTime expectedEndUtc = LocalDateTime.of(2026, 12, 1, 3, 0, 0);

        when(pointLedgerRepository.sumAmountByUserId("user-1")).thenReturn(55L);
        when(pointLedgerRepository.sumAmountByUserIdAndCreatedAtRange(
                "user-1", expectedStartUtc, expectedEndUtc)).thenReturn(25L);

        PointsSummaryDto summary = pointService.getUserPointsSummary("user-1");

        assertEquals(55, summary.total());
        assertEquals(25, summary.thisMonth());
    }

    @Test
    void getUserPointsShouldValidateUserAndReturnOrderedHistory() {
        User user = new User();
        user.setId("user-1");
        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(user);

        LocalDateTime expectedStartUtc = LocalDateTime.of(2026, 11, 1, 3, 0, 0);
        LocalDateTime expectedEndUtc = LocalDateTime.of(2026, 12, 1, 3, 0, 0);

        PointLedgerEntry newer = new PointLedgerEntry();
        newer.setId("p-2");
        newer.setUserId("user-1");
        newer.setReason(PointReason.GROCERY_BOUGHT);
        newer.setAmount(5);
        newer.setSourceType(PointSourceType.GROCERY);
        newer.setSourceId("g-1");
        newer.setDescription("Leite");
        newer.setCreatedAt(LocalDateTime.of(2026, 12, 1, 2, 0, 0));

        PointLedgerEntry older = new PointLedgerEntry();
        older.setId("p-1");
        older.setUserId("user-1");
        older.setReason(PointReason.EXPENSE_ON_TIME);
        older.setAmount(10);
        older.setSourceType(PointSourceType.EXPENSE_SPLIT);
        older.setSourceId("s-1");
        older.setDescription("Internet");
        older.setCreatedAt(LocalDateTime.of(2026, 11, 15, 14, 0, 0));

        when(pointLedgerRepository.sumAmountByUserId("user-1")).thenReturn(15L);
        when(pointLedgerRepository.sumAmountByUserIdAndCreatedAtRange(
                "user-1", expectedStartUtc, expectedEndUtc)).thenReturn(15L);
        when(pointLedgerRepository.findByUserIdOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(newer, older));

        UserPointsResponse response = pointService.getUserPoints("user-1");

        assertEquals(15, response.total());
        assertEquals(15, response.thisMonth());
        assertEquals(2, response.history().size());
        assertEquals("p-2", response.history().get(0).id());
        assertEquals(PointReason.GROCERY_BOUGHT, response.history().get(0).reason());
        assertEquals(5, response.history().get(0).amount());
        assertEquals(LocalDateTime.of(2026, 12, 1, 2, 0, 0), response.history().get(0).createdAt());
        assertEquals("Leite", response.history().get(0).description());
        assertEquals("p-1", response.history().get(1).id());
    }

    @Test
    void getHouseMonthlyPointsShouldSumCurrentSaoPauloMonthAndHandleBlankHouse() {
        LocalDateTime expectedStartUtc = LocalDateTime.of(2026, 11, 1, 3, 0, 0);
        LocalDateTime expectedEndUtc = LocalDateTime.of(2026, 12, 1, 3, 0, 0);

        when(pointLedgerRepository.sumAmountByHouseIdAndCreatedAtRange(
                "house-1", expectedStartUtc, expectedEndUtc)).thenReturn(70L);

        assertEquals(70, pointService.getHouseMonthlyPoints("house-1"));
        assertEquals(0, pointService.getHouseMonthlyPoints(null));
        assertEquals(0, pointService.getHouseMonthlyPoints("  "));
    }

    @Test
    void globalExceptionHandlerShouldReturn409ForConflictException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<Map<String, String>> response =
                handler.handleConflict(new ConflictException("house has no available vacancies"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(Map.of("error", "house has no available vacancies"), response.getBody());

        ResponseEntity<Map<String, String>> fallback =
                handler.handleConflict(new ConflictException(null));
        assertEquals(HttpStatus.CONFLICT, fallback.getStatusCode());
        assertEquals(Map.of("error", "Conflict"), fallback.getBody());
    }
}
