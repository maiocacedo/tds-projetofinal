package com.nossoteto.model.enums;

public enum MatchStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    // Candidate gave up on their own request (CANCELLED is reserved for system/house actions).
    WITHDRAWN,
    // Resident accepted by another house: the vacancy is reserved until moveDeadline (72 h).
    AWAITING_MOVE,
    // The AWAITING_MOVE reservation ran out without a confirmation; the vacancy was given back.
    EXPIRED
}
