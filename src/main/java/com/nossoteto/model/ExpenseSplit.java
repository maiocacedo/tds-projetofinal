package com.nossoteto.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expense_splits")
public class ExpenseSplit {

    @Id
    private String id;

    @Column(name = "expense_id", nullable = false)
    private String expenseId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "amount_due", nullable = false)
    private BigDecimal amountDue;

    @Column(name = "proposed_amount_due")
    private BigDecimal proposedAmountDue;

    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid = false;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "received_confirmed_at")
    private LocalDateTime receivedConfirmedAt;

    @Column(name = "reopened_by_id")
    private String reopenedById;

    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExpenseId() { return expenseId; }
    public void setExpenseId(String expenseId) { this.expenseId = expenseId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public BigDecimal getAmountDue() { return amountDue; }
    public void setAmountDue(BigDecimal amountDue) { this.amountDue = amountDue; }

    public BigDecimal getProposedAmountDue() { return proposedAmountDue; }
    public void setProposedAmountDue(BigDecimal proposedAmountDue) { this.proposedAmountDue = proposedAmountDue; }

    public Boolean getIsPaid() { return isPaid; }
    public void setIsPaid(Boolean isPaid) { this.isPaid = isPaid; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getReceivedConfirmedAt() { return receivedConfirmedAt; }
    public void setReceivedConfirmedAt(LocalDateTime receivedConfirmedAt) { this.receivedConfirmedAt = receivedConfirmedAt; }

    public String getReopenedById() { return reopenedById; }
    public void setReopenedById(String reopenedById) { this.reopenedById = reopenedById; }

    public LocalDateTime getReopenedAt() { return reopenedAt; }
    public void setReopenedAt(LocalDateTime reopenedAt) { this.reopenedAt = reopenedAt; }
}
