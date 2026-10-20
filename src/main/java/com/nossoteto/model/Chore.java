package com.nossoteto.model;

import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.ChoreType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "chores")
public class Chore {

    @Id
    private String id;

    @Column(name = "house_id", nullable = false)
    private String houseId;

    @Column(name = "description", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "chore_type", nullable = false)
    private ChoreType choreType;

    @Column(name = "current_assignee_id", nullable = false)
    private String currentAssigneeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChoreStatus status = ChoreStatus.PENDING;

    @Column(name = "last_reset_date", nullable = false)
    private LocalDate lastResetDate;

    public Chore() {}

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
