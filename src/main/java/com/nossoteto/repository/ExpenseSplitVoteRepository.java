package com.nossoteto.repository;

import com.nossoteto.model.ExpenseSplitVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseSplitVoteRepository extends JpaRepository<ExpenseSplitVote, String> {
    Optional<ExpenseSplitVote> findByExpenseIdAndVoterId(String expenseId, String voterId);
    List<ExpenseSplitVote> findByExpenseId(String expenseId);
    void deleteByExpenseId(String expenseId);
}
