package com.nossoteto.repository;

import com.nossoteto.model.ChoreSwap;
import com.nossoteto.model.enums.ChoreSwapStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChoreSwapRepository extends JpaRepository<ChoreSwap, String> {

    List<ChoreSwap> findByHouseIdAndStatusOrderByCreatedAtAsc(String houseId, ChoreSwapStatus status);

    @Query("select s from ChoreSwap s where s.status = :status"
            + " and (s.requesterChoreId = :choreId or s.targetChoreId = :choreId)")
    List<ChoreSwap> findByStatusInvolvingChore(
            @Param("status") ChoreSwapStatus status,
            @Param("choreId") String choreId);
}
