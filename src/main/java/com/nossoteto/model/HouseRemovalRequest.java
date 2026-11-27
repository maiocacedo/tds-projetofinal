package com.nossoteto.model;

import com.nossoteto.model.enums.RemovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "house_removal_requests")
public class HouseRemovalRequest {

    @Id
    private String id;

    @Column(name = "house_id", nullable = false)
    private String houseId;

    @Column(name = "target_user_id", nullable = false)
    private String targetUserId;

    @Column(name = "proposed_by_id", nullable = false)
    private String proposedById;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false)
    private RemovalStatus status = RemovalStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public HouseRemovalRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }

    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }

    public String getProposedById() { return proposedById; }
    public void setProposedById(String proposedById) { this.proposedById = proposedById; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public RemovalStatus getStatus() { return status; }
    public void setStatus(RemovalStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
