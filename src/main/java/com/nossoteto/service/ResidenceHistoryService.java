package com.nossoteto.service;

import com.nossoteto.model.ResidenceHistory;
import com.nossoteto.repository.ResidenceHistoryRepository;
import com.nossoteto.util.AppTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ResidenceHistoryService {

    private final ResidenceHistoryRepository residenceHistoryRepository;
    private final Clock clock;

    public ResidenceHistoryService(ResidenceHistoryRepository residenceHistoryRepository, Clock clock) {
        this.residenceHistoryRepository = residenceHistoryRepository;
        this.clock = clock;
    }

    @Transactional
    public void recordEntry(String userId, String houseId) {
        LocalDateTime now = AppTime.nowUtc(clock);
        List<ResidenceHistory> openEntries = residenceHistoryRepository.findByUserIdAndLeftAtIsNull(userId);

        boolean alreadyOpenInSameHouse = false;
        List<ResidenceHistory> toClose = new ArrayList<>();
        for (ResidenceHistory entry : openEntries) {
            if (houseId.equals(entry.getHouseId())) {
                alreadyOpenInSameHouse = true;
            } else {
                entry.setLeftAt(now);
                toClose.add(entry);
            }
        }

        if (!toClose.isEmpty()) {
            residenceHistoryRepository.saveAll(toClose);
        }
        if (alreadyOpenInSameHouse) {
            return;
        }

        ResidenceHistory newEntry = new ResidenceHistory();
        newEntry.setId(UUID.randomUUID().toString());
        newEntry.setUserId(userId);
        newEntry.setHouseId(houseId);
        newEntry.setJoinedAt(now);
        newEntry.setLeftAt(null);
        residenceHistoryRepository.save(newEntry);
    }

    @Transactional
    public void recordExit(String userId, String houseId) {
        if (houseId == null) {
            return;
        }

        List<ResidenceHistory> openEntries = residenceHistoryRepository.findByUserIdAndLeftAtIsNull(userId);
        List<ResidenceHistory> toClose = new ArrayList<>();
        for (ResidenceHistory entry : openEntries) {
            if (houseId.equals(entry.getHouseId())) {
                toClose.add(entry);
            }
        }
        if (toClose.isEmpty()) {
            return;
        }

        LocalDateTime now = AppTime.nowUtc(clock);
        for (ResidenceHistory entry : toClose) {
            entry.setLeftAt(now);
        }
        residenceHistoryRepository.saveAll(toClose);
    }

    @Transactional(readOnly = true)
    public Integer calculateTotalResidenceMonths(String userId) {
        List<ResidenceHistory> entries = residenceHistoryRepository.findByUserId(userId);
        if (entries.isEmpty()) {
            return null;
        }

        LocalDateTime now = AppTime.nowUtc(clock);
        long totalDays = 0L;
        for (ResidenceHistory entry : entries) {
            LocalDateTime end = entry.getLeftAt() != null ? entry.getLeftAt() : now;
            totalDays += Math.max(0L, ChronoUnit.DAYS.between(entry.getJoinedAt(), end));
        }
        return (int) (totalDays / 30L);
    }
}
