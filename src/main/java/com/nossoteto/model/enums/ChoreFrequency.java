package com.nossoteto.model.enums;

import com.nossoteto.exception.BusinessRuleException;

/**
 * How many completions a chore needs to close its cycle. Every frequency rotates normally;
 * {@link #BIWEEKLY} is the only one whose cycle spans two weeks.
 */
public enum ChoreFrequency {
    ONCE(1),
    TWICE(2),
    THRICE(3),
    DAILY(7),
    BIWEEKLY(1);

    private final int requiredCompletions;

    ChoreFrequency(int requiredCompletions) {
        this.requiredCompletions = requiredCompletions;
    }

    public int requiredCompletions() {
        return requiredCompletions;
    }

    public static ChoreFrequency fromString(String value) {
        if (value == null || value.isBlank()) {
            return ONCE;
        }
        try {
            return ChoreFrequency.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("invalid chore frequency");
        }
    }
}
