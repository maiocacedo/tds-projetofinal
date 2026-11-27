package com.nossoteto.repository;

import com.nossoteto.model.House;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HouseRepository extends JpaRepository<House, String> {
    List<House> findByIsAcceptingMatchesTrue();

    /**
     * Atomically takes one vacancy. Returns 0 when the house has none left (or a concurrent
     * acceptance took the last one), so callers must treat 0 as a conflict.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE House h SET h.vacancies = h.vacancies - 1 WHERE h.id = :id AND h.vacancies > 0")
    int decrementVacancyIfAvailable(@Param("id") String id);
}
