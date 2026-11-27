package com.nossoteto.dto;

import com.nossoteto.model.enums.MatchStatus;

import java.time.LocalDateTime;

/**
 * One of the caller's own match requests ("Minhas solicitações").
 * {@code votesCast} and {@code votersTotal} only count the house's current residents.
 * {@code moveDeadline} is always null until the move-in flow (B6) exists.
 */
public record MyMatchRequestDto(String id,
                                String houseId,
                                String houseName,
                                MatchStatus status,
                                LocalDateTime createdAt,
                                int votesCast,
                                int votersTotal,
                                LocalDateTime moveDeadline) {}
