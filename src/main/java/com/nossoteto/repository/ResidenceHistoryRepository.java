package com.nossoteto.repository;

import com.nossoteto.model.ResidenceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResidenceHistoryRepository extends JpaRepository<ResidenceHistory, String> {
    List<ResidenceHistory> findByUserId(String userId);
    List<ResidenceHistory> findByUserIdAndLeftAtIsNull(String userId);
}
