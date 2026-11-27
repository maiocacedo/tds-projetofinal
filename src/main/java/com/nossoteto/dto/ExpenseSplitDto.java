package com.nossoteto.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExpenseSplitDto {

    private String id;
    private String userId;
    private BigDecimal amountDue;
    private BigDecimal proposedAmountDue;
    private Boolean isPaid;
    private String userName;
    private LocalDateTime paidAt;
    private LocalDateTime receivedConfirmedAt;

    public ExpenseSplitDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public BigDecimal getAmountDue() { return amountDue; }
    public void setAmountDue(BigDecimal amountDue) { this.amountDue = amountDue; }

    public BigDecimal getProposedAmountDue() { return proposedAmountDue; }
    public void setProposedAmountDue(BigDecimal proposedAmountDue) { this.proposedAmountDue = proposedAmountDue; }

    public Boolean getIsPaid() { return isPaid; }
    public void setIsPaid(Boolean isPaid) { this.isPaid = isPaid; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getReceivedConfirmedAt() { return receivedConfirmedAt; }
    public void setReceivedConfirmedAt(LocalDateTime receivedConfirmedAt) { this.receivedConfirmedAt = receivedConfirmedAt; }
}
