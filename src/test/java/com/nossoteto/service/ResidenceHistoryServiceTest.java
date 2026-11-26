package com.nossoteto.service;

import com.nossoteto.model.ResidenceHistory;
import com.nossoteto.repository.ResidenceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResidenceHistoryServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-26T18:00:00Z");
    private static final LocalDateTime NOW_UTC = LocalDateTime.of(2026, 11, 26, 18, 0, 0);

    @Mock
    private ResidenceHistoryRepository residenceHistoryRepository;

    private ResidenceHistoryService residenceHistoryService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        residenceHistoryService = new ResidenceHistoryService(residenceHistoryRepository, fixedClock);
    }

    @Test
    void recordEntryShouldInsertNewOpenEntryWhenUserHasNoOpenStay() {
        when(residenceHistoryRepository.findByUserIdAndLeftAtIsNull("user-1")).thenReturn(List.of());

        residenceHistoryService.recordEntry("user-1", "house-1");

        ArgumentCaptor<ResidenceHistory> captor = ArgumentCaptor.forClass(ResidenceHistory.class);
        verify(residenceHistoryRepository).save(captor.capture());
        verify(residenceHistoryRepository, never()).saveAll(anyList());

        ResidenceHistory saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals("user-1", saved.getUserId());
        assertEquals("house-1", saved.getHouseId());
        assertEquals(NOW_UTC, saved.getJoinedAt());
        assertNull(saved.getLeftAt());
    }

    @Test
    void recordEntryShouldBeNoOpWhenUserAlreadyHasOpenStayInSameHouse() {
        ResidenceHistory existing = stay("rh-1", "user-1", "house-1", NOW_UTC.minusDays(10), null);
        when(residenceHistoryRepository.findByUserIdAndLeftAtIsNull("user-1")).thenReturn(List.of(existing));

        residenceHistoryService.recordEntry("user-1", "house-1");

        assertNull(existing.getLeftAt());
        verify(residenceHistoryRepository, never()).save(any());
        verify(residenceHistoryRepository, never()).saveAll(anyList());
    }

    @Test
    void recordEntryShouldClosePreviousOpenStayInAnotherHouseAndInsertNewOne() {
        ResidenceHistory oldStay = stay("rh-1", "user-1", "house-old", NOW_UTC.minusDays(40), null);
        when(residenceHistoryRepository.findByUserIdAndLeftAtIsNull("user-1")).thenReturn(List.of(oldStay));

        residenceHistoryService.recordEntry("user-1", "house-new");

        assertEquals(NOW_UTC, oldStay.getLeftAt());
        verify(residenceHistoryRepository).saveAll(List.of(oldStay));

        ArgumentCaptor<ResidenceHistory> captor = ArgumentCaptor.forClass(ResidenceHistory.class);
        verify(residenceHistoryRepository).save(captor.capture());
        ResidenceHistory newStay = captor.getValue();
        assertEquals("user-1", newStay.getUserId());
        assertEquals("house-new", newStay.getHouseId());
        assertEquals(NOW_UTC, newStay.getJoinedAt());
        assertNull(newStay.getLeftAt());
    }

    @Test
    void recordExitShouldCloseOpenStayWithCurrentUtcTime() {
        ResidenceHistory openStay = stay("rh-1", "user-1", "house-1", NOW_UTC.minusDays(15), null);
        when(residenceHistoryRepository.findByUserIdAndLeftAtIsNull("user-1")).thenReturn(List.of(openStay));

        residenceHistoryService.recordExit("user-1", "house-1");

        assertEquals(NOW_UTC, openStay.getLeftAt());
        verify(residenceHistoryRepository).saveAll(List.of(openStay));
    }

    @Test
    void recordExitShouldBeNoOpWhenUserHasNoOpenStay() {
        when(residenceHistoryRepository.findByUserIdAndLeftAtIsNull("user-1")).thenReturn(List.of());

        residenceHistoryService.recordExit("user-1", "house-1");

        verify(residenceHistoryRepository, never()).saveAll(anyList());
        verify(residenceHistoryRepository, never()).save(any());
    }

    @Test
    void recordExitShouldNotCloseOpenStayInDifferentHouse() {
        ResidenceHistory openStay = stay("rh-1", "user-1", "house-1", NOW_UTC.minusDays(15), null);
        when(residenceHistoryRepository.findByUserIdAndLeftAtIsNull("user-1")).thenReturn(List.of(openStay));

        residenceHistoryService.recordExit("user-1", "other-house");

        assertNull(openStay.getLeftAt());
        verify(residenceHistoryRepository, never()).saveAll(anyList());
        verify(residenceHistoryRepository, never()).save(any());
    }

    @Test
    void recordExitShouldBeNoOpWhenHouseIdIsNull() {
        residenceHistoryService.recordExit("user-1", null);

        verifyNoInteractions(residenceHistoryRepository);
    }

    @Test
    void calculateTotalResidenceMonthsShouldReturnNullWhenUserHasNoHistory() {
        when(residenceHistoryRepository.findByUserId("user-1")).thenReturn(List.of());

        assertNull(residenceHistoryService.calculateTotalResidenceMonths("user-1"));
    }

    @Test
    void calculateTotalResidenceMonthsShouldReturnZeroWhenUserJustJoinedOrUnder30Days() {
        ResidenceHistory justJoined = stay("rh-1", "user-1", "house-1", NOW_UTC, null);
        when(residenceHistoryRepository.findByUserId("user-1")).thenReturn(List.of(justJoined));
        assertEquals(0, residenceHistoryService.calculateTotalResidenceMonths("user-1"));

        ResidenceHistory twentyNineDays = stay("rh-2", "user-1", "house-1", NOW_UTC.minusDays(29), null);
        when(residenceHistoryRepository.findByUserId("user-1")).thenReturn(List.of(twentyNineDays));
        assertEquals(0, residenceHistoryService.calculateTotalResidenceMonths("user-1"));
    }

    @Test
    void calculateTotalResidenceMonthsShouldFloorSumOfDaysAcrossMultipleHousesDividedBy30() {
        // 20 days closed in house-1 + 15 days open in house-2 = 35 days -> floor(35 / 30) = 1
        ResidenceHistory stay1 = stay("rh-1", "user-1", "house-1", NOW_UTC.minusDays(60), NOW_UTC.minusDays(40));
        ResidenceHistory stay2 = stay("rh-2", "user-1", "house-2", NOW_UTC.minusDays(15), null);
        // Future joinedAt (clock skew) should contribute 0 instead of negative days
        ResidenceHistory futureStay = stay("rh-3", "user-1", "house-3", NOW_UTC.plusDays(5), null);

        when(residenceHistoryRepository.findByUserId("user-1")).thenReturn(List.of(stay1, stay2, futureStay));

        assertEquals(1, residenceHistoryService.calculateTotalResidenceMonths("user-1"));

        // 45 days closed + 45 days open = 90 days -> 3 months
        ResidenceHistory longer1 = stay("rh-4", "user-2", "house-1", NOW_UTC.minusDays(100), NOW_UTC.minusDays(55));
        ResidenceHistory longer2 = stay("rh-5", "user-2", "house-2", NOW_UTC.minusDays(45), null);
        when(residenceHistoryRepository.findByUserId("user-2")).thenReturn(List.of(longer1, longer2));

        assertEquals(3, residenceHistoryService.calculateTotalResidenceMonths("user-2"));
    }

    private ResidenceHistory stay(String id, String userId, String houseId,
                                  LocalDateTime joinedAt, LocalDateTime leftAt) {
        ResidenceHistory entry = new ResidenceHistory();
        entry.setId(id);
        entry.setUserId(userId);
        entry.setHouseId(houseId);
        entry.setJoinedAt(joinedAt);
        entry.setLeftAt(leftAt);
        return entry;
    }
}
