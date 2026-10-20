package com.nossoteto.repository;

import com.nossoteto.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, String> {
    List<Expense> findByHouseIdOrderByDueDateAsc(String houseId);
}
