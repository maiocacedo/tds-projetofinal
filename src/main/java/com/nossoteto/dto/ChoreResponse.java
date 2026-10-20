package com.nossoteto.dto;

import com.nossoteto.model.Chore;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.ChoreType;

import java.time.LocalDate;

public class ChoreResponse {

    private String id;
    private String houseId;
    private String description;
    private ChoreType choreType;
    private String currentAssigneeId;
    private ChoreStatus status;
    private LocalDate lastResetDate;

    public ChoreResponse() {}

    public static ChoreResponse fromEntity(Chore chore) {
        ChoreResponse response = new ChoreResponse();
        response.setId(chore.getId());
        response.setHouseId(chore.getHouseId());
        response.setDescription(chore.getDescription());
        response.setChoreType(chore.getChoreType());
        response.setCurrentAssigneeId(chore.getCurrentAssigneeId());
        response.setStatus(chore.getStatus());
        response.setLastResetDate(chore.getLastResetDate());
        return response;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ChoreType getChoreType() { return choreType; }
    public void setChoreType(ChoreType choreType) { this.choreType = choreType; }

    public String getCurrentAssigneeId() { return currentAssigneeId; }
    public void setCurrentAssigneeId(String currentAssigneeId) { this.currentAssigneeId = currentAssigneeId; }

    public ChoreStatus getStatus() { return status; }
    public void setStatus(ChoreStatus status) { this.status = status; }

    public LocalDate getLastResetDate() { return lastResetDate; }
    public void setLastResetDate(LocalDate lastResetDate) { this.lastResetDate = lastResetDate; }
}
