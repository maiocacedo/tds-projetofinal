package com.nossoteto.service;

import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.model.enums.ChoreType;
import com.nossoteto.model.enums.VoteType;
import com.nossoteto.model.enums.VotingOutcome;
import com.nossoteto.model.enums.VotingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VotingEvaluatorTest {

    private VotingEvaluator votingEvaluator;

    @BeforeEach
    void setUp() {
        votingEvaluator = new VotingEvaluator();
    }

    @Test
    void unanimityShouldApproveOnlyWhenAllMembersApprove() {
        assertEquals(VotingOutcome.APPROVED, votingEvaluator.evaluate(VotingRule.UNANIMITY, 3, 3, 0));
        assertEquals(VotingOutcome.APPROVED, votingEvaluator.evaluate(VotingRule.UNANIMITY, 1, 1, 0));
    }

    @Test
    void unanimityShouldRejectImmediatelyOnSingleRejectVote() {
        assertEquals(VotingOutcome.REJECTED, votingEvaluator.evaluate(VotingRule.UNANIMITY, 3, 0, 1));
        assertEquals(VotingOutcome.REJECTED, votingEvaluator.evaluate(VotingRule.UNANIMITY, 3, 2, 1));
    }

    @Test
    void unanimityShouldRemainPendingWhenVotesAreMissingAndNoRejections() {
        assertEquals(VotingOutcome.PENDING, votingEvaluator.evaluate(VotingRule.UNANIMITY, 3, 2, 0));
        assertEquals(VotingOutcome.PENDING, votingEvaluator.evaluate(VotingRule.UNANIMITY, 3, 0, 0));
        assertEquals(VotingOutcome.PENDING, votingEvaluator.evaluate(VotingRule.UNANIMITY, 0, 0, 0));
    }

    @Test
    void majorityShouldApproveWhenMoreThanHalfApprove() {
        assertEquals(VotingOutcome.APPROVED, votingEvaluator.evaluate(VotingRule.MAJORITY, 3, 2, 0));
        assertEquals(VotingOutcome.APPROVED, votingEvaluator.evaluate(VotingRule.MAJORITY, 4, 3, 0));
        assertEquals(VotingOutcome.APPROVED, votingEvaluator.evaluate(VotingRule.MAJORITY, 2, 2, 0));
        assertEquals(VotingOutcome.APPROVED, votingEvaluator.evaluate(VotingRule.MAJORITY, 1, 1, 0));
    }

    @Test
    void majorityShouldRejectWhenMajorityBecomesImpossible() {
        assertEquals(VotingOutcome.REJECTED, votingEvaluator.evaluate(VotingRule.MAJORITY, 3, 1, 2));
        assertEquals(VotingOutcome.REJECTED, votingEvaluator.evaluate(VotingRule.MAJORITY, 4, 1, 2));
        assertEquals(VotingOutcome.REJECTED, votingEvaluator.evaluate(VotingRule.MAJORITY, 2, 1, 1));
        assertEquals(VotingOutcome.REJECTED, votingEvaluator.evaluate(VotingRule.MAJORITY, 1, 0, 1));
    }

    @Test
    void majorityShouldRemainPendingWhenOutcomeStillUndecided() {
        assertEquals(VotingOutcome.PENDING, votingEvaluator.evaluate(VotingRule.MAJORITY, 3, 1, 0));
        assertEquals(VotingOutcome.PENDING, votingEvaluator.evaluate(VotingRule.MAJORITY, 3, 1, 1));
        assertEquals(VotingOutcome.PENDING, votingEvaluator.evaluate(VotingRule.MAJORITY, 4, 2, 1));
    }

    @Test
    void voteTypeFromStringShouldAcceptPortugueseAndEnglish() {
        assertEquals(VoteType.APPROVE, VoteType.fromString("APROVAR"));
        assertEquals(VoteType.APPROVE, VoteType.fromString("approve"));
        assertEquals(VoteType.REJECT, VoteType.fromString("REJEITAR"));
        assertEquals(VoteType.REJECT, VoteType.fromString("reject"));
        assertThrows(BusinessRuleException.class, () -> VoteType.fromString("INVALID"));
        assertThrows(BusinessRuleException.class, () -> VoteType.fromString(null));
    }

    @Test
    void choreTypeFromStringShouldAcceptPortugueseAndEnglish() {
        assertEquals(ChoreType.ROTATIONAL, ChoreType.fromString("ROTATIVA"));
        assertEquals(ChoreType.ROTATIONAL, ChoreType.fromString("rotational"));
        assertEquals(ChoreType.FIXED, ChoreType.fromString("FIXA"));
        assertEquals(ChoreType.FIXED, ChoreType.fromString("fixed"));
        assertThrows(BusinessRuleException.class, () -> ChoreType.fromString("INVALID"));
        assertThrows(BusinessRuleException.class, () -> ChoreType.fromString(null));
    }
}
