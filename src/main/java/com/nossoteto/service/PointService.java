package com.nossoteto.service;

import com.nossoteto.dto.PointHistoryItemDto;
import com.nossoteto.dto.PointsSummaryDto;
import com.nossoteto.dto.UserPointsResponse;
import com.nossoteto.model.PointLedgerEntry;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.model.enums.PointSourceType;
import com.nossoteto.repository.PointLedgerRepository;
import com.nossoteto.util.AppTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PointService {

    private final PointLedgerRepository pointLedgerRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final Clock clock;

    public PointService(
            PointLedgerRepository pointLedgerRepository,
            HouseAccessValidator houseAccessValidator,
            Clock clock) {
        this.pointLedgerRepository = pointLedgerRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.clock = clock;
    }

    @Transactional
    public boolean creditIfAbsent(
            String userId,
            String houseId,
            PointReason reason,
            int amount,
            PointSourceType sourceType,
            String sourceId,
            String cycleKey,
            String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Point amount must be positive");
        }
        String normalizedCycleKey = normalizeCycleKey(cycleKey);
        if (pointLedgerRepository.existsBySourceTypeAndSourceIdAndCycleKey(
                sourceType, sourceId, normalizedCycleKey)) {
            return false;
        }

        PointLedgerEntry entry = new PointLedgerEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setUserId(userId);
        entry.setHouseId(houseId);
        entry.setReason(reason);
        entry.setAmount(amount);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setCycleKey(normalizedCycleKey);
        entry.setDescription(description);
        entry.setCreatedAt(AppTime.nowUtc(clock));
        pointLedgerRepository.save(entry);
        return true;
    }

    @Transactional
    public void removeByOrigin(PointSourceType sourceType, String sourceId, String cycleKey) {
        pointLedgerRepository.deleteBySourceTypeAndSourceIdAndCycleKey(
                sourceType, sourceId, normalizeCycleKey(cycleKey));
    }

    @Transactional(readOnly = true)
    public PointsSummaryDto getUserPointsSummary(String userId) {
        LocalDateTime start = AppTime.monthStartUtc(clock);
        LocalDateTime end = AppTime.nextMonthStartUtc(clock);
        int total = Math.toIntExact(pointLedgerRepository.sumAmountByUserId(userId));
        int thisMonth = Math.toIntExact(
                pointLedgerRepository.sumAmountByUserIdAndCreatedAtRange(userId, start, end));
        return new PointsSummaryDto(total, thisMonth);
    }

    @Transactional(readOnly = true)
    public UserPointsResponse getUserPoints(String userId) {
        houseAccessValidator.requireAuthenticatedUser(userId);
        PointsSummaryDto summary = getUserPointsSummary(userId);
        List<PointHistoryItemDto> history = pointLedgerRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(entry -> new PointHistoryItemDto(
                        entry.getId(),
                        entry.getReason(),
                        entry.getAmount(),
                        entry.getCreatedAt(),
                        entry.getDescription()))
                .toList();
        return new UserPointsResponse(summary.total(), summary.thisMonth(), history);
    }

    @Transactional(readOnly = true)
    public int getHouseMonthlyPoints(String houseId) {
        if (houseId == null || houseId.isBlank()) {
            return 0;
        }
        LocalDateTime start = AppTime.monthStartUtc(clock);
        LocalDateTime end = AppTime.nextMonthStartUtc(clock);
        return Math.toIntExact(
                pointLedgerRepository.sumAmountByHouseIdAndCreatedAtRange(houseId, start, end));
    }

    private String normalizeCycleKey(String cycleKey) {
        return cycleKey == null ? "" : cycleKey;
    }
}
