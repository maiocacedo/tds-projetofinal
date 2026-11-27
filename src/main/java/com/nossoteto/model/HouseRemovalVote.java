package com.nossoteto.model;

import com.nossoteto.model.enums.VoteType;
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
@Table(name = "house_removal_votes")
public class HouseRemovalVote {

    @Id
    private String id;

    @Column(name = "removal_id", nullable = false)
    private String removalId;

    @Column(name = "voter_id", nullable = false)
    private String voterId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "vote", nullable = false)
    private VoteType vote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public HouseRemovalVote() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRemovalId() { return removalId; }
    public void setRemovalId(String removalId) { this.removalId = removalId; }

    public String getVoterId() { return voterId; }
    public void setVoterId(String voterId) { this.voterId = voterId; }

    public VoteType getVote() { return vote; }
    public void setVote(VoteType vote) { this.vote = vote; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
