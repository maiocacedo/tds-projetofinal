package com.nossoteto.model.enums;

public enum MatchStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    // Candidate gave up on their own request (CANCELLED is reserved for system/house actions).
    WITHDRAWN
}
