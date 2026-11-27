package com.nossoteto.repository;

import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface MatchRequestRepository extends JpaRepository<MatchRequest, String> {
    boolean existsByUserIdAndHouseIdAndStatus(String userId, String houseId, MatchStatus status);
    boolean existsByUserIdAndHouseIdAndStatusIn(String userId, String houseId, Collection<MatchStatus> statuses);
    List<MatchRequest> findByHouseIdAndStatusInOrderByCreatedAtAsc(String houseId, Collection<MatchStatus> statuses);
    List<MatchRequest> findByStatusAndMoveDeadlineLessThanEqual(MatchStatus status, LocalDateTime now);
    List<MatchRequest> findByHouseIdAndStatusOrderByCreatedAtAsc(String houseId, MatchStatus status);
    List<MatchRequest> findByUserIdAndStatus(String userId, MatchStatus status);
    List<MatchRequest> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Compare-and-set on the status: only moves a request that is still PENDING.
     * Returns 0 when another transaction already closed it (double approval, withdrawal race).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MatchRequest r SET r.status = :newStatus "
            + "WHERE r.id = :id AND r.status = com.nossoteto.model.enums.MatchStatus.PENDING")
    int transitionStatusFromPending(@Param("id") String id, @Param("newStatus") MatchStatus newStatus);

    /** Approval of a resident: status and deadline are written by the same compare-and-set. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MatchRequest m SET m.status = com.nossoteto.model.enums.MatchStatus.AWAITING_MOVE, "
            + "m.moveDeadline = :deadline "
            + "WHERE m.id = :id AND m.status = com.nossoteto.model.enums.MatchStatus.PENDING")
    int transitionPendingToAwaitingMove(@Param("id") String id, @Param("deadline") LocalDateTime deadline);

    /**
     * Expires one overdue reservation. Returns 1 only for the transaction that actually expired it,
     * so only that one gives the vacancy back (the job and a read racing never return +2).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MatchRequest m SET m.status = com.nossoteto.model.enums.MatchStatus.EXPIRED "
            + "WHERE m.id = :id AND m.status = com.nossoteto.model.enums.MatchStatus.AWAITING_MOVE "
            + "AND m.moveDeadline <= :now")
    int expireAwaitingMove(@Param("id") String id, @Param("now") LocalDateTime now);

    /** Compare-and-set out of AWAITING_MOVE (confirm, decline, retract, superseded). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MatchRequest m SET m.status = :newStatus "
            + "WHERE m.id = :id AND m.status = com.nossoteto.model.enums.MatchStatus.AWAITING_MOVE")
    int transitionAwaitingMoveTo(@Param("id") String id, @Param("newStatus") MatchStatus newStatus);
}
