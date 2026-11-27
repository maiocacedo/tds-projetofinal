package com.nossoteto.model;

import com.nossoteto.model.enums.PointReason;
import com.nossoteto.model.enums.PointSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "point_ledger",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_point_ledger_origin",
                columnNames = {"source_type", "source_id", "cycle_key"}
        )
)
public class PointLedgerEntry {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "house_id", length = 36)
    private String houseId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason", nullable = false, length = 40)
    private PointReason reason;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_type", nullable = false, length = 40)
    private PointSourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(name = "cycle_key", nullable = false, length = 40)
    private String cycleKey = "";

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PointLedgerEntry() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }

    public PointReason getReason() { return reason; }
    public void setReason(PointReason reason) { this.reason = reason; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public PointSourceType getSourceType() { return sourceType; }
    public void setSourceType(PointSourceType sourceType) { this.sourceType = sourceType; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getCycleKey() { return cycleKey; }
    public void setCycleKey(String cycleKey) { this.cycleKey = cycleKey == null ? "" : cycleKey; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
