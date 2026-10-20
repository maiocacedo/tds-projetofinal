package com.nossoteto.service;

import com.nossoteto.dto.CreateExpenseRequest;
import com.nossoteto.dto.ExpenseResponse;
import com.nossoteto.dto.ExpenseSplitDto;
import com.nossoteto.dto.ProposeSplitRequest;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Expense;
import com.nossoteto.model.ExpenseSplit;
import com.nossoteto.model.ExpenseSplitVote;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.SplitType;
import com.nossoteto.model.enums.VoteType;
import com.nossoteto.model.enums.VotingOutcome;
import com.nossoteto.model.enums.VotingRule;
import com.nossoteto.repository.ExpenseRepository;
import com.nossoteto.repository.ExpenseSplitRepository;
import com.nossoteto.repository.ExpenseSplitVoteRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseSplitRepository expenseSplitRepository;

    @Mock
    private ExpenseSplitVoteRepository expenseSplitVoteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HouseRepository houseRepository;

    @Mock
    private HouseAccessValidator houseAccessValidator;

    @Mock
    private VotingEvaluator votingEvaluator;

    @TempDir
    Path tempDir;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(
                expenseRepository,
                expenseSplitRepository,
                expenseSplitVoteRepository,
                userRepository,
                houseRepository,
                houseAccessValidator,
                votingEvaluator
        );
        ReflectionTestUtils.setField(expenseService, "uploadDir", tempDir.toString());
    }

    private User createMember(String id, String houseId) {
        User u = new User();
        u.setId(id);
        u.setHouseId(houseId);
        return u;
    }

    @Test
    void createExpenseShouldSplitEquallyWithCentAdjustmentAndSaveAttachment() {
        String houseId = "house-1";
        List<User> members = List.of(
                createMember("u1", houseId),
                createMember("u2", houseId),
                createMember("u3", houseId)
        );

        when(userRepository.findByHouseIdOrderByIdAsc(houseId)).thenReturn(members);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expenseSplitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        byte[] rawContent = "PDF_CONTENT_123".getBytes(StandardCharsets.UTF_8);
        String base64 = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(rawContent);

        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setTitle("Conta de Luz");
        request.setTotalAmount(new BigDecimal("100.00"));
        request.setDueDate(LocalDate.of(2026, 11, 10));
        request.setAttachmentBase64(base64);

        ExpenseResponse response = expenseService.createExpense("u1", houseId, request);

        assertNotNull(response.getId());
        assertEquals("Conta de Luz", response.getTitle());
        assertEquals(new BigDecimal("100.00"), response.getTotalAmount());
        assertTrue(response.isHasAttachment());
        assertEquals(SplitType.EQUAL, response.getSplitType());
        assertEquals(3, response.getSplits().size());

        // 100.00 / 3 = 33.34 + 33.33 + 33.33 = 100.00
        assertEquals(new BigDecimal("33.34"), response.getSplits().get(0).getAmountDue());
        assertEquals(new BigDecimal("33.33"), response.getSplits().get(1).getAmountDue());
        assertEquals(new BigDecimal("33.33"), response.getSplits().get(2).getAmountDue());
        assertFalse(response.getSplits().get(0).getIsPaid());

        ArgumentCaptor<Expense> expenseCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(expenseCaptor.capture());
        Expense savedExpense = expenseCaptor.getValue();

        when(expenseRepository.findById(savedExpense.getId())).thenReturn(Optional.of(savedExpense));
        byte[] readBack = expenseService.getAttachment("u2", houseId, savedExpense.getId());
        assertArrayEquals(rawContent, readBack);
    }

    @Test
    void getAttachmentShouldThrowForbiddenWhenUserIsNotHouseMember() {
        doThrow(new ForbiddenOperationException("User does not belong to this house"))
                .when(houseAccessValidator).requireHouseMember("intruder", "house-1");

        assertThrows(ForbiddenOperationException.class,
                () -> expenseService.getAttachment("intruder", "house-1", "exp-1"));
    }

    @Test
    void proposeCustomSplitByValueShouldRejectWhenSumDiffersFromTotal() {
        String houseId = "house-1";
        Expense expense = new Expense();
        expense.setId("exp-1");
        expense.setHouseId(houseId);
        expense.setTotalAmount(new BigDecimal("200.00"));
        expense.setSplitType(SplitType.EQUAL);

        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(expense));

        ProposeSplitRequest request = new ProposeSplitRequest();
        ProposeSplitRequest.SplitItem item1 = new ProposeSplitRequest.SplitItem();
        item1.setUserId("u1");
        item1.setAmountOrPercentage(new BigDecimal("100.00"));
        ProposeSplitRequest.SplitItem item2 = new ProposeSplitRequest.SplitItem();
        item2.setUserId("u2");
        item2.setAmountOrPercentage(new BigDecimal("50.00"));
        request.setSplits(List.of(item1, item2));

        assertThrows(BusinessRuleException.class,
                () -> expenseService.proposeCustomSplit("u1", houseId, "exp-1", request));
    }

    @Test
    void proposeAndVoteCustomSplitShouldApplyAmountsWhenApproved() {
        String houseId = "house-1";
        Expense expense = new Expense();
        expense.setId("exp-1");
        expense.setHouseId(houseId);
        expense.setTitle("Internet");
        expense.setTotalAmount(new BigDecimal("200.00"));
        expense.setDueDate(LocalDate.of(2026, 11, 15));
        expense.setSplitType(SplitType.EQUAL);

        ExpenseSplit split1 = new ExpenseSplit();
        split1.setId("s1");
        split1.setExpenseId("exp-1");
        split1.setUserId("u1");
        split1.setAmountDue(new BigDecimal("100.00"));

        ExpenseSplit split2 = new ExpenseSplit();
        split2.setId("s2");
        split2.setExpenseId("exp-1");
        split2.setUserId("u2");
        split2.setAmountDue(new BigDecimal("100.00"));

        List<ExpenseSplit> splits = new ArrayList<>(List.of(split1, split2));

        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(expense));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(splits);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expenseSplitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        // Propose 60% / 40%
        ProposeSplitRequest proposeReq = new ProposeSplitRequest();
        proposeReq.setSplitMode("PORCENTAGEM");
        ProposeSplitRequest.SplitItem i1 = new ProposeSplitRequest.SplitItem();
        i1.setUserId("u1");
        i1.setAmountOrPercentage(new BigDecimal("60"));
        ProposeSplitRequest.SplitItem i2 = new ProposeSplitRequest.SplitItem();
        i2.setUserId("u2");
        i2.setAmountOrPercentage(new BigDecimal("40"));
        proposeReq.setSplits(List.of(i1, i2));

        ExpenseResponse proposedResponse = expenseService.proposeCustomSplit("u1", houseId, "exp-1", proposeReq);
        assertEquals(SplitType.CUSTOM_PENDING, proposedResponse.getSplitType());
        assertEquals(new BigDecimal("120.00"), split1.getProposedAmountDue());
        assertEquals(new BigDecimal("80.00"), split2.getProposedAmountDue());
        verify(expenseSplitVoteRepository).deleteByExpenseId("exp-1");

        // Now vote and approve
        House house = new House();
        house.setId(houseId);
        house.setVotingRule(VotingRule.UNANIMITY);

        ExpenseSplitVote v1 = new ExpenseSplitVote();
        v1.setExpenseId("exp-1");
        v1.setVoterId("u1");
        v1.setVote(VoteType.APPROVE);

        ExpenseSplitVote v2 = new ExpenseSplitVote();
        v2.setExpenseId("exp-1");
        v2.setVoterId("u2");
        v2.setVote(VoteType.APPROVE);

        when(expenseSplitVoteRepository.findByExpenseIdAndVoterId("exp-1", "u2")).thenReturn(Optional.empty());
        when(expenseSplitVoteRepository.findByExpenseId("exp-1")).thenReturn(List.of(v1, v2));
        when(houseRepository.findById(houseId)).thenReturn(Optional.of(house));
        when(userRepository.countByHouseId(houseId)).thenReturn(2L);
        when(votingEvaluator.evaluate(VotingRule.UNANIMITY, 2, 2, 0)).thenReturn(VotingOutcome.APPROVED);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APROVAR");

        ExpenseResponse votedResponse = expenseService.voteOnCustomSplit("u2", houseId, "exp-1", voteDto);
        assertEquals(SplitType.CUSTOM_APPROVED, votedResponse.getSplitType());
        assertEquals(new BigDecimal("120.00"), split1.getAmountDue());
        assertEquals(new BigDecimal("80.00"), split2.getAmountDue());
        assertNull(split1.getProposedAmountDue());
        assertNull(split2.getProposedAmountDue());
    }

    @Test
    void markSplitAsPaidShouldUpdateUserSplit() {
        String houseId = "house-1";
        Expense expense = new Expense();
        expense.setId("exp-1");
        expense.setHouseId(houseId);
        expense.setTitle("Agua");
        expense.setTotalAmount(new BigDecimal("90.00"));
        expense.setDueDate(LocalDate.of(2026, 11, 20));
        expense.setSplitType(SplitType.EQUAL);

        ExpenseSplit split1 = new ExpenseSplit();
        split1.setId("s1");
        split1.setExpenseId("exp-1");
        split1.setUserId("u1");
        split1.setAmountDue(new BigDecimal("45.00"));
        split1.setIsPaid(false);

        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(expense));
        when(expenseSplitRepository.findByExpenseIdAndUserId("exp-1", "u1")).thenReturn(Optional.of(split1));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(split1));

        ExpenseResponse response = expenseService.markSplitAsPaid("u1", houseId, "exp-1");

        assertTrue(split1.getIsPaid());
        ExpenseSplitDto dto = response.getSplits().get(0);
        assertTrue(dto.getIsPaid());
        verify(expenseSplitRepository).save(split1);
    }

    @Test
    void markSplitAsPaidShouldThrowNotFoundWhenUserHasNoSplit() {
        String houseId = "house-1";
        Expense expense = new Expense();
        expense.setId("exp-1");
        expense.setHouseId(houseId);

        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(expense));
        when(expenseSplitRepository.findByExpenseIdAndUserId("exp-1", "u1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> expenseService.markSplitAsPaid("u1", houseId, "exp-1"));
    }
}
