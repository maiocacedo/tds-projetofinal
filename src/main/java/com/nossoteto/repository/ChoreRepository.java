package com.nossoteto.repository;

import com.nossoteto.model.Chore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChoreRepository extends JpaRepository<Chore, String> {
    List<Chore> findByHouseId(String houseId);
}
