package com.nossoteto.repository;

import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRequestRepository extends JpaRepository<MatchRequest, String> {
    boolean existsByUserIdAndHouseIdAndStatus(String userId, String houseId, MatchStatus status);
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
}
