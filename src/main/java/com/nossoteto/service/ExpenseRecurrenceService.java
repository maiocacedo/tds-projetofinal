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
import com.nossoteto.repository.UserRepository;
import com.nossoteto.util.AppTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ExpenseRecurrenceService {

    static final String ACTIVE_REQUIRED_MESSAGE = "active is required";
    static final String ONLY_CREATOR_OR_ADMIN_MESSAGE = "only the creator or a house admin can change the recurrence";

    private static final Logger log = LoggerFactory.getLogger(ExpenseRecurrenceService.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final UserRepository userRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final ExpenseService expenseService;
    private final TransactionTemplate perTemplateTransaction;
    private final Clock clock;

    public ExpenseRecurrenceService(
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            UserRepository userRepository,
            HouseAccessValidator houseAccessValidator,
            ExpenseService expenseService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.userRepository = userRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.expenseService = expenseService;
        this.perTemplateTransaction = new TransactionTemplate(transactionManager);
        this.perTemplateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Turns the recurrence on or off. Unlike editing the expense, this works after people have paid:
     * it only changes what happens next month, never the shares already settled.
     */
    @Transactional
    public ExpenseResponse updateRecurrence(
            String userId,
            String houseId,
            String expenseId,
            UpdateRecurrenceRequest request) {
        User caller = houseAccessValidator.requireHouseMember(userId, houseId);
        Expense expense = expenseRepository.findById(expenseId)
                .filter(e -> houseId.equals(e.getHouseId()))
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));

        boolean isCreator = expense.getCreatedById() != null && Objects.equals(expense.getCreatedById(), userId);
        if (!isCreator && !Boolean.TRUE.equals(caller.getIsHouseAdmin())) {
            throw new ForbiddenOperationException(ONLY_CREATOR_OR_ADMIN_MESSAGE);
        }
        if (ExpenseService.isGeneratedOccurrence(expense)) {
            throw new ConflictException(ExpenseService.GENERATED_OCCURRENCE_MESSAGE);
        }
        if (request == null || request.getActive() == null) {
            throw new BusinessRuleException(ACTIVE_REQUIRED_MESSAGE);
        }

        if (request.getActive()) {
            Integer day = request.getDay();
            if (day == null || day < 1 || day > 31) {
                throw new BusinessRuleException(ExpenseService.RECURRING_DAY_MESSAGE);
            }
            expense.setRecurrenceActive(true);
            expense.setRecurringDay(day);
            if (expense.getRecurrenceSourceId() == null) {
                expense.setRecurrenceSourceId(expense.getId());
                expense.setRecurrenceMonth(expenseService.firstRecurrenceMonth(expense.getDueDate()));
            }
        } else {
            // Source and month stay, so turning it back on later never repeats a month already covered.
            expense.setRecurrenceActive(false);
        }

        Expense saved = expenseRepository.save(expense);
        return expenseService.toResponse(saved, expenseSplitRepository.findByExpenseId(saved.getId()));
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Sao_Paulo")
    public void processRecurringExpenses() {
        processRecurringExpensesForDate(AppTime.today(clock));
    }

    /**
     * Creates this month's occurrence of every active template whose day has come. Each template runs
     * in its own transaction, so one failure is logged and the others still go through.
     */
    public void processRecurringExpensesForDate(LocalDate today) {
        List<String> templateIds = expenseRepository.findActiveRecurrenceTemplates().stream()
                .map(Expense::getId)
                .toList();
        for (String templateId : templateIds) {
            try {
                perTemplateTransaction.executeWithoutResult(status -> processTemplate(templateId, today));
            } catch (RuntimeException e) {
                log.warn("Recurring expense {} could not be generated for {}", templateId, today, e);
            }
        }
    }

    private void processTemplate(String templateId, LocalDate today) {
        Expense source = expenseRepository.findById(templateId).orElse(null);
        if (source == null
                || !Boolean.TRUE.equals(source.getRecurrenceActive())
                || source.getRecurringDay() == null
                || ExpenseService.isGeneratedOccurrence(source)) {
            return;
        }

        List<String> memberIds = userRepository.findByHouseIdOrderByIdAsc(source.getHouseId()).stream()
                .map(User::getId)
                .toList();
        if (source.getPaidById() == null || !memberIds.contains(source.getPaidById())) {
            // Nobody left to front the bill: pause instead of charging the house on someone else's behalf.
            source.setRecurrenceActive(false);
            expenseRepository.save(source);
            return;
        }

        YearMonth month = YearMonth.from(today);
        // Months before the first one the template stands for are never generated.
        if (source.getRecurrenceMonth() != null && month.isBefore(YearMonth.parse(source.getRecurrenceMonth()))) {
            return;
        }
        String monthKey = month.toString();
        int effectiveDay = Math.min(source.getRecurringDay(), month.lengthOfMonth());
        // ">=" catches up a day missed while the server was down; the month key keeps it to one per month.
        if (today.getDayOfMonth() < effectiveDay
                || expenseRepository.existsByRecurrenceSourceIdAndRecurrenceMonth(source.getId(), monthKey)) {
            return;
        }

        Expense child = new Expense();
        child.setId(UUID.randomUUID().toString());
        child.setHouseId(source.getHouseId());
        child.setTitle(source.getTitle());
        child.setTotalAmount(source.getTotalAmount());
        child.setDueDate(month.atDay(effectiveDay));
        child.setSplitType(SplitType.EQUAL);
        child.setPaidById(source.getPaidById());
        child.setCreatedById(source.getCreatedById());
        child.setAttachmentUrl(null);
        child.setRecurrenceActive(false);
        child.setRecurringDay(null);
        child.setRecurrenceSourceId(source.getId());
        child.setRecurrenceMonth(monthKey);
        expenseRepository.saveAndFlush(child);

        // Always an equal split among who lives there now; the payer's share is settled and earns no points.
        List<ExpenseSplit> splits = ExpenseService.buildEqualSplits(
                child.getId(), child.getTotalAmount(), memberIds, child.getPaidById(), AppTime.nowUtc(clock));
        expenseSplitRepository.saveAll(splits);
    }
}
