package com.nossoteto.service;

import com.nossoteto.dto.ExpenseResponse;
import com.nossoteto.dto.UpdateRecurrenceRequest;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ConflictException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Expense;
import com.nossoteto.model.ExpenseSplit;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.SplitType;
import com.nossoteto.repository.ExpenseRepository;
import com.nossoteto.repository.ExpenseSplitRepository;
import com.nossoteto.repository.ExpenseSplitVoteRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseRecurrenceServiceTest {

    private static final String HOUSE = "house-1";

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseSplitRepository expenseSplitRepository;
    @Mock private ExpenseSplitVoteRepository expenseSplitVoteRepository;
    @Mock private UserRepository userRepository;
    @Mock private HouseRepository houseRepository;
    @Mock private HouseAccessValidator houseAccessValidator;
    @Mock private VotingEvaluator votingEvaluator;
    @Mock private PointService pointService;
    @Mock private PlatformTransactionManager transactionManager;

    private ExpenseRecurrenceService service;

    @BeforeEach
    void setUp() {
        // 2026-11-15 09:00 in America/Sao_Paulo
        build(Instant.parse("2026-11-15T12:00:00Z"));
    }

    private void build(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        ExpenseService expenseService = new ExpenseService(expenseRepository, expenseSplitRepository,
                expenseSplitVoteRepository, userRepository, houseRepository, houseAccessValidator,
                votingEvaluator, pointService, clock);
        service = new ExpenseRecurrenceService(expenseRepository, expenseSplitRepository, userRepository,
                houseAccessValidator, expenseService, transactionManager, clock);
    }

    private static User member(String id, boolean admin) {
        User u = new User();
        u.setId(id);
        u.setHouseId(HOUSE);
        u.setIsHouseAdmin(admin);
        return u;
    }

    private static Expense expense(String id, String createdBy, String paidBy) {
        Expense e = new Expense();
        e.setId(id);
        e.setHouseId(HOUSE);
        e.setTitle("Internet");
        e.setTotalAmount(new BigDecimal("100.00"));
        e.setDueDate(LocalDate.of(2026, 11, 20));
        e.setSplitType(SplitType.EQUAL);
        e.setCreatedById(createdBy);
        e.setPaidById(paidBy);
        return e;
    }

    private static Expense template(String id, int day, String month) {
        Expense e = expense(id, "u1", "u1");
        e.setRecurrenceActive(true);
        e.setRecurringDay(day);
        e.setRecurrenceSourceId(id);
        e.setRecurrenceMonth(month);
        return e;
    }

    private static ExpenseSplit paidSplit(String expenseId, String userId) {
        ExpenseSplit s = new ExpenseSplit();
        s.setId("s-" + userId);
        s.setExpenseId(expenseId);
        s.setUserId(userId);
        s.setAmountDue(new BigDecimal("50.00"));
        s.setIsPaid(true);
        return s;
    }

    private void callerIs(User caller) {
        when(houseAccessValidator.requireHouseMember(caller.getId(), HOUSE)).thenReturn(caller);
    }

    private void stored(Expense e) {
        when(expenseRepository.findById(e.getId())).thenReturn(Optional.of(e));
    }

    private void savesReturnTheirArgument() {
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------ updateRecurrence

    @Test
    void creatorCanActivateEvenAfterOthersPaidAndTheCurrentMonthIsAlreadyCovered() {
        Expense e = expense("exp-1", "u1", "u1");
        callerIs(member("u1", false));
        stored(e);
        savesReturnTheirArgument();
        when(expenseSplitRepository.findByExpenseId("exp-1"))
                .thenReturn(List.of(paidSplit("exp-1", "u1"), paidSplit("exp-1", "u2")));

        ExpenseResponse response = service.updateRecurrence("u1", HOUSE, "exp-1", new UpdateRecurrenceRequest(true, 5));

        assertTrue(response.getRecurrenceActive());
        assertEquals(5, response.getRecurringDay());
        assertEquals(2, response.getSplits().size());
        assertTrue(e.getRecurrenceActive());
        assertEquals(5, e.getRecurringDay());
        assertEquals("exp-1", e.getRecurrenceSourceId());
        assertEquals("2026-11", e.getRecurrenceMonth());
    }

    @Test
    void activatingAnExpenseDueNextMonthMarksThatMonthAsCovered() {
        build(Instant.parse("2026-11-26T15:00:00Z"));
        Expense e = expense("exp-1", "u1", "u1");
        e.setDueDate(LocalDate.of(2026, 12, 5));
        callerIs(member("u1", false));
        stored(e);
        savesReturnTheirArgument();

        service.updateRecurrence("u1", HOUSE, "exp-1", new UpdateRecurrenceRequest(true, 5));

        assertEquals("2026-12", e.getRecurrenceMonth());
    }

    @Test
    void houseAdminWhoIsNotTheCreatorCanChangeTheRecurrence() {
        Expense e = expense("exp-1", "u1", "u1");
        callerIs(member("admin", true));
        stored(e);
        savesReturnTheirArgument();

        service.updateRecurrence("admin", HOUSE, "exp-1", new UpdateRecurrenceRequest(true, 10));

        assertTrue(e.getRecurrenceActive());
        assertEquals(10, e.getRecurringDay());
    }

    @Test
    void otherResidentCannotChangeTheRecurrence() {
        Expense e = expense("exp-1", "u1", "u1");
        callerIs(member("u2", false));
        stored(e);

        assertThrows(ForbiddenOperationException.class,
                () -> service.updateRecurrence("u2", HOUSE, "exp-1", new UpdateRecurrenceRequest(true, 5)));
        assertFalse(e.getRecurrenceActive());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void generatedOccurrenceCannotChangeItsRecurrence() {
        Expense child = expense("child", "u1", "u1");
        child.setRecurrenceSourceId("template");
        child.setRecurrenceMonth("2026-11");
        callerIs(member("u1", false));
        stored(child);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.updateRecurrence("u1", HOUSE, "child", new UpdateRecurrenceRequest(true, 5)));
        assertEquals("cannot change recurrence of a generated occurrence", ex.getMessage());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void activeMustBeGivenAndDayMustBeBetweenOneAndThirtyOne() {
        Expense e = expense("exp-1", "u1", "u1");
        callerIs(member("u1", false));
        stored(e);

        assertThrows(BusinessRuleException.class,
                () -> service.updateRecurrence("u1", HOUSE, "exp-1", new UpdateRecurrenceRequest(null, 5)));
        assertThrows(BusinessRuleException.class,
                () -> service.updateRecurrence("u1", HOUSE, "exp-1", null));
        for (Integer day : new Integer[] {null, 0, 32}) {
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> service.updateRecurrence("u1", HOUSE, "exp-1", new UpdateRecurrenceRequest(true, day)));
            assertEquals("recurringDay must be between 1 and 31", ex.getMessage());
        }
        assertFalse(e.getRecurrenceActive());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void expenseFromAnotherHouseIsNotFound() {
        Expense e = expense("exp-1", "u1", "u1");
        e.setHouseId("other-house");
        callerIs(member("u1", true));
        stored(e);

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateRecurrence("u1", HOUSE, "exp-1", new UpdateRecurrenceRequest(true, 5)));
    }

    @Test
    void deactivatingAndReactivatingKeepsTheOriginalSourceAndMonth() {
        Expense e = template("exp-1", 5, "2026-10");
        callerIs(member("u1", false));
        stored(e);
        savesReturnTheirArgument();

        service.updateRecurrence("u1", HOUSE, "exp-1", new UpdateRecurrenceRequest(false, null));
        assertFalse(e.getRecurrenceActive());
        assertEquals(5, e.getRecurringDay());
        assertEquals("exp-1", e.getRecurrenceSourceId());
        assertEquals("2026-10", e.getRecurrenceMonth());

        service.updateRecurrence("u1", HOUSE, "exp-1", new UpdateRecurrenceRequest(true, 20));
        assertTrue(e.getRecurrenceActive());
        assertEquals(20, e.getRecurringDay());
        assertEquals("exp-1", e.getRecurrenceSourceId());
        assertEquals("2026-10", e.getRecurrenceMonth());
    }

    // ------------------------------------------------------------ job

    private void activeTemplates(Expense... templates) {
        when(expenseRepository.findActiveRecurrenceTemplates()).thenReturn(List.of(templates));
        for (Expense t : templates) {
            stored(t);
        }
    }

    private void residents(String... ids) {
        List<User> users = new ArrayList<>();
        for (String id : ids) {
            users.add(member(id, false));
        }
        when(userRepository.findByHouseIdOrderByIdAsc(HOUSE)).thenReturn(users);
    }

    private Expense generatedChild() {
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<ExpenseSplit> generatedSplits() {
        ArgumentCaptor<List<ExpenseSplit>> captor = ArgumentCaptor.forClass(List.class);
        verify(expenseSplitRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private LocalDate dueDateGeneratedOn(int recurringDay, LocalDate runDate) {
        Expense t = template("tpl", recurringDay, "2026-01");
        activeTemplates(t);
        residents("u1", "u2");
        service.processRecurringExpensesForDate(runDate);
        return generatedChild().getDueDate();
    }

    @Test
    void dayThirtyOneIsClampedToTheLastDayOfFebruaryInACommonYear() {
        assertEquals(LocalDate.of(2026, 2, 28), dueDateGeneratedOn(31, LocalDate.of(2026, 2, 28)));
    }

    @Test
    void dayThirtyIsClampedToTheTwentyNinthOfFebruaryInALeapYear() {
        assertEquals(LocalDate.of(2028, 2, 29), dueDateGeneratedOn(30, LocalDate.of(2028, 2, 29)));
    }

    @Test
    void dayTwentyNineInACommonFebruaryBecomesTheTwentyEighth() {
        assertEquals(LocalDate.of(2026, 2, 28), dueDateGeneratedOn(29, LocalDate.of(2026, 2, 28)));
    }

    @Test
    void dayThirtyOneIsClampedToTheThirtiethInApril() {
        assertEquals(LocalDate.of(2026, 4, 30), dueDateGeneratedOn(31, LocalDate.of(2026, 4, 30)));
    }

    @Test
    void aDayMissedWhileTheServerWasDownIsCaughtUpLaterInTheMonth() {
        assertEquals(LocalDate.of(2027, 3, 5), dueDateGeneratedOn(5, LocalDate.of(2027, 3, 9)));
    }

    @Test
    void nothingIsGeneratedBeforeTheEffectiveDay() {
        Expense t = template("tpl", 31, "2026-01");
        activeTemplates(t);
        residents("u1");

        service.processRecurringExpensesForDate(LocalDate.of(2026, 2, 27));
        service.processRecurringExpensesForDate(LocalDate.of(2026, 4, 29));

        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void monthAlreadyCoveredIsNotGeneratedAgain() {
        Expense t = template("tpl", 5, "2026-11");
        activeTemplates(t);
        residents("u1", "u2");
        when(expenseRepository.existsByRecurrenceSourceIdAndRecurrenceMonth("tpl", "2026-11")).thenReturn(true);

        service.processRecurringExpensesForDate(LocalDate.of(2026, 11, 20));

        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void expenseCreatedOnNov26DueDec5IsNotDuplicatedInNovemberNorOnDec5() {
        // Created on 26/11, due 05/12, day 5: the original stands for December.
        build(Instant.parse("2026-11-26T15:00:00Z"));
        Expense t = expense("tpl", "u1", "u1");
        t.setDueDate(LocalDate.of(2026, 12, 5));
        callerIs(member("u1", false));
        stored(t);
        savesReturnTheirArgument();
        service.updateRecurrence("u1", HOUSE, "tpl", new UpdateRecurrenceRequest(true, 5));
        assertEquals("2026-12", t.getRecurrenceMonth());

        when(expenseRepository.findActiveRecurrenceTemplates()).thenReturn(List.of(t));
        residents("u1", "u2");
        when(expenseRepository.existsByRecurrenceSourceIdAndRecurrenceMonth("tpl", "2026-12")).thenReturn(true);

        service.processRecurringExpensesForDate(LocalDate.of(2026, 11, 27));
        service.processRecurringExpensesForDate(LocalDate.of(2026, 12, 5));

        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void payerWhoLeftTheHousePausesTheTemplate() {
        Expense t = template("tpl", 5, "2026-10");
        activeTemplates(t);
        residents("u2", "u3");
        savesReturnTheirArgument();

        service.processRecurringExpensesForDate(LocalDate.of(2026, 11, 5));

        assertFalse(t.getRecurrenceActive());
        assertEquals("tpl", t.getRecurrenceSourceId());
        verify(expenseRepository).save(t);
        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void emptyHousePausesTheTemplate() {
        Expense t = template("tpl", 5, "2026-10");
        activeTemplates(t);
        residents();
        savesReturnTheirArgument();

        service.processRecurringExpensesForDate(LocalDate.of(2026, 11, 5));

        assertFalse(t.getRecurrenceActive());
        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void occurrenceIsAnEqualSplitAmongCurrentResidentsWithThePayerShareSettledAndNoPoints() {
        Expense t = template("tpl", 5, "2026-10");
        t.setTotalAmount(new BigDecimal("100.00"));
        t.setSplitType(SplitType.CUSTOM_APPROVED);
        t.setAttachmentUrl("uploads/expenses/tpl");
        t.setPaidById("u2");
        activeTemplates(t);
        residents("u1", "u2", "u3");

        service.processRecurringExpensesForDate(LocalDate.of(2026, 11, 5));

        Expense child = generatedChild();
        assertEquals(HOUSE, child.getHouseId());
        assertEquals("Internet", child.getTitle());
        assertEquals(new BigDecimal("100.00"), child.getTotalAmount());
        assertEquals(LocalDate.of(2026, 11, 5), child.getDueDate());
        assertEquals(SplitType.EQUAL, child.getSplitType());
        assertEquals("u2", child.getPaidById());
        assertEquals("u1", child.getCreatedById());
        assertNull(child.getAttachmentUrl());
        assertFalse(child.getRecurrenceActive());
        assertNull(child.getRecurringDay());
        assertEquals("tpl", child.getRecurrenceSourceId());
        assertEquals("2026-11", child.getRecurrenceMonth());

        List<ExpenseSplit> splits = generatedSplits();
        Map<String, ExpenseSplit> byUser = splits.stream()
                .collect(Collectors.toMap(ExpenseSplit::getUserId, s -> s));
        assertEquals(3, splits.size());
        assertEquals(new BigDecimal("33.33"), byUser.get("u1").getAmountDue());
        assertEquals(new BigDecimal("33.34"), byUser.get("u2").getAmountDue());
        assertEquals(new BigDecimal("33.33"), byUser.get("u3").getAmountDue());
        splits.forEach(s -> assertEquals(child.getId(), s.getExpenseId()));
        splits.forEach(s -> assertNull(s.getProposedAmountDue()));
        assertTrue(byUser.get("u2").getIsPaid());
        assertTrue(byUser.get("u2").getPaidAt() != null && byUser.get("u2").getReceivedConfirmedAt() != null);
        assertFalse(byUser.get("u1").getIsPaid());
        assertFalse(byUser.get("u3").getIsPaid());
        verifyNoInteractions(pointService);
    }

    @Test
    void aFailingTemplateDoesNotStopTheOthers() {
        Expense broken = template("broken", 5, "2026-10");
        Expense ok = template("ok", 5, "2026-10");
        when(expenseRepository.findActiveRecurrenceTemplates()).thenReturn(List.of(broken, ok));
        when(expenseRepository.findById("broken")).thenThrow(new IllegalStateException("boom"));
        stored(ok);
        residents("u1", "u2");

        service.processRecurringExpensesForDate(LocalDate.of(2026, 11, 5));

        assertEquals("ok", generatedChild().getRecurrenceSourceId());
    }

    @Test
    void templateDeactivatedAfterBeingListedIsSkipped() {
        Expense t = template("tpl", 5, "2026-10");
        when(expenseRepository.findActiveRecurrenceTemplates()).thenReturn(List.of(t));
        Expense reloaded = template("tpl", 5, "2026-10");
        reloaded.setRecurrenceActive(false);
        when(expenseRepository.findById("tpl")).thenReturn(Optional.of(reloaded));

        service.processRecurringExpensesForDate(LocalDate.of(2026, 11, 5));

        verify(expenseRepository, never()).saveAndFlush(any());
    }
}
