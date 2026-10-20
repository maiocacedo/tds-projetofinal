package com.nossoteto.model.enums;

import com.nossoteto.exception.BusinessRuleException;

public enum ChoreType {
    ROTATIONAL,
    FIXED;

    public static ChoreType fromString(String value) {
        if (value == null) {
            throw new BusinessRuleException("Chore type cannot be null");
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "ROTATIVA", "ROTATIONAL" -> ROTATIONAL;
            case "FIXA", "FIXED" -> FIXED;
            default -> throw new BusinessRuleException("Invalid chore type: " + value);
        };
    }
}
