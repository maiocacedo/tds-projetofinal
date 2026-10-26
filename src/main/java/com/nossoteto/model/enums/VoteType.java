package com.nossoteto.model.enums;

import com.nossoteto.exception.BusinessRuleException;

public enum VoteType {
    APPROVE,
    REJECT;

    public static VoteType fromString(String value) {
        if (value == null) {
            throw new BusinessRuleException("Vote cannot be null");
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "APROVAR", "APPROVE" -> APPROVE;
            case "REJEITAR", "REJECT" -> REJECT;
            default -> throw new BusinessRuleException("Invalid vote type: " + value);
        };
    }
}
