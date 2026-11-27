package com.nossoteto.repository;

import com.nossoteto.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, String> {
    List<Expense> findByHouseIdOrderByDueDateAsc(String houseId);

    @Query("SELECT e FROM Expense e WHERE e.recurrenceActive = true AND e.recurringDay IS NOT NULL AND e.recurrenceSourceId = e.id ORDER BY e.id ASC")
    List<Expense> findActiveRecurrenceTemplates();

    boolean existsByRecurrenceSourceIdAndRecurrenceMonth(String recurrenceSourceId, String recurrenceMonth);
}
