package com.nossoteto.repository;

import com.nossoteto.model.Grocery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroceryRepository extends JpaRepository<Grocery, String> {
    List<Grocery> findByHouseId(String houseId);
    List<Grocery> findByHouseIdAndIsPurchasedFalse(String houseId);
}
