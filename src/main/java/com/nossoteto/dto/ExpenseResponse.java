package com.nossoteto.dto;

import com.nossoteto.model.enums.SplitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ExpenseResponse {

    private String id;
    private String houseId;
    private String title;
    private BigDecimal totalAmount;
    private LocalDate dueDate;
    private boolean hasAttachment;
    private SplitType splitType;
    private List<ExpenseSplitDto> splits;

    public ExpenseResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public boolean isHasAttachment() { return hasAttachment; }
    public void setHasAttachment(boolean hasAttachment) { this.hasAttachment = hasAttachment; }

    public SplitType getSplitType() { return splitType; }
    public void setSplitType(SplitType splitType) { this.splitType = splitType; }

    public List<ExpenseSplitDto> getSplits() { return splits; }
    public void setSplits(List<ExpenseSplitDto> splits) { this.splits = splits; }
}
