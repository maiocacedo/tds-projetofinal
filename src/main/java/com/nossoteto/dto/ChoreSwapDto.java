package com.nossoteto.dto;

import com.nossoteto.model.enums.ChoreSwapStatus;

import java.time.LocalDateTime;

public record ChoreSwapDto(
        String id,
        String houseId,
        String requesterChoreId,
        String requesterChoreDescription,
        String targetChoreId,
        String targetChoreDescription,
        String requesterId,
        String requesterName,
        String targetUserId,
        String targetUserName,
        ChoreSwapStatus status,
        Integer cycleNumber,
        LocalDateTime createdAt) {
}
