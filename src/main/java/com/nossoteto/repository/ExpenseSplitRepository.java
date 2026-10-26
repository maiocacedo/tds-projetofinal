package com.nossoteto.repository;

import com.nossoteto.model.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, String> {
    List<ExpenseSplit> findByExpenseId(String expenseId);
    Optional<ExpenseSplit> findByExpenseIdAndUserId(String expenseId, String userId);
    List<ExpenseSplit> findByUserIdAndIsPaidFalse(String userId);
}
