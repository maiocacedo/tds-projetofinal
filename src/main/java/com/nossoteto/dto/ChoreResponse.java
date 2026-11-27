package com.nossoteto.dto;

import com.nossoteto.model.Chore;
import com.nossoteto.model.enums.ChoreFrequency;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.ChoreType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ChoreResponse {

    private String id;
    private String houseId;
    private String description;
    private ChoreType choreType;
    /** Owner of the rotation slot (a swap does not change it). */
    private String currentAssigneeId;
    private String currentAssigneeName;
    /** Who has to do the chore this cycle: the temporary assignee from an accepted swap, else the owner. */
    private String effectiveAssigneeId;
    private String effectiveAssigneeName;
    private String temporaryAssigneeId;
    private String temporaryAssigneeName;
    private String nextAssigneeId;
    private String nextAssigneeName;
    private ChoreStatus status;
    private LocalDate lastResetDate;
    private ChoreFrequency frequency;
    private int requiredCompletions;
    private int completionsThisCycle;
    private boolean delayedThisCycle;
    private boolean linkedToGroceries;
    private List<String> rotationOrder = new ArrayList<>();
    private int cycleNumber;
    private String reopenedById;
    private String reopenedByName;
    private LocalDateTime reopenedAt;
    private List<ChoreSwapDto> pendingSwaps = new ArrayList<>();

    public ChoreResponse() {}

    /** Raw view of the stored row; {@code ChoreService} fills the derived fields (effective, next, names, swaps). */
    public static ChoreResponse fromEntity(Chore chore) {
        ChoreResponse response = new ChoreResponse();
        response.setId(chore.getId());
        response.setHouseId(chore.getHouseId());
        response.setDescription(chore.getDescription());
        response.setChoreType(chore.getChoreType());
        response.setCurrentAssigneeId(chore.getCurrentAssigneeId());
        response.setEffectiveAssigneeId(chore.getTemporaryAssigneeId() != null
                ? chore.getTemporaryAssigneeId() : chore.getCurrentAssigneeId());
        response.setTemporaryAssigneeId(chore.getTemporaryAssigneeId());
        response.setStatus(chore.getStatus());
        response.setLastResetDate(chore.getLastResetDate());
        ChoreFrequency frequency = chore.getFrequency() != null ? chore.getFrequency() : ChoreFrequency.ONCE;
        response.setFrequency(frequency);
        response.setRequiredCompletions(frequency.requiredCompletions());
        response.setCompletionsThisCycle(chore.getCompletionsThisCycle() != null ? chore.getCompletionsThisCycle() : 0);
        response.setDelayedThisCycle(Boolean.TRUE.equals(chore.getDelayedThisCycle()));
        response.setLinkedToGroceries(Boolean.TRUE.equals(chore.getLinkedToGroceries()));
        response.setRotationOrder(new ArrayList<>(chore.rotationOrderIds()));
        response.setCycleNumber(chore.getCycleNumber() != null ? chore.getCycleNumber() : 1);
        response.setReopenedById(chore.getReopenedById());
        response.setReopenedAt(chore.getReopenedAt());
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

    public String getCurrentAssigneeName() { return currentAssigneeName; }
    public void setCurrentAssigneeName(String currentAssigneeName) { this.currentAssigneeName = currentAssigneeName; }

    public String getEffectiveAssigneeId() { return effectiveAssigneeId; }
    public void setEffectiveAssigneeId(String effectiveAssigneeId) { this.effectiveAssigneeId = effectiveAssigneeId; }

    public String getEffectiveAssigneeName() { return effectiveAssigneeName; }
    public void setEffectiveAssigneeName(String effectiveAssigneeName) { this.effectiveAssigneeName = effectiveAssigneeName; }

    public String getTemporaryAssigneeId() { return temporaryAssigneeId; }
    public void setTemporaryAssigneeId(String temporaryAssigneeId) { this.temporaryAssigneeId = temporaryAssigneeId; }

    public String getTemporaryAssigneeName() { return temporaryAssigneeName; }
    public void setTemporaryAssigneeName(String temporaryAssigneeName) { this.temporaryAssigneeName = temporaryAssigneeName; }

    public String getNextAssigneeId() { return nextAssigneeId; }
    public void setNextAssigneeId(String nextAssigneeId) { this.nextAssigneeId = nextAssigneeId; }

    public String getNextAssigneeName() { return nextAssigneeName; }
    public void setNextAssigneeName(String nextAssigneeName) { this.nextAssigneeName = nextAssigneeName; }

    public ChoreStatus getStatus() { return status; }
    public void setStatus(ChoreStatus status) { this.status = status; }

    public LocalDate getLastResetDate() { return lastResetDate; }
    public void setLastResetDate(LocalDate lastResetDate) { this.lastResetDate = lastResetDate; }

    public ChoreFrequency getFrequency() { return frequency; }
    public void setFrequency(ChoreFrequency frequency) { this.frequency = frequency; }

    public int getRequiredCompletions() { return requiredCompletions; }
    public void setRequiredCompletions(int requiredCompletions) { this.requiredCompletions = requiredCompletions; }

    public int getCompletionsThisCycle() { return completionsThisCycle; }
    public void setCompletionsThisCycle(int completionsThisCycle) { this.completionsThisCycle = completionsThisCycle; }

    public boolean isDelayedThisCycle() { return delayedThisCycle; }
    public void setDelayedThisCycle(boolean delayedThisCycle) { this.delayedThisCycle = delayedThisCycle; }

    public boolean isLinkedToGroceries() { return linkedToGroceries; }
    public void setLinkedToGroceries(boolean linkedToGroceries) { this.linkedToGroceries = linkedToGroceries; }

    public List<String> getRotationOrder() { return rotationOrder; }
    public void setRotationOrder(List<String> rotationOrder) { this.rotationOrder = rotationOrder; }

    public int getCycleNumber() { return cycleNumber; }
    public void setCycleNumber(int cycleNumber) { this.cycleNumber = cycleNumber; }

    public String getReopenedById() { return reopenedById; }
    public void setReopenedById(String reopenedById) { this.reopenedById = reopenedById; }

    public String getReopenedByName() { return reopenedByName; }
    public void setReopenedByName(String reopenedByName) { this.reopenedByName = reopenedByName; }

    public LocalDateTime getReopenedAt() { return reopenedAt; }
    public void setReopenedAt(LocalDateTime reopenedAt) { this.reopenedAt = reopenedAt; }

    public List<ChoreSwapDto> getPendingSwaps() { return pendingSwaps; }
    public void setPendingSwaps(List<ChoreSwapDto> pendingSwaps) { this.pendingSwaps = pendingSwaps; }

    /** The spec's singular field: the first pending swap involving this chore, or {@code null}. */
    public ChoreSwapDto getPendingSwap() {
        return pendingSwaps == null || pendingSwaps.isEmpty() ? null : pendingSwaps.get(0);
    }
}
