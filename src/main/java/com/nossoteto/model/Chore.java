package com.nossoteto.model;

import com.nossoteto.model.enums.ChoreFrequency;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.ChoreType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "chore_type", nullable = false)
    private ChoreType choreType;

    /** Owner of the slot in the rotation; a temporary swap never changes it. */
    @Column(name = "current_assignee_id", nullable = false)
    private String currentAssigneeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private ChoreStatus status = ChoreStatus.PENDING;

    @Column(name = "last_reset_date", nullable = false)
    private LocalDate lastResetDate;

    // The fields below are initialized on purpose: Hibernate would otherwise insert NULL and ignore the column DEFAULT.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "frequency", nullable = false)
    private ChoreFrequency frequency = ChoreFrequency.ONCE;

    @Column(name = "completions_this_cycle", nullable = false)
    private Integer completionsThisCycle = 0;

    @Column(name = "delayed_this_cycle", nullable = false)
    private Boolean delayedThisCycle = false;

    @Column(name = "linked_to_groceries", nullable = false)
    private Boolean linkedToGroceries = false;

    /** Comma-separated user ids in rotation order. */
    @Column(name = "rotation_order", columnDefinition = "TEXT")
    private String rotationOrder;

    @Column(name = "cycle_number", nullable = false)
    private Integer cycleNumber = 1;

    @Column(name = "weeks_in_cycle", nullable = false)
    private Integer weeksInCycle = 0;

    /** ISO week (e.g. 2026-W48) of the last close, so the Sunday job never closes the same week twice. */
    @Column(name = "last_closed_week", length = 10)
    private String lastClosedWeek;

    @Column(name = "reopened_by_id")
    private String reopenedById;

    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    /** Resident covering this chore through an accepted swap; valid only for the current cycle. */
    @Column(name = "temporary_assignee_id")
    private String temporaryAssigneeId;

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

    public ChoreFrequency getFrequency() { return frequency; }
    public void setFrequency(ChoreFrequency frequency) { this.frequency = frequency; }

    public Integer getCompletionsThisCycle() { return completionsThisCycle; }
    public void setCompletionsThisCycle(Integer completionsThisCycle) { this.completionsThisCycle = completionsThisCycle; }

    public Boolean getDelayedThisCycle() { return delayedThisCycle; }
    public void setDelayedThisCycle(Boolean delayedThisCycle) { this.delayedThisCycle = delayedThisCycle; }

    public Boolean getLinkedToGroceries() { return linkedToGroceries; }
    public void setLinkedToGroceries(Boolean linkedToGroceries) { this.linkedToGroceries = linkedToGroceries; }

    public String getRotationOrder() { return rotationOrder; }
    public void setRotationOrder(String rotationOrder) { this.rotationOrder = rotationOrder; }

    public List<String> rotationOrderIds() {
        if (rotationOrder == null || rotationOrder.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rotationOrder.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public void assignRotationOrderIds(List<String> ids) {
        this.rotationOrder = (ids == null || ids.isEmpty()) ? null : String.join(",", ids);
    }

    public Integer getCycleNumber() { return cycleNumber; }
    public void setCycleNumber(Integer cycleNumber) { this.cycleNumber = cycleNumber; }

    public Integer getWeeksInCycle() { return weeksInCycle; }
    public void setWeeksInCycle(Integer weeksInCycle) { this.weeksInCycle = weeksInCycle; }

    public String getLastClosedWeek() { return lastClosedWeek; }
    public void setLastClosedWeek(String lastClosedWeek) { this.lastClosedWeek = lastClosedWeek; }

    public String getReopenedById() { return reopenedById; }
    public void setReopenedById(String reopenedById) { this.reopenedById = reopenedById; }

    public LocalDateTime getReopenedAt() { return reopenedAt; }
    public void setReopenedAt(LocalDateTime reopenedAt) { this.reopenedAt = reopenedAt; }

    public String getTemporaryAssigneeId() { return temporaryAssigneeId; }
    public void setTemporaryAssigneeId(String temporaryAssigneeId) { this.temporaryAssigneeId = temporaryAssigneeId; }
}
