package com.nossoteto.dto;

import com.nossoteto.model.enums.PointReason;

import java.time.LocalDateTime;

public record PointHistoryItemDto(
        String id,
        PointReason reason,
        int amount,
        LocalDateTime createdAt,
        String description
) {}
