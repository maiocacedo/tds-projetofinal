package com.nossoteto.service;

import com.nossoteto.dto.CreateExpenseRequest;
import com.nossoteto.dto.ExpenseResponse;
import com.nossoteto.dto.ExpenseSplitDto;
import com.nossoteto.dto.ProposeSplitRequest;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Expense;
import com.nossoteto.model.ExpenseSplit;
import com.nossoteto.model.ExpenseSplitVote;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.SplitType;
import com.nossoteto.model.enums.VoteType;
import com.nossoteto.model.enums.VotingOutcome;
import com.nossoteto.repository.ExpenseRepository;
import com.nossoteto.repository.ExpenseSplitRepository;
import com.nossoteto.repository.ExpenseSplitVoteRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserRepository;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ExpenseSplitVoteRepository expenseSplitVoteRepository;
    private final UserRepository userRepository;
    private final HouseRepository houseRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final VotingEvaluator votingEvaluator;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir = "uploads";

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            ExpenseSplitVoteRepository expenseSplitVoteRepository,
            UserRepository userRepository,
            HouseRepository houseRepository,
            HouseAccessValidator houseAccessValidator,
            VotingEvaluator votingEvaluator) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.expenseSplitVoteRepository = expenseSplitVoteRepository;
        this.userRepository = userRepository;
        this.houseRepository = houseRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.votingEvaluator = votingEvaluator;
    }

    @Transactional
    public ExpenseResponse createExpense(String userId, String houseId, CreateExpenseRequest request) {
        houseAccessValidator.requireHouseMember(userId, houseId);

        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessRuleException("Expense title is required");
        }
        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Expense total amount must be greater than zero");
        }
        if (request.getDueDate() == null) {
            throw new BusinessRuleException("Expense due date is required");
        }

        List<User> members = userRepository.findByHouseIdOrderByIdAsc(houseId);
        if (members.isEmpty()) {
            throw new BusinessRuleException("House has no members");
        }

        BigDecimal totalAmount = request.getTotalAmount().setScale(2, RoundingMode.HALF_UP);

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID().toString());
        expense.setHouseId(houseId);
        expense.setTitle(request.getTitle().trim());
        expense.setTotalAmount(totalAmount);
        expense.setDueDate(request.getDueDate());
        expense.setSplitType(SplitType.EQUAL);

        if (request.getAttachmentBase64() != null && !request.getAttachmentBase64().isBlank()) {
            String savedPath = saveAttachmentToDisk(expense.getId(), request.getAttachmentBase64());
            expense.setAttachmentUrl(savedPath);
        }

        Expense savedExpense = expenseRepository.save(expense);

        BigDecimal count = BigDecimal.valueOf(members.size());
        BigDecimal baseShare = totalAmount.divide(count, 2, RoundingMode.HALF_UP);
        BigDecimal remainder = totalAmount.subtract(baseShare.multiply(count));

        List<ExpenseSplit> splits = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            User member = members.get(i);
            BigDecimal share = (i == 0) ? baseShare.add(remainder) : baseShare;

            ExpenseSplit split = new ExpenseSplit();
            split.setId(UUID.randomUUID().toString());
            split.setExpenseId(savedExpense.getId());
            split.setUserId(member.getId());
            split.setAmountDue(share);
            split.setIsPaid(false);
            splits.add(split);
        }

        expenseSplitRepository.saveAll(splits);
        return toResponse(savedExpense, splits);
    }

    public List<ExpenseResponse> listExpenses(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        List<Expense> expenses = expenseRepository.findByHouseIdOrderByDueDateAsc(houseId);
        return expenses.stream()
                .map(exp -> toResponse(exp, expenseSplitRepository.findByExpenseId(exp.getId())))
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
        for (ProposeSplitRequest.SplitItem item : request.getSplits()) {
            if (item.getUserId() == null || item.getUserId().isBlank() || item.getAmountOrPercentage() == null) {
                throw new BusinessRuleException("Each split item must have userId and amountOrPercentage");
            }
            if (item.getAmountOrPercentage().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException("Split values cannot be negative");
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
                throw new BusinessRuleException("Proposed split percentages must sum to 100");
            }
            BigDecimal sumConverted = BigDecimal.ZERO;
            List<BigDecimal> calculatedAmounts = new ArrayList<>();
            for (ProposeSplitRequest.SplitItem item : request.getSplits()) {
                BigDecimal amount = totalAmount
                        .multiply(item.getAmountOrPercentage())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                calculatedAmounts.add(amount);
                sumConverted = sumConverted.add(amount);
            }
            BigDecimal remainder = totalAmount.subtract(sumConverted);
            for (int i = 0; i < request.getSplits().size(); i++) {
                BigDecimal finalAmount = (i == 0) ? calculatedAmounts.get(i).add(remainder) : calculatedAmounts.get(i);
                proposedByUser.put(request.getSplits().get(i).getUserId(), finalAmount);
            }
        } else {
            if (sumInput.compareTo(totalAmount) != 0) {
                throw new BusinessRuleException("Proposed split amounts must sum to total expense amount");
            }
            for (ProposeSplitRequest.SplitItem item : request.getSplits()) {
                proposedByUser.put(item.getUserId(), item.getAmountOrPercentage().setScale(2, RoundingMode.HALF_UP));
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
        int totalMembers = (int) userRepository.countByHouseId(houseId);

        List<ExpenseSplitVote> allVotes = expenseSplitVoteRepository.findByExpenseId(expenseId);
        int approveCount = (int) allVotes.stream().filter(v -> v.getVote() == VoteType.APPROVE).count();
        int rejectCount = (int) allVotes.stream().filter(v -> v.getVote() == VoteType.REJECT).count();

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

        split.setIsPaid(true);
        expenseSplitRepository.save(split);

        List<ExpenseSplit> splits = expenseSplitRepository.findByExpenseId(expenseId);
        return toResponse(expense, splits);
    }

    private Expense findExpenseInHouse(String houseId, String expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found: " + expenseId));
        if (!houseId.equals(expense.getHouseId())) {
            throw new ResourceNotFoundException("Expense not found in this house");
        }
        return expense;
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

    private ExpenseResponse toResponse(Expense expense, List<ExpenseSplit> splits) {
        ExpenseResponse response = new ExpenseResponse();
        response.setId(expense.getId());
        response.setHouseId(expense.getHouseId());
        response.setTitle(expense.getTitle());
        response.setTotalAmount(expense.getTotalAmount());
        response.setDueDate(expense.getDueDate());
        response.setHasAttachment(expense.getAttachmentUrl() != null && !expense.getAttachmentUrl().isBlank());
        response.setSplitType(expense.getSplitType());

        List<ExpenseSplitDto> splitDtos = splits.stream().map(s -> {
            ExpenseSplitDto dto = new ExpenseSplitDto();
            dto.setId(s.getId());
            dto.setUserId(s.getUserId());
            dto.setAmountDue(s.getAmountDue());
            dto.setProposedAmountDue(s.getProposedAmountDue());
            dto.setIsPaid(s.getIsPaid());
            return dto;
        }).collect(Collectors.toList());

        response.setSplits(splitDtos);
        return response;
    }
}
