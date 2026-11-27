package com.nossoteto.repository;

import com.nossoteto.model.HouseRemovalRequest;
import com.nossoteto.model.enums.RemovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HouseRemovalRequestRepository extends JpaRepository<HouseRemovalRequest, String> {

    boolean existsByHouseIdAndTargetUserIdAndStatus(String houseId, String targetUserId, RemovalStatus status);

    List<HouseRemovalRequest> findByHouseIdAndStatusOrderByCreatedAtAsc(String houseId, RemovalStatus status);

    Optional<HouseRemovalRequest> findByIdAndHouseId(String id, String houseId);

    @Query("SELECT r FROM HouseRemovalRequest r WHERE r.houseId = :houseId "
            + "AND r.status = com.nossoteto.model.enums.RemovalStatus.OPEN "
            + "AND (r.targetUserId = :userId OR r.proposedById = :userId)")
    List<HouseRemovalRequest> findOpenInvolvingUser(@Param("houseId") String houseId,
                                                    @Param("userId") String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE HouseRemovalRequest r SET r.status = :newStatus, r.resolvedAt = :resolvedAt "
            + "WHERE r.id = :id AND r.status = com.nossoteto.model.enums.RemovalStatus.OPEN")
    int transitionStatusFromOpen(@Param("id") String id,
                                 @Param("newStatus") RemovalStatus newStatus,
                                 @Param("resolvedAt") LocalDateTime resolvedAt);
}
