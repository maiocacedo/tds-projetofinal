package com.nossoteto.model;

import com.nossoteto.model.enums.VoteType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "expense_split_votes")
public class ExpenseSplitVote {

    @Id
    private String id;

    @Column(name = "expense_id", nullable = false)
    private String expenseId;

    @Column(name = "voter_id", nullable = false)
    private String voterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote", nullable = false)
    private VoteType vote;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExpenseId() { return expenseId; }
    public void setExpenseId(String expenseId) { this.expenseId = expenseId; }

    public String getVoterId() { return voterId; }
    public void setVoterId(String voterId) { this.voterId = voterId; }

    public VoteType getVote() { return vote; }
    public void setVote(VoteType vote) { this.vote = vote; }
}
