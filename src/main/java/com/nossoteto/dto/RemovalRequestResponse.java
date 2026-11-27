package com.nossoteto.dto;

import com.nossoteto.model.enums.RemovalStatus;

import java.time.LocalDateTime;
import java.util.List;

public record RemovalRequestResponse(
        String id,
        String houseId,
        String targetUserId,
        String targetUserName,
        String proposedById,
        String proposedByName,
        String reason,
        RemovalStatus status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,
        List<VoterSummaryDto> voters,
        String myVote,
        int votesCast,
        int votersTotal) {}
