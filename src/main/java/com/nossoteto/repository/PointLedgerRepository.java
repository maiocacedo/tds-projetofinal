package com.nossoteto.repository;

import com.nossoteto.model.PointLedgerEntry;
import com.nossoteto.model.enums.PointSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PointLedgerRepository extends JpaRepository<PointLedgerEntry, String> {

    boolean existsBySourceTypeAndSourceIdAndCycleKey(
            PointSourceType sourceType,
            String sourceId,
            String cycleKey);

    void deleteBySourceTypeAndSourceIdAndCycleKey(
            PointSourceType sourceType,
            String sourceId,
            String cycleKey);

    List<PointLedgerEntry> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("select coalesce(sum(p.amount), 0L) from PointLedgerEntry p where p.userId = :userId")
    long sumAmountByUserId(@Param("userId") String userId);

    @Query("select coalesce(sum(p.amount), 0L) from PointLedgerEntry p "
            + "where p.userId = :userId and p.createdAt >= :start and p.createdAt < :end")
    long sumAmountByUserIdAndCreatedAtRange(
            @Param("userId") String userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(p.amount), 0L) from PointLedgerEntry p "
            + "where p.houseId = :houseId and p.createdAt >= :start and p.createdAt < :end")
    long sumAmountByHouseIdAndCreatedAtRange(
            @Param("houseId") String houseId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
