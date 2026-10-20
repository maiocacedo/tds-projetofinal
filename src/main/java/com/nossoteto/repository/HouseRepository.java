package com.nossoteto.repository;

import com.nossoteto.model.House;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HouseRepository extends JpaRepository<House, String> {
    List<House> findByIsAcceptingMatchesTrue();
}
