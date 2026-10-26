package com.nossoteto.service;

import com.nossoteto.model.enums.VotingOutcome;
import com.nossoteto.model.enums.VotingRule;
import org.springframework.stereotype.Component;

@Component
public class VotingEvaluator {

    public VotingOutcome evaluate(VotingRule rule, int totalMembers, int approveCount, int rejectCount) {
        if (totalMembers <= 0) {
            return VotingOutcome.PENDING;
        }

        VotingRule effectiveRule = rule != null ? rule : VotingRule.UNANIMITY;

        if (effectiveRule == VotingRule.UNANIMITY) {
            if (rejectCount >= 1) {
                return VotingOutcome.REJECTED;
            }
            if (approveCount >= totalMembers) {
                return VotingOutcome.APPROVED;
            }
            return VotingOutcome.PENDING;
        }

        // MAJORITY (> 50%)
        if (approveCount > totalMembers / 2) {
            return VotingOutcome.APPROVED;
        }
        if (rejectCount >= (totalMembers + 1) / 2) {
            return VotingOutcome.REJECTED;
        }
        return VotingOutcome.PENDING;
    }
}
