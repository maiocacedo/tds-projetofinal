package com.nossoteto.dto;

import java.math.BigDecimal;
import java.util.List;

public class DashboardResponse {

    private String houseId;
    private String houseName;
    private BigDecimal totalPendingAmountForUser;
    private List<ExpenseSplitDto> pendingSplitsForUser;
    private List<ChoreResponse> houseChores;
    private List<GroceryResponse> pendingGroceries;
    private int pendingMatchRequestsCount;

    public DashboardResponse() {}

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }

    public String getHouseName() { return houseName; }
    public void setHouseName(String houseName) { this.houseName = houseName; }

    public BigDecimal getTotalPendingAmountForUser() { return totalPendingAmountForUser; }
    public void setTotalPendingAmountForUser(BigDecimal totalPendingAmountForUser) { this.totalPendingAmountForUser = totalPendingAmountForUser; }

    public List<ExpenseSplitDto> getPendingSplitsForUser() { return pendingSplitsForUser; }
    public void setPendingSplitsForUser(List<ExpenseSplitDto> pendingSplitsForUser) { this.pendingSplitsForUser = pendingSplitsForUser; }

    public List<ChoreResponse> getHouseChores() { return houseChores; }
    public void setHouseChores(List<ChoreResponse> houseChores) { this.houseChores = houseChores; }

    public List<GroceryResponse> getPendingGroceries() { return pendingGroceries; }
    public void setPendingGroceries(List<GroceryResponse> pendingGroceries) { this.pendingGroceries = pendingGroceries; }

    public int getPendingMatchRequestsCount() { return pendingMatchRequestsCount; }
    public void setPendingMatchRequestsCount(int pendingMatchRequestsCount) { this.pendingMatchRequestsCount = pendingMatchRequestsCount; }
}
