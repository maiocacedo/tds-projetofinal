package com.nossoteto.repository;

import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchRequestRepository extends JpaRepository<MatchRequest, String> {
    boolean existsByUserIdAndHouseIdAndStatus(String userId, String houseId, MatchStatus status);
    List<MatchRequest> findByHouseIdAndStatusOrderByCreatedAtAsc(String houseId, MatchStatus status);
    List<MatchRequest> findByUserIdAndStatus(String userId, MatchStatus status);
}
