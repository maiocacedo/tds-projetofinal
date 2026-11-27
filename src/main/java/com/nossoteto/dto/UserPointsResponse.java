package com.nossoteto.dto;

import java.util.List;

public record UserPointsResponse(
        int total,
        int thisMonth,
        List<PointHistoryItemDto> history
) {}
