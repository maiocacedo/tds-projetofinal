package com.nossoteto.dto;

import java.math.BigDecimal;

public class ExpenseSplitDto {

    private String id;
    private String userId;
    private BigDecimal amountDue;
    private BigDecimal proposedAmountDue;
    private Boolean isPaid;

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
}
