package com.nossoteto.service;

import com.nossoteto.dto.CreateExpenseRequest;
import com.nossoteto.dto.ExpenseResponse;
import com.nossoteto.dto.ExpenseSplitDto;
import com.nossoteto.dto.ExpenseSummaryResponse;
import com.nossoteto.dto.ProposeSplitRequest;
import com.nossoteto.dto.UpdateExpenseRequest;
import com.nossoteto.dto.UserRefDto;
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
import com.nossoteto.repository.ExpenseRepository;
import com.nossoteto.repository.ExpenseSplitRepository;
import com.nossoteto.repository.ExpenseSplitVoteRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.util.AppTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    static final String SPLIT_SUM_MESSAGE = "split must add up to the total";
    static final String ONLY_PAYER_MESSAGE = "only the payer can confirm payment";
    static final String NOT_PAID_MESSAGE = "split is not marked as paid";
    static final String ONLY_CREATOR_MESSAGE = "only the creator can modify this expense";
    static final String HAS_PAYMENTS_MESSAGE = "expense already has payments";
    static final String PAYER_NOT_MEMBER_MESSAGE = "payer must be a current house member";
    static final String RECURRING_DAY_MESSAGE = "recurringDay must be between 1 and 31";
    static final String RECURRING_DAY_REQUIRED_MESSAGE = "recurringDay is required when recurrence is active";
    static final String TOTAL_NOT_POSITIVE_MESSAGE = "Expense total amount must be greater than zero";
    static final String NOTHING_TO_PAY_MESSAGE = "split has nothing to pay";
    static final String GENERATED_OCCURRENCE_MESSAGE = "cannot change recurrence of a generated occurrence";
    static final int ON_TIME_POINTS = 10;

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ExpenseSplitVoteRepository expenseSplitVoteRepository;
    private final UserRepository userRepository;
    private final HouseRepository houseRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final VotingEvaluator votingEvaluator;
    private final PointService pointService;
    private final Clock clock;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir = "uploads";

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            ExpenseSplitVoteRepository expenseSplitVoteRepository,
            UserRepository userRepository,
            HouseRepository houseRepository,
            HouseAccessValidator houseAccessValidator,
            VotingEvaluator votingEvaluator,
            PointService pointService,
            Clock clock) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.expenseSplitVoteRepository = expenseSplitVoteRepository;
        this.userRepository = userRepository;
        this.houseRepository = houseRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.votingEvaluator = votingEvaluator;
        this.pointService = pointService;
        this.clock = clock;
    }

    @Transactional
    public ExpenseResponse createExpense(String userId, String houseId, CreateExpenseRequest request) {
        houseAccessValidator.requireHouseMember(userId, houseId);

        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessRuleException("Expense title is required");
        }
        // Validate the stored (rounded) value, so 0.004 cannot become a 0.00 expense.
        if (request.getTotalAmount() == null
                || request.getTotalAmount().setScale(2, RoundingMode.HALF_UP).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(TOTAL_NOT_POSITIVE_MESSAGE);
        }
        if (request.getDueDate() == null) {
            throw new BusinessRuleException("Expense due date is required");
        }
        validateRecurringDay(request.getRecurringDay());
        validateRecurrence(request.getRecurrenceActive(), request.getRecurringDay());

        List<User> members = userRepository.findByHouseIdOrderByIdAsc(houseId);
        if (members.isEmpty()) {
            throw new BusinessRuleException("House has no members");
        }

        String paidById = isBlank(request.getPaidById()) ? userId : request.getPaidById();
        if (members.stream().noneMatch(m -> Objects.equals(m.getId(), paidById))) {
            throw new BusinessRuleException(PAYER_NOT_MEMBER_MESSAGE);
        }

        BigDecimal totalAmount = request.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        boolean recurrenceActive = Boolean.TRUE.equals(request.getRecurrenceActive());

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID().toString());
        expense.setHouseId(houseId);
        expense.setTitle(request.getTitle().trim());
        expense.setTotalAmount(totalAmount);
        expense.setDueDate(request.getDueDate());
        expense.setSplitType(SplitType.EQUAL);
        expense.setCreatedById(userId);
        expense.setPaidById(paidById);
        expense.setRecurringDay(request.getRecurringDay());
        expense.setRecurrenceActive(recurrenceActive);
        if (request.getRecurringDay() != null && recurrenceActive) {
            // The original counts as the occurrence of its own month, so the recurrence job will not duplicate it.
            expense.setRecurrenceSourceId(expense.getId());
            expense.setRecurrenceMonth(firstRecurrenceMonth(expense.getDueDate()));
        }

        if (request.getAttachmentBase64() != null && !request.getAttachmentBase64().isBlank()) {
            String savedPath = saveAttachmentToDisk(expense.getId(), request.getAttachmentBase64());
            expense.setAttachmentUrl(savedPath);
        }

        Expense savedExpense = expenseRepository.save(expense);

        List<String> memberIds = members.stream().map(User::getId).toList();
        List<ExpenseSplit> splits = buildEqualSplits(
                savedExpense.getId(), totalAmount, memberIds, paidById, AppTime.nowUtc(clock));

        expenseSplitRepository.saveAll(splits);
        return toResponse(savedExpense, splits);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listExpenses(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        List<Expense> expenses = expenseRepository.findByHouseIdOrderByDueDateAsc(houseId);

        Map<String, List<ExpenseSplit>> splitsByExpense = new LinkedHashMap<>();
        Set<String> userIds = new HashSet<>();
        for (Expense expense : expenses) {
            List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expense.getId());
            splitsByExpense.put(expense.getId(), splits);
            userIds.add(expense.getPaidById());
            splits.forEach(s -> userIds.add(s.getUserId()));
        }
        Map<String, String> names = resolveNames(userIds);

        return expenses.stream()
                .map(exp -> toResponse(exp, splitsByExpense.get(exp.getId()), names))
                .collect(Collectors.toList());
    }

    public byte[] getAttachment(String userId, String houseId, String expenseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Expense expense = findExpenseInHouse(houseId, expenseId);

        if (expense.getAttachmentUrl() == null || expense.getAttachmentUrl().isBlank()) {
            throw new ResourceNotFoundException("Attachment not found");
        }

        Path path = Paths.get(expense.getAttachmentUrl());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("Attachment not found");
        }

        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ResourceNotFoundException("Could not read attachment");
        }
    }

    @Transactional
    public ExpenseResponse proposeCustomSplit(String userId, String houseId, String expenseId, ProposeSplitRequest request) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Expense expense = findExpenseInHouse(houseId, expenseId);

        if (request == null || request.getSplits() == null || request.getSplits().isEmpty()) {
            throw new BusinessRuleException("Split items are required");
        }

        BigDecimal sumInput = BigDecimal.ZERO;
        Set<String> seenUserIds = new HashSet<>();
        for (ProposeSplitRequest.SplitItem item : request.getSplits()) {
            if (item.getUserId() == null || item.getUserId().isBlank() || item.getAmountOrPercentage() == null) {
                throw new BusinessRuleException("Each split item must have userId and amountOrPercentage");
            }
            // Zero is allowed: it keeps someone out of this expense.
            if (item.getAmountOrPercentage().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException("Split values cannot be negative");
            }
            // A repeated user would pass the sum check but only one of its values would be stored.
            if (!seenUserIds.add(item.getUserId())) {
                throw new BusinessRuleException("Duplicate userId in split proposal: " + item.getUserId());
            }
            sumInput = sumInput.add(item.getAmountOrPercentage());
        }

        String mode = request.getSplitMode() != null ? request.getSplitMode().trim().toUpperCase() : null;
        boolean isPercentage = "PORCENTAGEM".equals(mode)
                || "PERCENTAGE".equals(mode)
                || (mode == null
                    && sumInput.compareTo(new BigDecimal("100")) == 0
                    && expense.getTotalAmount().compareTo(new BigDecimal("100")) != 0);

        Map<String, BigDecimal> proposedByUser = new HashMap<>();
        BigDecimal totalAmount = expense.getTotalAmount().setScale(2, RoundingMode.HALF_UP);

        if (isPercentage) {
            if (sumInput.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new BusinessRuleException(SPLIT_SUM_MESSAGE);
            }
            List<ProposeSplitRequest.SplitItem> items = request.getSplits();
            BigDecimal sumConverted = BigDecimal.ZERO;
            List<BigDecimal> calculatedAmounts = new ArrayList<>();
            for (ProposeSplitRequest.SplitItem item : items) {
                // Rounded down, so the leftover cents are never negative and 0% stays exactly 0.00.
                BigDecimal amount = totalAmount
                        .multiply(item.getAmountOrPercentage())
                        .divide(new BigDecimal("100"), 2, RoundingMode.DOWN);
                calculatedAmounts.add(amount);
                sumConverted = sumConverted.add(amount);
            }
            // The leftover cents go to the payer when they have a share, otherwise to the first
            // person with a share: someone excluded with 0% never absorbs them.
            BigDecimal remainder = totalAmount.subtract(sumConverted);
            int absorber = -1;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getAmountOrPercentage().signum() <= 0) {
                    continue;
                }
                if (Objects.equals(items.get(i).getUserId(), expense.getPaidById())) {
                    absorber = i;
                    break;
                }
                if (absorber < 0) {
                    absorber = i;
                }
            }
            for (int i = 0; i < items.size(); i++) {
                BigDecimal finalAmount = (i == absorber) ? calculatedAmounts.get(i).add(remainder) : calculatedAmounts.get(i);
                // Only reachable through the 0.01 sum tolerance (e.g. 100.01%) on a tiny share.
                if (finalAmount.signum() < 0) {
                    throw new BusinessRuleException(SPLIT_SUM_MESSAGE);
                }
                proposedByUser.put(items.get(i).getUserId(), finalAmount);
            }
        } else {
            // Values are stored with 2 decimals, so a fraction of a cent (e.g. 25.005) is rejected
            // and the sum is checked on the stored values: what is saved always adds up to the total.
            BigDecimal sumScaled = BigDecimal.ZERO;
            for (ProposeSplitRequest.SplitItem item : request.getSplits()) {
                BigDecimal raw = item.getAmountOrPercentage();
                BigDecimal scaled = raw.setScale(2, RoundingMode.HALF_UP);
                if (scaled.compareTo(raw) != 0) {
                    throw new BusinessRuleException(SPLIT_SUM_MESSAGE);
                }
                sumScaled = sumScaled.add(scaled);
                proposedByUser.put(item.getUserId(), scaled);
            }
            if (sumInput.compareTo(totalAmount) != 0 || sumScaled.compareTo(totalAmount) != 0) {
                throw new BusinessRuleException(SPLIT_SUM_MESSAGE);
            }
        }

        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);
        Map<String, ExpenseSplit> splitByUser = splits.stream()
                .collect(Collectors.toMap(ExpenseSplit::getUserId, s -> s));

        for (String targetUserId : proposedByUser.keySet()) {
            if (!splitByUser.containsKey(targetUserId)) {
                throw new BusinessRuleException("User does not belong to this expense split: " + targetUserId);
            }
        }

        for (ExpenseSplit split : splits) {
            BigDecimal proposed = proposedByUser.getOrDefault(
                    split.getUserId(),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            );
            split.setProposedAmountDue(proposed);
        }

        expenseSplitRepository.saveAll(splits);
        expenseSplitVoteRepository.deleteByExpenseId(expenseId);

        expense.setSplitType(SplitType.CUSTOM_PENDING);
        Expense savedExpense = expenseRepository.save(expense);
        return toResponse(savedExpense, splits);
    }

    @Transactional
    public ExpenseResponse voteOnCustomSplit(String userId, String houseId, String expenseId, VoteRequestDto voteDto) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Expense expense = findExpenseInHouse(houseId, expenseId);

        if (expense.getSplitType() != SplitType.CUSTOM_PENDING) {
            throw new BusinessRuleException("No custom split pending approval");
        }

        VoteType voteType = VoteType.fromString(voteDto != null ? voteDto.getVote() : null);

        ExpenseSplitVote vote = expenseSplitVoteRepository.findByExpenseIdAndVoterId(expenseId, userId)
                .orElseGet(() -> {
                    ExpenseSplitVote v = new ExpenseSplitVote();
                    v.setId(UUID.randomUUID().toString());
                    v.setExpenseId(expenseId);
                    v.setVoterId(userId);
                    return v;
                });
        vote.setVote(voteType);
        expenseSplitVoteRepository.save(vote);

        House house = houseRepository.findById(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("House not found: " + houseId));
        Set<String> memberIds = userRepository.findByHouseIdOrderByIdAsc(houseId).stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        int totalMembers = memberIds.size();

        // Votes from people who have since left the house no longer count.
        List<ExpenseSplitVote> currentVotes = expenseSplitVoteRepository.findByExpenseId(expenseId).stream()
                .filter(v -> memberIds.contains(v.getVoterId()))
                .toList();
        int approveCount = (int) currentVotes.stream().filter(v -> v.getVote() == VoteType.APPROVE).count();
        int rejectCount = (int) currentVotes.stream().filter(v -> v.getVote() == VoteType.REJECT).count();

        VotingOutcome outcome = votingEvaluator.evaluate(house.getVotingRule(), totalMembers, approveCount, rejectCount);
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);

        if (outcome == VotingOutcome.APPROVED) {
            for (ExpenseSplit split : splits) {
                if (split.getProposedAmountDue() != null) {
                    split.setAmountDue(split.getProposedAmountDue());
                    split.setProposedAmountDue(null);
                }
            }
            expenseSplitRepository.saveAll(splits);
            expense.setSplitType(SplitType.CUSTOM_APPROVED);
            expense = expenseRepository.save(expense);
        } else if (outcome == VotingOutcome.REJECTED) {
            for (ExpenseSplit split : splits) {
                split.setProposedAmountDue(null);
            }
            expenseSplitRepository.saveAll(splits);
            expense.setSplitType(SplitType.EQUAL);
            expense = expenseRepository.save(expense);
        }

        return toResponse(expense, splits);
    }

    @Transactional
    public ExpenseResponse markSplitAsPaid(String userId, String houseId, String expenseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Expense expense = findExpenseInHouse(houseId, expenseId);

        ExpenseSplit split = expenseSplitRepository.findByExpenseIdAndUserId(expenseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense split not found for user"));
        // A 0.00 share (excluded by a custom split) owes nothing: paying it would earn points for free.
        if (!hasAmountToPay(split)) {
            throw new BusinessRuleException(NOTHING_TO_PAY_MESSAGE);
        }

        // Paying twice keeps the original paidAt and never credits points again.
        if (!Boolean.TRUE.equals(split.getIsPaid())) {
            LocalDateTime now = AppTime.nowUtc(clock);
            boolean isPayer = Objects.equals(userId, expense.getPaidById());
            split.setIsPaid(true);
            split.setPaidAt(now);
            if (isPayer) {
                split.setReceivedConfirmedAt(now);
            }
            expenseSplitRepository.save(split);

            boolean onTime = !AppTime.today(clock).isAfter(expense.getDueDate());
            if (onTime && !isPayer) {
                pointService.creditIfAbsent(userId, houseId, PointReason.EXPENSE_ON_TIME, ON_TIME_POINTS,
                        PointSourceType.EXPENSE_SPLIT, split.getId(), "", expense.getTitle());
            }
        }

        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);
        return toResponse(expense, splits);
    }

    @Transactional
    public ExpenseResponse confirmReceived(String userId, String houseId, String expenseId, String splitId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Expense expense = findExpenseInHouse(houseId, expenseId);
        ExpenseSplit split = findSplitOfExpense(expenseId, splitId);
        requirePayer(userId, expense);
        if (!Boolean.TRUE.equals(split.getIsPaid())) {
            throw new BusinessRuleException(NOT_PAID_MESSAGE);
        }

        split.setReceivedConfirmedAt(AppTime.nowUtc(clock));
        expenseSplitRepository.save(split);

        return toResponse(expense, expenseSplitRepository.findByExpenseId(expenseId));
    }

    @Transactional
    public ExpenseResponse markNotReceived(String userId, String houseId, String expenseId, String splitId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        Expense expense = findExpenseInHouse(houseId, expenseId);
        ExpenseSplit split = findSplitOfExpense(expenseId, splitId);
        requirePayer(userId, expense);
        if (!Boolean.TRUE.equals(split.getIsPaid())) {
            throw new BusinessRuleException(NOT_PAID_MESSAGE);
        }
        if (Objects.equals(split.getUserId(), expense.getPaidById())) {
            throw new BusinessRuleException("the payer's own share cannot be reopened");
        }

        split.setIsPaid(false);
        split.setPaidAt(null);
        split.setReceivedConfirmedAt(null);
        split.setReopenedById(userId);
        split.setReopenedAt(AppTime.nowUtc(clock));
        expenseSplitRepository.save(split);
        pointService.removeByOrigin(PointSourceType.EXPENSE_SPLIT, split.getId(), "");

        return toResponse(expense, expenseSplitRepository.findByExpenseId(expenseId));
    }

    @Transactional
    public ExpenseResponse updateExpense(String userId, String houseId, String expenseId, UpdateExpenseRequest request) {
        User actor = houseAccessValidator.requireHouseMember(userId, houseId);
        Expense expense = findExpenseInHouse(houseId, expenseId);
        requireCanModify(actor, userId, expense);
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);
        requireNoPaymentsFromOthers(expense, splits);

        if (request == null) {
            throw new BusinessRuleException("Request body is required");
        }
        if (isGeneratedOccurrence(expense) && Boolean.TRUE.equals(request.getRecurrenceActive())) {
            throw new ConflictException(GENERATED_OCCURRENCE_MESSAGE);
        }
        if (request.getTitle() != null && request.getTitle().isBlank()) {
            throw new BusinessRuleException("Expense title is required");
        }
        if (request.getTotalAmount() != null
                && request.getTotalAmount().setScale(2, RoundingMode.HALF_UP).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(TOTAL_NOT_POSITIVE_MESSAGE);
        }
        validateRecurringDay(request.getRecurringDay());
        validateRecurrence(
                request.getRecurrenceActive() != null ? request.getRecurrenceActive() : expense.getRecurrenceActive(),
                request.getRecurringDay() != null ? request.getRecurringDay() : expense.getRecurringDay());

        String newPayer = isBlank(request.getPaidById()) ? expense.getPaidById() : request.getPaidById();
        boolean payerChanged = !Objects.equals(newPayer, expense.getPaidById());
        if (payerChanged) {
            boolean isMember = userRepository.findByHouseIdOrderByIdAsc(houseId).stream()
                    .anyMatch(m -> Objects.equals(m.getId(), newPayer));
            if (!isMember) {
                throw new BusinessRuleException(PAYER_NOT_MEMBER_MESSAGE);
            }
            if (splits.stream().noneMatch(s -> Objects.equals(s.getUserId(), newPayer))) {
                throw new BusinessRuleException("payer must be part of this expense split");
            }
        }
        BigDecimal newTotal = request.getTotalAmount() != null
                ? request.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                : expense.getTotalAmount();
        boolean totalChanged = newTotal.compareTo(expense.getTotalAmount()) != 0;

        if (request.getTitle() != null) {
            expense.setTitle(request.getTitle().trim());
        }
        if (request.getDueDate() != null) {
            expense.setDueDate(request.getDueDate());
        }
        expense.setTotalAmount(newTotal);
        expense.setPaidById(newPayer);
        if (request.getRecurringDay() != null) {
            expense.setRecurringDay(request.getRecurringDay());
        }
        if (request.getRecurrenceActive() != null) {
            expense.setRecurrenceActive(request.getRecurrenceActive());
        }
        if (Boolean.TRUE.equals(expense.getRecurrenceActive())
                && expense.getRecurringDay() != null
                && expense.getRecurrenceSourceId() == null) {
            expense.setRecurrenceSourceId(expense.getId());
            expense.setRecurrenceMonth(firstRecurrenceMonth(expense.getDueDate()));
        }

        boolean customSplit = expense.getSplitType() != SplitType.EQUAL;
        // Equal shares follow the payer (who absorbs the cents); custom amounts only reset when the total changes.
        boolean recomputeShares = !splits.isEmpty() && (totalChanged || (payerChanged && !customSplit));
        if (recomputeShares) {
            // Custom amounts no longer add up to the new total: fall back to an equal split.
            if (customSplit) {
                expense.setSplitType(SplitType.EQUAL);
                expenseSplitVoteRepository.deleteByExpenseId(expenseId);
            }
            List<String> splitUserIds = splits.stream()
                    .map(ExpenseSplit::getUserId)
                    .sorted()
                    .toList();
            Map<String, BigDecimal> shares = equalShares(newTotal, splitUserIds, newPayer);
            for (ExpenseSplit split : splits) {
                split.setAmountDue(shares.get(split.getUserId()));
                split.setProposedAmountDue(null);
            }
        }
        if (totalChanged || payerChanged) {
            LocalDateTime now = AppTime.nowUtc(clock);
            for (ExpenseSplit split : splits) {
                if (Objects.equals(split.getUserId(), newPayer)) {
                    if (!Boolean.TRUE.equals(split.getIsPaid())) {
                        markAsPayerShare(split, now);
                    }
                } else {
                    split.setIsPaid(false);
                    split.setPaidAt(null);
                    split.setReceivedConfirmedAt(null);
                }
            }
            expenseSplitRepository.saveAll(splits);
        }

        Expense saved = expenseRepository.save(expense);
        return toResponse(saved, splits);
    }

    @Transactional
    public void deleteExpense(String userId, String houseId, String expenseId) {
        User actor = houseAccessValidator.requireHouseMember(userId, houseId);
        Expense expense = findExpenseInHouse(houseId, expenseId);
        requireCanModify(actor, userId, expense);
        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);
        requireNoPaymentsFromOthers(expense, splits);

        expenseSplitVoteRepository.deleteByExpenseId(expenseId);
        expenseSplitRepository.deleteByExpenseId(expenseId);
        expenseRepository.delete(expense);

        if (expense.getAttachmentUrl() != null && !expense.getAttachmentUrl().isBlank()) {
            try {
                Files.deleteIfExists(Paths.get(expense.getAttachmentUrl()));
            } catch (IOException | RuntimeException ignored) {
                // An orphan receipt file must not undo the deletion.
            }
        }
    }

    @Transactional(readOnly = true)
    public ExpenseSummaryResponse getSummary(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);

        Map<String, Expense> expensesById = new LinkedHashMap<>();
        for (Expense expense : expenseRepository.findByHouseIdOrderByDueDateAsc(houseId)) {
            expensesById.put(expense.getId(), expense);
        }
        List<ExpenseSplit> splits = expensesById.isEmpty()
                ? List.of()
                : expenseSplitRepository.findByExpenseIdIn(expensesById.keySet());
        List<String> currentMemberIds = userRepository.findByHouseIdOrderByIdAsc(houseId).stream()
                .map(User::getId)
                .toList();

        Map<String, BigDecimal> debtsByCreditor = new LinkedHashMap<>();
        Map<String, BigDecimal> creditsByDebtor = new LinkedHashMap<>();
        Map<String, MemberTally> tallies = new LinkedHashMap<>();
        currentMemberIds.forEach(id -> tallies.put(id, new MemberTally()));

        for (ExpenseSplit split : splits) {
            // A 0.00 share owes nothing: no phantom debt and it does not count as a share to pay.
            if (!hasAmountToPay(split)) {
                continue;
            }
            Expense expense = expensesById.get(split.getExpenseId());
            String payerId = expense != null ? expense.getPaidById() : null;
            boolean paid = Boolean.TRUE.equals(split.getIsPaid());
            BigDecimal amount = split.getAmountDue();

            MemberTally tally = tallies.computeIfAbsent(split.getUserId(), id -> new MemberTally());
            tally.total++;
            if (paid) {
                tally.paid++;
            } else {
                tally.open = tally.open.add(amount);
            }

            // Legacy expenses without a payer have no creditor to settle with.
            if (paid || payerId == null || Objects.equals(payerId, split.getUserId())) {
                continue;
            }
            if (Objects.equals(split.getUserId(), userId)) {
                debtsByCreditor.merge(payerId, amount, BigDecimal::add);
            }
            if (Objects.equals(payerId, userId)) {
                creditsByDebtor.merge(split.getUserId(), amount, BigDecimal::add);
            }
        }

        Set<String> currentMembers = new HashSet<>(currentMemberIds);
        // Former residents only show up while they still have something open.
        tallies.entrySet().removeIf(e -> !currentMembers.contains(e.getKey())
                && e.getValue().open.compareTo(BigDecimal.ZERO) <= 0);

        Set<String> ids = new LinkedHashSet<>(tallies.keySet());
        ids.addAll(debtsByCreditor.keySet());
        ids.addAll(creditsByDebtor.keySet());
        Map<String, String> names = resolveNames(ids);

        List<ExpenseSummaryResponse.Debt> myDebts = debtsByCreditor.entrySet().stream()
                .map(e -> new ExpenseSummaryResponse.Debt(e.getKey(), names.get(e.getKey()), e.getValue()))
                .toList();
        List<ExpenseSummaryResponse.Credit> owedToMe = creditsByDebtor.entrySet().stream()
                .map(e -> new ExpenseSummaryResponse.Credit(e.getKey(), names.get(e.getKey()), e.getValue()))
                .toList();
        List<ExpenseSummaryResponse.MemberBalance> members = tallies.entrySet().stream()
                .map(e -> new ExpenseSummaryResponse.MemberBalance(
                        e.getKey(), names.get(e.getKey()), e.getValue().paid, e.getValue().total, e.getValue().open))
                .toList();
        return new ExpenseSummaryResponse(myDebts, owedToMe, members);
    }

    private static final class MemberTally {
        int paid;
        int total;
        BigDecimal open = BigDecimal.ZERO.setScale(2);
    }

    private void validateRecurringDay(Integer recurringDay) {
        if (recurringDay != null && (recurringDay < 1 || recurringDay > 31)) {
            throw new BusinessRuleException(RECURRING_DAY_MESSAGE);
        }
    }

    private void validateRecurrence(Boolean recurrenceActive, Integer recurringDay) {
        if (Boolean.TRUE.equals(recurrenceActive) && recurringDay == null) {
            throw new BusinessRuleException(RECURRING_DAY_REQUIRED_MESSAGE);
        }
    }

    private static boolean hasAmountToPay(ExpenseSplit split) {
        return split.getAmountDue() != null && split.getAmountDue().signum() > 0;
    }

    private void requirePayer(String userId, Expense expense) {
        // Legacy rows have no payer, so nobody can confirm them.
        if (expense.getPaidById() == null || !Objects.equals(expense.getPaidById(), userId)) {
            throw new ForbiddenOperationException(ONLY_PAYER_MESSAGE);
        }
    }

    private void requireCanModify(User actor, String userId, Expense expense) {
        boolean allowed = expense.getCreatedById() == null
                ? actor != null && Boolean.TRUE.equals(actor.getIsHouseAdmin())
                : Objects.equals(expense.getCreatedById(), userId);
        if (!allowed) {
            throw new ForbiddenOperationException(ONLY_CREATOR_MESSAGE);
        }
    }

    private void requireNoPaymentsFromOthers(Expense expense, List<ExpenseSplit> splits) {
        boolean othersPaid = splits.stream()
                .anyMatch(s -> Boolean.TRUE.equals(s.getIsPaid())
                        && hasAmountToPay(s)
                        && !Objects.equals(s.getUserId(), expense.getPaidById()));
        if (othersPaid) {
            throw new ConflictException(HAS_PAYMENTS_MESSAGE);
        }
    }

    /**
     * First month the recurrence covers: the later of the current month and the due date's month, so an
     * expense created on 26/11 and due on 05/12 already stands for December and the job does not repeat it.
     */
    String firstRecurrenceMonth(LocalDate dueDate) {
        YearMonth current = YearMonth.from(AppTime.today(clock));
        YearMonth due = YearMonth.from(dueDate);
        return (due.isAfter(current) ? due : current).toString();
    }

    /** An occurrence created by the recurrence job points at its template, never at itself. */
    static boolean isGeneratedOccurrence(Expense expense) {
        return expense.getRecurrenceSourceId() != null
                && !expense.getRecurrenceSourceId().equals(expense.getId());
    }

    /** Equal splits among the given members, with the payer's share already settled. */
    static List<ExpenseSplit> buildEqualSplits(
            String expenseId, BigDecimal total, List<String> memberIds, String payerId, LocalDateTime now) {
        Map<String, BigDecimal> shares = equalShares(total, memberIds, payerId);
        List<ExpenseSplit> splits = new ArrayList<>();
        for (String memberId : memberIds) {
            ExpenseSplit split = new ExpenseSplit();
            split.setId(UUID.randomUUID().toString());
            split.setExpenseId(expenseId);
            split.setUserId(memberId);
            split.setAmountDue(shares.get(memberId));
            split.setIsPaid(false);
            if (Objects.equals(memberId, payerId)) {
                markAsPayerShare(split, now);
            }
            splits.add(split);
        }
        return splits;
    }

    /** The payer fronted the whole bill, so their own share is settled the moment it exists. */
    private static void markAsPayerShare(ExpenseSplit split, LocalDateTime now) {
        split.setIsPaid(true);
        split.setPaidAt(now);
        split.setReceivedConfirmedAt(now);
    }

    /**
     * Equal shares rounded down to the cent; the leftover cents go to the payer (or to the first
     * participant when the payer is not part of the split).
     */
    private static Map<String, BigDecimal> equalShares(BigDecimal total, List<String> userIds, String payerId) {
        BigDecimal count = BigDecimal.valueOf(userIds.size());
        BigDecimal baseShare = total.divide(count, 2, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(baseShare.multiply(count));
        String absorber = userIds.contains(payerId) ? payerId : userIds.get(0);

        Map<String, BigDecimal> shares = new LinkedHashMap<>();
        for (String id : userIds) {
            shares.put(id, id.equals(absorber) ? baseShare.add(remainder) : baseShare);
        }
        return shares;
    }

    private ExpenseSplit findSplitOfExpense(String expenseId, String splitId) {
        return expenseSplitRepository.findById(splitId)
                .filter(s -> expenseId.equals(s.getExpenseId()))
                .orElseThrow(() -> new ResourceNotFoundException("Expense split not found: " + splitId));
    }

    private Expense findExpenseInHouse(String houseId, String expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));
        if (!houseId.equals(expense.getHouseId())) {
            throw new ResourceNotFoundException("Expense not found in this house");
        }
        return expense;
    }

    private Map<String, String> resolveNames(Collection<String> userIds) {
        Set<String> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            names.put(user.getId(), user.getName());
        }
        return names;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String saveAttachmentToDisk(String expenseId, String base64Content) {
        String raw = base64Content.trim();
        int commaIndex = raw.indexOf(',');
        if (raw.startsWith("data:") && commaIndex != -1) {
            raw = raw.substring(commaIndex + 1).trim();
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            bytes = raw.getBytes(StandardCharsets.UTF_8);
        }

        String baseDir = (uploadDir != null && !uploadDir.isBlank()) ? uploadDir : "uploads";
        Path filePath = Paths.get(baseDir, "expenses", expenseId);
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, bytes);
            return filePath.toString();
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to save expense attachment");
        }
    }

    ExpenseResponse toResponse(Expense expense, List<ExpenseSplit> splits) {
        Set<String> ids = new HashSet<>();
        ids.add(expense.getPaidById());
        splits.forEach(s -> ids.add(s.getUserId()));
        return toResponse(expense, splits, resolveNames(ids));
    }

    private ExpenseResponse toResponse(Expense expense, List<ExpenseSplit> splits, Map<String, String> names) {
        ExpenseResponse response = new ExpenseResponse();
        response.setId(expense.getId());
        response.setHouseId(expense.getHouseId());
        response.setTitle(expense.getTitle());
        response.setTotalAmount(expense.getTotalAmount());
        response.setDueDate(expense.getDueDate());
        response.setHasAttachment(expense.getAttachmentUrl() != null && !expense.getAttachmentUrl().isBlank());
        response.setSplitType(expense.getSplitType());
        response.setPaidBy(expense.getPaidById() != null
                ? new UserRefDto(expense.getPaidById(), names.get(expense.getPaidById()))
                : null);
        response.setCreatedById(expense.getCreatedById());
        response.setRecurringDay(expense.getRecurringDay());
        response.setRecurrenceActive(Boolean.TRUE.equals(expense.getRecurrenceActive()));

        List<ExpenseSplitDto> splitDtos = splits.stream()
                .map(s -> {
                    ExpenseSplitDto dto = new ExpenseSplitDto();
                    dto.setId(s.getId());
                    dto.setUserId(s.getUserId());
                    dto.setUserName(names.get(s.getUserId()));
                    dto.setAmountDue(s.getAmountDue());
                    dto.setProposedAmountDue(s.getProposedAmountDue());
                    dto.setIsPaid(s.getIsPaid());
                    dto.setPaidAt(s.getPaidAt());
                    dto.setReceivedConfirmedAt(s.getReceivedConfirmedAt());
                    return dto;
                }).collect(Collectors.toList());

        response.setSplits(splitDtos);
        return response;
    }
}
