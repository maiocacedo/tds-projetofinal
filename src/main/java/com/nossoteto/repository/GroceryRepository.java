package com.nossoteto.repository;

import com.nossoteto.model.Grocery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GroceryRepository extends JpaRepository<Grocery, String> {
    List<Grocery> findByHouseId(String houseId);
    List<Grocery> findByHouseIdAndIsPurchasedFalse(String houseId);

    @Query("SELECT g FROM Grocery g WHERE g.houseId = :houseId AND (g.isPurchased = false OR g.purchasedAt >= :cutoff)")
    List<Grocery> findVisibleForHouse(@Param("houseId") String houseId, @Param("cutoff") LocalDateTime cutoff);
}
