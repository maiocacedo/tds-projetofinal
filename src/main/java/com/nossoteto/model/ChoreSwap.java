package com.nossoteto.model;

import com.nossoteto.model.enums.ChoreSwapStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** A proposal to trade two chores for the current cycle only; the rotation itself never changes. */
@Entity
@Table(name = "chore_swaps")
public class ChoreSwap {

    @Id
    private String id;

    @Column(name = "house_id", nullable = false)
    private String houseId;

    @Column(name = "requester_chore_id", nullable = false)
    private String requesterChoreId;

    @Column(name = "target_chore_id", nullable = false)
    private String targetChoreId;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;

    @Column(name = "target_user_id", nullable = false)
    private String targetUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private ChoreSwapStatus status = ChoreSwapStatus.PENDING;

    @Column(name = "cycle_number", nullable = false)
    private Integer cycleNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ChoreSwap() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }

    public String getRequesterChoreId() { return requesterChoreId; }
    public void setRequesterChoreId(String requesterChoreId) { this.requesterChoreId = requesterChoreId; }

    public String getTargetChoreId() { return targetChoreId; }
    public void setTargetChoreId(String targetChoreId) { this.targetChoreId = targetChoreId; }

    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }

    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }

    public ChoreSwapStatus getStatus() { return status; }
    public void setStatus(ChoreSwapStatus status) { this.status = status; }

    public Integer getCycleNumber() { return cycleNumber; }
    public void setCycleNumber(Integer cycleNumber) { this.cycleNumber = cycleNumber; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
