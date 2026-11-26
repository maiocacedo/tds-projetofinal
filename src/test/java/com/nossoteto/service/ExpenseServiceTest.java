package com.nossoteto.service;

import com.nossoteto.dto.CreateExpenseRequest;
import com.nossoteto.dto.ExpenseResponse;
import com.nossoteto.dto.ExpenseSplitDto;
import com.nossoteto.dto.ExpenseSummaryResponse;
import com.nossoteto.dto.UpdateExpenseRequest;
import com.nossoteto.dto.ProposeSplitRequest;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ConflictException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Expense;
import com.nossoteto.model.ExpenseSplit;
import com.nossoteto.model.ExpenseSplitVote;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.model.enums.PointSourceType;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    @Mock
    private PointService pointService;

    @TempDir
    Path tempDir;

    private ExpenseService expenseService;

    // 2026-11-15 09:00 in America/Sao_Paulo
    private static final Instant DEFAULT_NOW = Instant.parse("2026-11-15T12:00:00Z");

    @BeforeEach
    void setUp() {
        buildService(Clock.fixed(DEFAULT_NOW, ZoneOffset.UTC));
    }

    private void buildService(Clock clock) {
        expenseService = new ExpenseService(
                expenseRepository,
                expenseSplitRepository,
                expenseSplitVoteRepository,
                userRepository,
                houseRepository,
                houseAccessValidator,
                votingEvaluator,
                pointService,
                clock
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
        // u1 is the caller and therefore the default payer: its own share is born paid and confirmed
        assertTrue(response.getSplits().get(0).getIsPaid());
        assertNotNull(response.getSplits().get(0).getPaidAt());
        assertNotNull(response.getSplits().get(0).getReceivedConfirmedAt());
        assertFalse(response.getSplits().get(1).getIsPaid());
        assertFalse(response.getSplits().get(2).getIsPaid());
        assertEquals("u1", response.getPaidBy().id());
        assertEquals("u1", response.getCreatedById());
        assertFalse(response.getRecurrenceActive());
        verify(pointService, never()).creditIfAbsent(any(), any(), any(), anyInt(), any(), any(), any(), any());

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

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.proposeCustomSplit("u1", houseId, "exp-1", request));
        assertEquals("split must add up to the total", ex.getMessage());
    }

    @Test
    void proposeCustomSplitShouldRejectDuplicatedUserIdEvenWhenSumMatchesTotal() {
        String houseId = "house-1";
        Expense expense = new Expense();
        expense.setId("exp-1");
        expense.setHouseId(houseId);
        expense.setTotalAmount(new BigDecimal("100.00"));
        expense.setSplitType(SplitType.EQUAL);

        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(expense));

        ProposeSplitRequest request = new ProposeSplitRequest();
        request.setSplitMode("VALOR");
        ProposeSplitRequest.SplitItem item1 = new ProposeSplitRequest.SplitItem();
        item1.setUserId("u1");
        item1.setAmountOrPercentage(new BigDecimal("50.00"));
        ProposeSplitRequest.SplitItem item2 = new ProposeSplitRequest.SplitItem();
        item2.setUserId("u1");
        item2.setAmountOrPercentage(new BigDecimal("50.00"));
        request.setSplits(List.of(item1, item2));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.proposeCustomSplit("u1", houseId, "exp-1", request));
        assertTrue(ex.getMessage().contains("Duplicate userId"));
        verify(expenseSplitRepository, never()).saveAll(any());
    }

    @Test
    void voteOnCustomSplitShouldIgnoreVotesFromFormerResidents() {
        String houseId = "house-1";
        Expense expense = new Expense();
        expense.setId("exp-1");
        expense.setHouseId(houseId);
        expense.setTotalAmount(new BigDecimal("100.00"));
        expense.setSplitType(SplitType.CUSTOM_PENDING);

        House house = new House();
        house.setId(houseId);
        house.setVotingRule(VotingRule.UNANIMITY);

        ExpenseSplitVote currentVote = new ExpenseSplitVote();
        currentVote.setVoterId("u1");
        currentVote.setVote(VoteType.APPROVE);

        ExpenseSplitVote formerResidentVote = new ExpenseSplitVote();
        formerResidentVote.setVoterId("former");
        formerResidentVote.setVote(VoteType.APPROVE);

        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(expense));
        when(expenseSplitVoteRepository.findByExpenseIdAndVoterId("exp-1", "u1")).thenReturn(Optional.empty());
        when(houseRepository.findById(houseId)).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc(houseId))
                .thenReturn(List.of(createMember("u1", houseId), createMember("u2", houseId)));
        when(expenseSplitVoteRepository.findByExpenseId("exp-1")).thenReturn(List.of(currentVote, formerResidentVote));
        when(votingEvaluator.evaluate(VotingRule.UNANIMITY, 2, 1, 0)).thenReturn(VotingOutcome.PENDING);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APROVAR");

        ExpenseResponse response = expenseService.voteOnCustomSplit("u1", houseId, "exp-1", voteDto);

        assertEquals(SplitType.CUSTOM_PENDING, response.getSplitType());
        verify(votingEvaluator).evaluate(VotingRule.UNANIMITY, 2, 1, 0);
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
        when(userRepository.findByHouseIdOrderByIdAsc(houseId))
                .thenReturn(List.of(createMember("u1", houseId), createMember("u2", houseId)));
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
        expense.setPaidById("u2");

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
        assertEquals(LocalDateTime.of(2026, 11, 15, 12, 0), split1.getPaidAt());
        ExpenseSplitDto dto = response.getSplits().get(0);
        assertTrue(dto.getIsPaid());
        verify(expenseSplitRepository).save(split1);
        verify(pointService).creditIfAbsent("u1", houseId, PointReason.EXPENSE_ON_TIME, 10,
                PointSourceType.EXPENSE_SPLIT, "s1", "", "Agua");
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

    // ---------------------------------------------------------------- B8a helpers

    private User namedMember(String id, String houseId, String name) {
        User u = createMember(id, houseId);
        u.setName(name);
        return u;
    }

    private Expense expense(String id, String houseId, String createdById, String paidById, String total) {
        Expense e = new Expense();
        e.setId(id);
        e.setHouseId(houseId);
        e.setTitle("Luz");
        e.setTotalAmount(new BigDecimal(total));
        e.setDueDate(LocalDate.of(2026, 11, 20));
        e.setSplitType(SplitType.EQUAL);
        e.setCreatedById(createdById);
        e.setPaidById(paidById);
        return e;
    }

    private ExpenseSplit split(String id, String expenseId, String userId, String amount, boolean paid) {
        ExpenseSplit s = new ExpenseSplit();
        s.setId(id);
        s.setExpenseId(expenseId);
        s.setUserId(userId);
        s.setAmountDue(new BigDecimal(amount));
        s.setIsPaid(paid);
        if (paid) {
            s.setPaidAt(LocalDateTime.of(2026, 11, 10, 10, 0));
        }
        return s;
    }

    private CreateExpenseRequest createRequest() {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setTitle("Internet");
        request.setTotalAmount(new BigDecimal("100.00"));
        request.setDueDate(LocalDate.of(2026, 11, 30));
        return request;
    }

    // ---------------------------------------------------------------- create

    @Test
    void createExpenseWithAnotherPayerShouldMarkOnlyThePayerShareAsPaid() {
        String houseId = "house-1";
        when(userRepository.findByHouseIdOrderByIdAsc(houseId))
                .thenReturn(List.of(createMember("u1", houseId), createMember("u2", houseId)));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expenseSplitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateExpenseRequest request = createRequest();
        request.setPaidById("u2");

        ExpenseResponse response = expenseService.createExpense("u1", houseId, request);

        assertEquals("u2", response.getPaidBy().id());
        assertEquals("u1", response.getCreatedById());
        ExpenseSplitDto u1Split = response.getSplits().stream().filter(s -> s.getUserId().equals("u1")).findFirst().orElseThrow();
        ExpenseSplitDto u2Split = response.getSplits().stream().filter(s -> s.getUserId().equals("u2")).findFirst().orElseThrow();
        assertFalse(u1Split.getIsPaid());
        assertNull(u1Split.getPaidAt());
        assertTrue(u2Split.getIsPaid());
        assertEquals(LocalDateTime.of(2026, 11, 15, 12, 0), u2Split.getPaidAt());
        assertEquals(LocalDateTime.of(2026, 11, 15, 12, 0), u2Split.getReceivedConfirmedAt());
        verify(pointService, never()).creditIfAbsent(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void createExpenseShouldRejectPayerWhoIsNotACurrentResident() {
        String houseId = "house-1";
        when(userRepository.findByHouseIdOrderByIdAsc(houseId))
                .thenReturn(List.of(createMember("u1", houseId), createMember("u2", houseId)));

        CreateExpenseRequest request = createRequest();
        request.setPaidById("outsider");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense("u1", houseId, request));
        assertEquals("payer must be a current house member", ex.getMessage());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createExpenseShouldRejectRecurringDayOutsideOneToThirtyOne() {
        CreateExpenseRequest zero = createRequest();
        zero.setRecurringDay(0);
        CreateExpenseRequest thirtyTwo = createRequest();
        thirtyTwo.setRecurringDay(32);

        BusinessRuleException low = assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense("u1", "house-1", zero));
        BusinessRuleException high = assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense("u1", "house-1", thirtyTwo));
        assertEquals("recurringDay must be between 1 and 31", low.getMessage());
        assertEquals("recurringDay must be between 1 and 31", high.getMessage());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createExpenseWithActiveRecurrenceShouldStampSourceAndCurrentMonth() {
        String houseId = "house-1";
        when(userRepository.findByHouseIdOrderByIdAsc(houseId)).thenReturn(List.of(createMember("u1", houseId)));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expenseSplitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateExpenseRequest request = createRequest();
        request.setRecurringDay(31);
        request.setRecurrenceActive(true);

        ExpenseResponse response = expenseService.createExpense("u1", houseId, request);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        Expense saved = captor.getValue();
        assertEquals(saved.getId(), saved.getRecurrenceSourceId());
        assertEquals("2026-11", saved.getRecurrenceMonth());
        assertEquals(31, saved.getRecurringDay());
        assertTrue(saved.getRecurrenceActive());
        assertEquals(31, response.getRecurringDay());
        assertTrue(response.getRecurrenceActive());
    }

    @Test
    void createExpenseWithRecurringDayButInactiveShouldNotStampRecurrenceSource() {
        String houseId = "house-1";
        when(userRepository.findByHouseIdOrderByIdAsc(houseId)).thenReturn(List.of(createMember("u1", houseId)));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expenseSplitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateExpenseRequest request = createRequest();
        request.setRecurringDay(5);
        request.setRecurrenceActive(null);

        expenseService.createExpense("u1", houseId, request);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        assertNull(captor.getValue().getRecurrenceSourceId());
        assertNull(captor.getValue().getRecurrenceMonth());
        assertFalse(captor.getValue().getRecurrenceActive());
    }

    @Test
    void listExpensesShouldExposePayerAndSplitUserNames() {
        String houseId = "house-1";
        Expense e = expense("exp-1", houseId, "u1", "u1", "100.00");
        when(expenseRepository.findByHouseIdOrderByDueDateAsc(houseId)).thenReturn(List.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(
                split("s1", "exp-1", "u1", "50.00", true),
                split("s2", "exp-1", "u2", "50.00", false)));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                namedMember("u1", houseId, "Ana"), namedMember("u2", houseId, "Bruno")));

        ExpenseResponse response = expenseService.listExpenses("u1", houseId).get(0);

        assertEquals("u1", response.getPaidBy().id());
        assertEquals("Ana", response.getPaidBy().name());
        assertEquals("Ana", response.getSplits().get(0).getUserName());
        assertEquals("Bruno", response.getSplits().get(1).getUserName());
        assertNotNull(response.getSplits().get(0).getPaidAt());
    }

    // ---------------------------------------------------------------- pay

    @Test
    void payAtLateEveningOfDueDateInSaoPauloShouldStillCreditTenPoints() {
        // 2026-11-21T02:30Z == 2026-11-20 23:30 in America/Sao_Paulo (the due date)
        buildService(Clock.fixed(Instant.parse("2026-11-21T02:30:00Z"), ZoneOffset.UTC));
        Expense e = expense("exp-1", "house-1", "u2", "u2", "90.00");
        ExpenseSplit s1 = split("s1", "exp-1", "u1", "45.00", false);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseIdAndUserId("exp-1", "u1")).thenReturn(Optional.of(s1));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s1));

        expenseService.markSplitAsPaid("u1", "house-1", "exp-1");

        verify(pointService).creditIfAbsent("u1", "house-1", PointReason.EXPENSE_ON_TIME, 10,
                PointSourceType.EXPENSE_SPLIT, "s1", "", "Luz");
    }

    @Test
    void payAfterDueDateInSaoPauloShouldNotCreditPoints() {
        // 2026-11-21T03:00:01Z == 2026-11-21 00:00:01 in America/Sao_Paulo (one day late)
        buildService(Clock.fixed(Instant.parse("2026-11-21T03:00:01Z"), ZoneOffset.UTC));
        Expense e = expense("exp-1", "house-1", "u2", "u2", "90.00");
        ExpenseSplit s1 = split("s1", "exp-1", "u1", "45.00", false);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseIdAndUserId("exp-1", "u1")).thenReturn(Optional.of(s1));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s1));

        expenseService.markSplitAsPaid("u1", "house-1", "exp-1");

        assertTrue(s1.getIsPaid());
        assertNotNull(s1.getPaidAt());
        verify(pointService, never()).creditIfAbsent(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void payAlreadyPaidSplitShouldKeepPaidAtAndNotCreditAgain() {
        Expense e = expense("exp-1", "house-1", "u2", "u2", "90.00");
        ExpenseSplit s1 = split("s1", "exp-1", "u1", "45.00", true);
        LocalDateTime originalPaidAt = s1.getPaidAt();
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseIdAndUserId("exp-1", "u1")).thenReturn(Optional.of(s1));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s1));

        expenseService.markSplitAsPaid("u1", "house-1", "exp-1");

        assertEquals(originalPaidAt, s1.getPaidAt());
        verify(expenseSplitRepository, never()).save(any());
        verify(pointService, never()).creditIfAbsent(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void payerPayingOwnShareShouldNotEarnPoints() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        ExpenseSplit s1 = split("s1", "exp-1", "u1", "45.00", false);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseIdAndUserId("exp-1", "u1")).thenReturn(Optional.of(s1));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s1));

        expenseService.markSplitAsPaid("u1", "house-1", "exp-1");

        assertTrue(s1.getIsPaid());
        verify(pointService, never()).creditIfAbsent(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------- confirm / not received

    @Test
    void confirmReceivedShouldOnlyBeAllowedToThePayer() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "45.00", true);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findById("s2")).thenReturn(Optional.of(s2));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> expenseService.confirmReceived("u2", "house-1", "exp-1", "s2"));
        assertEquals("only the payer can confirm payment", ex.getMessage());
        assertNull(s2.getReceivedConfirmedAt());
    }

    @Test
    void confirmReceivedOnLegacyExpenseWithoutPayerShouldBeForbidden() {
        Expense e = expense("exp-1", "house-1", null, null, "90.00");
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "45.00", true);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findById("s2")).thenReturn(Optional.of(s2));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> expenseService.confirmReceived("u1", "house-1", "exp-1", "s2"));
        assertEquals("only the payer can confirm payment", ex.getMessage());
    }

    @Test
    void confirmReceivedShouldRejectSplitThatIsNotPaid() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "45.00", false);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findById("s2")).thenReturn(Optional.of(s2));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.confirmReceived("u1", "house-1", "exp-1", "s2"));
        assertEquals("split is not marked as paid", ex.getMessage());
    }

    @Test
    void confirmReceivedShouldRejectSplitFromAnotherExpense() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        ExpenseSplit foreign = split("s9", "exp-OTHER", "u2", "45.00", true);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findById("s9")).thenReturn(Optional.of(foreign));

        assertThrows(ResourceNotFoundException.class,
                () -> expenseService.confirmReceived("u1", "house-1", "exp-1", "s9"));
    }

    @Test
    void confirmReceivedShouldStampConfirmationTime() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "45.00", true);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findById("s2")).thenReturn(Optional.of(s2));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s2));

        ExpenseResponse response = expenseService.confirmReceived("u1", "house-1", "exp-1", "s2");

        assertEquals(LocalDateTime.of(2026, 11, 15, 12, 0), s2.getReceivedConfirmedAt());
        assertEquals(LocalDateTime.of(2026, 11, 15, 12, 0), response.getSplits().get(0).getReceivedConfirmedAt());
        verify(expenseSplitRepository).save(s2);
    }

    @Test
    void notReceivedShouldReopenSplitAndRemoveItsPoints() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "45.00", true);
        s2.setReceivedConfirmedAt(LocalDateTime.of(2026, 11, 11, 10, 0));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findById("s2")).thenReturn(Optional.of(s2));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s2));

        ExpenseResponse response = expenseService.markNotReceived("u1", "house-1", "exp-1", "s2");

        assertFalse(s2.getIsPaid());
        assertNull(s2.getPaidAt());
        assertNull(s2.getReceivedConfirmedAt());
        assertEquals("u1", s2.getReopenedById());
        assertEquals(LocalDateTime.of(2026, 11, 15, 12, 0), s2.getReopenedAt());
        assertFalse(response.getSplits().get(0).getIsPaid());
        verify(pointService).removeByOrigin(PointSourceType.EXPENSE_SPLIT, "s2", "");
    }

    @Test
    void notReceivedShouldBeForbiddenForNonPayerAndRejectUnpaidSplit() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        ExpenseSplit paid = split("s2", "exp-1", "u2", "45.00", true);
        ExpenseSplit unpaid = split("s3", "exp-1", "u3", "45.00", false);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findById("s2")).thenReturn(Optional.of(paid));
        when(expenseSplitRepository.findById("s3")).thenReturn(Optional.of(unpaid));

        ForbiddenOperationException forbidden = assertThrows(ForbiddenOperationException.class,
                () -> expenseService.markNotReceived("u2", "house-1", "exp-1", "s2"));
        assertEquals("only the payer can confirm payment", forbidden.getMessage());

        BusinessRuleException notPaid = assertThrows(BusinessRuleException.class,
                () -> expenseService.markNotReceived("u1", "house-1", "exp-1", "s3"));
        assertEquals("split is not marked as paid", notPaid.getMessage());

        assertTrue(paid.getIsPaid());
        verify(pointService, never()).removeByOrigin(any(), anyString(), any());
    }

    // ---------------------------------------------------------------- update / delete

    @Test
    void updateShouldBeForbiddenForSomeoneOtherThanTheCreator() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        when(houseAccessValidator.requireHouseMember("u2", "house-1")).thenReturn(createMember("u2", "house-1"));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setTitle("Outro");

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> expenseService.updateExpense("u2", "house-1", "exp-1", request));
        assertEquals("only the creator can modify this expense", ex.getMessage());
        assertEquals("Luz", e.getTitle());
    }

    @Test
    void legacyExpenseWithoutCreatorCanOnlyBeModifiedByHouseAdmin() {
        Expense e = expense("exp-1", "house-1", null, null, "90.00");
        User admin = createMember("admin", "house-1");
        admin.setIsHouseAdmin(true);
        when(houseAccessValidator.requireHouseMember("u2", "house-1")).thenReturn(createMember("u2", "house-1"));
        when(houseAccessValidator.requireHouseMember("admin", "house-1")).thenReturn(admin);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(split("s1", "exp-1", "u2", "90.00", false)));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setTitle("Renomeada");

        assertThrows(ForbiddenOperationException.class,
                () -> expenseService.updateExpense("u2", "house-1", "exp-1", request));

        ExpenseResponse response = expenseService.updateExpense("admin", "house-1", "exp-1", request);
        assertEquals("Renomeada", response.getTitle());
    }

    @Test
    void updateShouldConflictWhenSomeoneOtherThanThePayerAlreadyPaid() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        when(houseAccessValidator.requireHouseMember("u1", "house-1")).thenReturn(createMember("u1", "house-1"));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(
                split("s1", "exp-1", "u1", "45.00", true),
                split("s2", "exp-1", "u2", "45.00", true)));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setTotalAmount(new BigDecimal("120.00"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> expenseService.updateExpense("u1", "house-1", "exp-1", request));
        assertEquals("expense already has payments", ex.getMessage());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void updateTotalAndPayerShouldRecalculateEqualSharesWithOnlyNewPayerPaid() {
        String houseId = "house-1";
        Expense e = expense("exp-1", houseId, "u1", "u1", "90.00");
        ExpenseSplit s1 = split("s1", "exp-1", "u1", "45.00", true);
        s1.setReceivedConfirmedAt(LocalDateTime.of(2026, 11, 10, 10, 0));
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "45.00", false);
        when(houseAccessValidator.requireHouseMember("u1", houseId)).thenReturn(createMember("u1", houseId));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s1, s2));
        when(userRepository.findByHouseIdOrderByIdAsc(houseId))
                .thenReturn(List.of(createMember("u1", houseId), createMember("u2", houseId)));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setTotalAmount(new BigDecimal("100.01"));
        request.setPaidById("u2");

        ExpenseResponse response = expenseService.updateExpense("u1", houseId, "exp-1", request);

        assertEquals(new BigDecimal("100.01"), response.getTotalAmount());
        assertEquals("u2", e.getPaidById());
        // the payer absorbs the cent difference
        assertEquals(new BigDecimal("50.00"), s1.getAmountDue());
        assertEquals(new BigDecimal("50.01"), s2.getAmountDue());
        assertFalse(s1.getIsPaid());
        assertNull(s1.getPaidAt());
        assertNull(s1.getReceivedConfirmedAt());
        assertTrue(s2.getIsPaid());
        assertEquals(LocalDateTime.of(2026, 11, 15, 12, 0), s2.getPaidAt());
        assertEquals(LocalDateTime.of(2026, 11, 15, 12, 0), s2.getReceivedConfirmedAt());
    }

    @Test
    void updatePayerOnlyShouldRecalculateEqualSharesSoTheNewPayerAbsorbsTheCent() {
        String houseId = "house-1";
        Expense e = expense("exp-1", houseId, "u1", "u1", "100.00");
        ExpenseSplit s1 = split("s1", "exp-1", "u1", "33.34", true);
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "33.33", false);
        ExpenseSplit s3 = split("s3", "exp-1", "u3", "33.33", false);
        when(houseAccessValidator.requireHouseMember("u1", houseId)).thenReturn(createMember("u1", houseId));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s1, s2, s3));
        when(userRepository.findByHouseIdOrderByIdAsc(houseId)).thenReturn(List.of(
                createMember("u1", houseId), createMember("u2", houseId), createMember("u3", houseId)));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setPaidById("u2");

        expenseService.updateExpense("u1", houseId, "exp-1", request);

        assertEquals(new BigDecimal("33.33"), s1.getAmountDue());
        assertEquals(new BigDecimal("33.34"), s2.getAmountDue());
        assertEquals(new BigDecimal("33.33"), s3.getAmountDue());
        assertFalse(s1.getIsPaid());
        assertTrue(s2.getIsPaid());
        assertFalse(s3.getIsPaid());
    }

    @Test
    void updateShouldRejectPayerWhoIsNotACurrentResident() {
        String houseId = "house-1";
        Expense e = expense("exp-1", houseId, "u1", "u1", "90.00");
        when(houseAccessValidator.requireHouseMember("u1", houseId)).thenReturn(createMember("u1", houseId));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(split("s1", "exp-1", "u1", "90.00", true)));
        when(userRepository.findByHouseIdOrderByIdAsc(houseId)).thenReturn(List.of(createMember("u1", houseId)));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setPaidById("outsider");

        assertThrows(BusinessRuleException.class,
                () -> expenseService.updateExpense("u1", houseId, "exp-1", request));
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void deleteShouldRemoveSplitsVotesAndExpenseWhenOnlyThePayerHasPaid() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        when(houseAccessValidator.requireHouseMember("u1", "house-1")).thenReturn(createMember("u1", "house-1"));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(
                split("s1", "exp-1", "u1", "45.00", true),
                split("s2", "exp-1", "u2", "45.00", false)));

        expenseService.deleteExpense("u1", "house-1", "exp-1");

        verify(expenseSplitVoteRepository).deleteByExpenseId("exp-1");
        verify(expenseSplitRepository).deleteByExpenseId("exp-1");
        verify(expenseRepository).delete(e);
    }

    @Test
    void deleteShouldConflictWhenAnotherResidentAlreadyPaidAndForbidNonCreator() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        when(houseAccessValidator.requireHouseMember("u1", "house-1")).thenReturn(createMember("u1", "house-1"));
        when(houseAccessValidator.requireHouseMember("u2", "house-1")).thenReturn(createMember("u2", "house-1"));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(
                split("s1", "exp-1", "u1", "45.00", true),
                split("s2", "exp-1", "u2", "45.00", true)));

        assertThrows(ForbiddenOperationException.class,
                () -> expenseService.deleteExpense("u2", "house-1", "exp-1"));
        ConflictException ex = assertThrows(ConflictException.class,
                () -> expenseService.deleteExpense("u1", "house-1", "exp-1"));
        assertEquals("expense already has payments", ex.getMessage());
        verify(expenseRepository, never()).delete(any());
    }

    // ---------------------------------------------------------------- propose-split

    @Test
    void proposeCustomSplitShouldAcceptZeroAndNotBeBlockedByPayerPaidShare() {
        String houseId = "house-1";
        Expense e = expense("exp-1", houseId, "u1", "u1", "200.00");
        ExpenseSplit s1 = split("s1", "exp-1", "u1", "100.00", true);
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "100.00", false);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s1, s2));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expenseSplitRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ProposeSplitRequest request = new ProposeSplitRequest();
        request.setSplitMode("VALOR");
        ProposeSplitRequest.SplitItem i1 = new ProposeSplitRequest.SplitItem();
        i1.setUserId("u1");
        i1.setAmountOrPercentage(new BigDecimal("200.00"));
        ProposeSplitRequest.SplitItem i2 = new ProposeSplitRequest.SplitItem();
        i2.setUserId("u2");
        i2.setAmountOrPercentage(BigDecimal.ZERO);
        request.setSplits(List.of(i1, i2));

        ExpenseResponse response = expenseService.proposeCustomSplit("u2", houseId, "exp-1", request);

        assertEquals(SplitType.CUSTOM_PENDING, response.getSplitType());
        assertEquals(new BigDecimal("0.00"), s2.getProposedAmountDue());
    }

    @Test
    void proposeCustomSplitByPercentageShouldUseSameSumErrorMessage() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "200.00");
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));

        ProposeSplitRequest request = new ProposeSplitRequest();
        request.setSplitMode("PORCENTAGEM");
        ProposeSplitRequest.SplitItem i1 = new ProposeSplitRequest.SplitItem();
        i1.setUserId("u1");
        i1.setAmountOrPercentage(new BigDecimal("50"));
        ProposeSplitRequest.SplitItem i2 = new ProposeSplitRequest.SplitItem();
        i2.setUserId("u2");
        i2.setAmountOrPercentage(new BigDecimal("30"));
        request.setSplits(List.of(i1, i2));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.proposeCustomSplit("u1", "house-1", "exp-1", request));
        assertEquals("split must add up to the total", ex.getMessage());
    }

    // ---------------------------------------------------------------- summary

    @Test
    void summaryShouldGroupOpenSharesIncludeFormerResidentAndTolerateLegacyPayer() {
        String houseId = "house-1";
        Expense luz = expense("exp-1", houseId, "u1", "u1", "300.00");
        Expense agua = expense("exp-2", houseId, "u2", "u2", "60.00");
        Expense legacy = expense("exp-3", houseId, null, null, "40.00");
        when(expenseRepository.findByHouseIdOrderByDueDateAsc(houseId)).thenReturn(List.of(luz, agua, legacy));
        when(expenseSplitRepository.findByExpenseIdIn(any())).thenReturn(List.of(
                split("s1", "exp-1", "u1", "100.00", true),
                split("s2", "exp-1", "u2", "100.00", false),
                split("s3", "exp-1", "former", "100.00", false),
                split("s4", "exp-2", "u1", "30.00", false),
                split("s5", "exp-2", "u2", "30.00", true),
                split("s6", "exp-3", "u1", "20.00", false),
                split("s7", "exp-3", "u2", "20.00", true)));
        when(userRepository.findByHouseIdOrderByIdAsc(houseId)).thenReturn(List.of(
                namedMember("u1", houseId, "Ana"), namedMember("u2", houseId, "Bruno")));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                namedMember("u1", houseId, "Ana"), namedMember("u2", houseId, "Bruno"),
                namedMember("former", null, "Fabio Ex")));

        ExpenseSummaryResponse summary = expenseService.getSummary("u1", houseId);

        assertEquals(1, summary.myDebts().size());
        assertEquals("u2", summary.myDebts().get(0).creditorId());
        assertEquals("Bruno", summary.myDebts().get(0).creditorName());
        assertEquals(0, new BigDecimal("30.00").compareTo(summary.myDebts().get(0).amount()));

        assertEquals(2, summary.owedToMe().size());
        ExpenseSummaryResponse.Credit fromBruno = summary.owedToMe().stream()
                .filter(c -> c.debtorId().equals("u2")).findFirst().orElseThrow();
        ExpenseSummaryResponse.Credit fromFormer = summary.owedToMe().stream()
                .filter(c -> c.debtorId().equals("former")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(fromBruno.amount()));
        assertEquals("Fabio Ex", fromFormer.debtorName());
        assertEquals(0, new BigDecimal("100.00").compareTo(fromFormer.amount()));

        ExpenseSummaryResponse.MemberBalance ana = summary.members().stream()
                .filter(m -> m.userId().equals("u1")).findFirst().orElseThrow();
        assertEquals("Ana", ana.name());
        assertEquals(1, ana.paidCount());
        assertEquals(3, ana.totalCount());
        assertEquals(0, new BigDecimal("50.00").compareTo(ana.openAmount()));

        ExpenseSummaryResponse.MemberBalance former = summary.members().stream()
                .filter(m -> m.userId().equals("former")).findFirst().orElseThrow();
        assertEquals("Fabio Ex", former.name());
        assertEquals(0, former.paidCount());
        assertEquals(1, former.totalCount());
        assertEquals(0, new BigDecimal("100.00").compareTo(former.openAmount()));
    }

    // ---------------------------------------------------------------- cents, zero shares, invariants

    private ProposeSplitRequest percentageRequest(String... userAndPercent) {
        ProposeSplitRequest request = new ProposeSplitRequest();
        request.setSplitMode("PORCENTAGEM");
        List<ProposeSplitRequest.SplitItem> items = new ArrayList<>();
        for (int i = 0; i < userAndPercent.length; i += 2) {
            ProposeSplitRequest.SplitItem item = new ProposeSplitRequest.SplitItem();
            item.setUserId(userAndPercent[i]);
            item.setAmountOrPercentage(new BigDecimal(userAndPercent[i + 1]));
            items.add(item);
        }
        request.setSplits(items);
        return request;
    }

    private ProposeSplitRequest valueRequest(String... userAndAmount) {
        ProposeSplitRequest request = percentageRequest(userAndAmount);
        request.setSplitMode("VALOR");
        return request;
    }

    @Test
    void valueSplitShouldRejectFractionOfACentEvenWhenTheRawSumMatchesTheTotal() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "50.01");
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.proposeCustomSplit("u1", "house-1", "exp-1",
                        valueRequest("u1", "25.005", "u2", "25.005")));

        assertEquals("split must add up to the total", ex.getMessage());
        verify(expenseSplitRepository, never()).saveAll(any());
    }

    @Test
    void valueSplitShouldRejectThirdsWithFractionOfACentThatWouldNotAddUpOnceRounded() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "100.00");
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.proposeCustomSplit("u1", "house-1", "exp-1",
                        valueRequest("u1", "33.334", "u2", "33.333", "u3", "33.333")));

        assertEquals("split must add up to the total", ex.getMessage());
        verify(expenseSplitRepository, never()).saveAll(any());
    }

    @Test
    void percentageSplitShouldGiveTheCentToAPositiveShareWhenThePayerHasZeroPercent() {
        String houseId = "house-1";
        Expense e = expense("exp-1", houseId, "u1", "u1", "50.01");
        ExpenseSplit s1 = split("s1", "exp-1", "u1", "16.67", true);
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "16.67", false);
        ExpenseSplit s3 = split("s3", "exp-1", "u3", "16.67", false);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s1, s2, s3));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        expenseService.proposeCustomSplit("u2", houseId, "exp-1", percentageRequest("u1", "0", "u2", "50", "u3", "50"));

        assertEquals(new BigDecimal("0.00"), s1.getProposedAmountDue());
        assertEquals(new BigDecimal("25.01"), s2.getProposedAmountDue());
        assertEquals(new BigDecimal("25.00"), s3.getProposedAmountDue());
    }

    @Test
    void percentageSplitWithThirdsAndZeroPayerShouldAddUpWithoutCentsForTheExcluded() {
        String houseId = "house-1";
        Expense e = expense("exp-1", houseId, "u1", "u1", "10.00");
        ExpenseSplit s1 = split("s1", "exp-1", "u1", "2.50", true);
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "2.50", false);
        ExpenseSplit s3 = split("s3", "exp-1", "u3", "2.50", false);
        ExpenseSplit s4 = split("s4", "exp-1", "u4", "2.50", false);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s1, s2, s3, s4));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        expenseService.proposeCustomSplit("u2", houseId, "exp-1",
                percentageRequest("u1", "0", "u2", "33.33", "u3", "33.33", "u4", "33.34"));

        assertEquals(new BigDecimal("0.00"), s1.getProposedAmountDue());
        assertEquals(new BigDecimal("3.34"), s2.getProposedAmountDue());
        assertEquals(new BigDecimal("3.33"), s3.getProposedAmountDue());
        assertEquals(new BigDecimal("3.33"), s4.getProposedAmountDue());
        BigDecimal sum = s1.getProposedAmountDue().add(s2.getProposedAmountDue())
                .add(s3.getProposedAmountDue()).add(s4.getProposedAmountDue());
        assertEquals(new BigDecimal("10.00"), sum);
    }

    @Test
    void percentageSplitShouldStillGiveTheCentToThePayerWhenThePayerHasAShare() {
        String houseId = "house-1";
        Expense e = expense("exp-1", houseId, "u2", "u2", "50.01");
        ExpenseSplit s1 = split("s1", "exp-1", "u1", "25.00", false);
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "25.01", true);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(s1, s2));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        expenseService.proposeCustomSplit("u1", houseId, "exp-1", percentageRequest("u1", "50", "u2", "50"));

        assertEquals(new BigDecimal("25.00"), s1.getProposedAmountDue());
        assertEquals(new BigDecimal("25.01"), s2.getProposedAmountDue());
    }

    @Test
    void payingAZeroShareShouldBeRejectedWithoutPoints() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        ExpenseSplit s2 = split("s2", "exp-1", "u2", "0.00", false);
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseIdAndUserId("exp-1", "u2")).thenReturn(Optional.of(s2));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.markSplitAsPaid("u2", "house-1", "exp-1"));

        assertEquals("split has nothing to pay", ex.getMessage());
        assertFalse(s2.getIsPaid());
        verify(expenseSplitRepository, never()).save(any());
        verify(pointService, never()).creditIfAbsent(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void zeroShareMarkedPaidShouldNotBlockUpdateOrDelete() {
        String houseId = "house-1";
        Expense e = expense("exp-1", houseId, "u1", "u1", "90.00");
        e.setSplitType(SplitType.CUSTOM_APPROVED);
        when(houseAccessValidator.requireHouseMember("u1", houseId)).thenReturn(createMember("u1", houseId));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(
                split("s1", "exp-1", "u1", "90.00", true),
                split("s2", "exp-1", "u2", "0.00", true)));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setTitle("Renomeada");
        assertEquals("Renomeada", expenseService.updateExpense("u1", houseId, "exp-1", request).getTitle());

        expenseService.deleteExpense("u1", houseId, "exp-1");
        verify(expenseRepository).delete(e);
    }

    @Test
    void summaryShouldIgnoreZeroShares() {
        String houseId = "house-1";
        Expense luz = expense("exp-1", houseId, "u1", "u1", "100.00");
        when(expenseRepository.findByHouseIdOrderByDueDateAsc(houseId)).thenReturn(List.of(luz));
        when(expenseSplitRepository.findByExpenseIdIn(any())).thenReturn(List.of(
                split("s1", "exp-1", "u1", "50.00", true),
                split("s2", "exp-1", "u2", "50.00", false),
                split("s3", "exp-1", "u3", "0.00", false)));
        when(userRepository.findByHouseIdOrderByIdAsc(houseId)).thenReturn(List.of(
                namedMember("u1", houseId, "Ana"), namedMember("u2", houseId, "Bruno"),
                namedMember("u3", houseId, "Carla")));
        when(userRepository.findAllById(any())).thenReturn(List.of(
                namedMember("u1", houseId, "Ana"), namedMember("u2", houseId, "Bruno"),
                namedMember("u3", houseId, "Carla")));

        ExpenseSummaryResponse forAna = expenseService.getSummary("u1", houseId);
        assertEquals(1, forAna.owedToMe().size());
        assertEquals("u2", forAna.owedToMe().get(0).debtorId());
        ExpenseSummaryResponse.MemberBalance carla = forAna.members().stream()
                .filter(m -> m.userId().equals("u3")).findFirst().orElseThrow();
        assertEquals(0, carla.paidCount());
        assertEquals(0, carla.totalCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(carla.openAmount()));

        ExpenseSummaryResponse forCarla = expenseService.getSummary("u3", houseId);
        assertTrue(forCarla.myDebts().isEmpty());
    }

    @Test
    void createShouldRejectTotalThatRoundsToZero() {
        CreateExpenseRequest request = createRequest();
        request.setTotalAmount(new BigDecimal("0.004"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense("u1", "house-1", request));
        assertEquals("Expense total amount must be greater than zero", ex.getMessage());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createShouldRejectActiveRecurrenceWithoutRecurringDay() {
        CreateExpenseRequest request = createRequest();
        request.setRecurrenceActive(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense("u1", "house-1", request));
        assertEquals("recurringDay is required when recurrence is active", ex.getMessage());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectTotalThatRoundsToZero() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        when(houseAccessValidator.requireHouseMember("u1", "house-1")).thenReturn(createMember("u1", "house-1"));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(split("s1", "exp-1", "u1", "90.00", true)));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setTotalAmount(new BigDecimal("0.004"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.updateExpense("u1", "house-1", "exp-1", request));
        assertEquals("Expense total amount must be greater than zero", ex.getMessage());
        assertEquals(new BigDecimal("90.00"), e.getTotalAmount());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectActivatingRecurrenceWhenTheExpenseHasNoRecurringDay() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        when(houseAccessValidator.requireHouseMember("u1", "house-1")).thenReturn(createMember("u1", "house-1"));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(split("s1", "exp-1", "u1", "90.00", true)));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setRecurrenceActive(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.updateExpense("u1", "house-1", "exp-1", request));
        assertEquals("recurringDay is required when recurrence is active", ex.getMessage());
        assertFalse(Boolean.TRUE.equals(e.getRecurrenceActive()));
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void updateShouldAllowActivatingRecurrenceWhenTheExpenseAlreadyHasARecurringDay() {
        Expense e = expense("exp-1", "house-1", "u1", "u1", "90.00");
        e.setRecurringDay(5);
        when(houseAccessValidator.requireHouseMember("u1", "house-1")).thenReturn(createMember("u1", "house-1"));
        when(expenseRepository.findById("exp-1")).thenReturn(Optional.of(e));
        when(expenseSplitRepository.findByExpenseId("exp-1")).thenReturn(List.of(split("s1", "exp-1", "u1", "90.00", true)));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setRecurrenceActive(true);

        ExpenseResponse response = expenseService.updateExpense("u1", "house-1", "exp-1", request);
        assertTrue(response.getRecurrenceActive());
        assertEquals("exp-1", e.getRecurrenceSourceId());
    }
}
