package com.nossoteto.service;

import com.nossoteto.dto.ChoreResponse;
import com.nossoteto.dto.DashboardPointsDto;
import com.nossoteto.dto.DashboardResponse;
import com.nossoteto.dto.DelayedChoreSummaryDto;
import com.nossoteto.dto.ExpenseSplitDto;
import com.nossoteto.dto.GroceryResponse;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Expense;
import com.nossoteto.model.Grocery;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.MatchStatus;
import com.nossoteto.repository.ExpenseRepository;
import com.nossoteto.repository.ExpenseSplitRepository;
import com.nossoteto.repository.GroceryRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.MatchRequestRepository;
import com.nossoteto.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DashboardService {

    private final HouseRepository houseRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ChoreService choreService;
    private final GroceryRepository groceryRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final PointService pointService;
    private final UserRepository userRepository;

    public DashboardService(
            HouseRepository houseRepository,
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            ChoreService choreService,
            GroceryRepository groceryRepository,
            MatchRequestRepository matchRequestRepository,
            HouseAccessValidator houseAccessValidator,
            PointService pointService,
            UserRepository userRepository) {
        this.houseRepository = houseRepository;
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.choreService = choreService;
        this.groceryRepository = groceryRepository;
        this.matchRequestRepository = matchRequestRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.pointService = pointService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getHouseDashboard(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);

        House house = houseRepository.findById(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("House not found: " + houseId));

        List<Expense> expenses = expenseRepository.findByHouseIdOrderByDueDateAsc(houseId);
        List<ExpenseSplitDto> pendingSplits = new ArrayList<>();
        BigDecimal totalPending = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Expense expense : expenses) {
            expenseSplitRepository.findByExpenseIdAndUserId(expense.getId(), userId)
                    .ifPresent(split -> {
                        if (!Boolean.TRUE.equals(split.getIsPaid())
                                && split.getAmountDue() != null
                                && split.getAmountDue().compareTo(BigDecimal.ZERO) > 0) {
                            ExpenseSplitDto dto = new ExpenseSplitDto();
                            dto.setId(split.getId());
                            dto.setUserId(split.getUserId());
                            dto.setAmountDue(split.getAmountDue());
                            dto.setProposedAmountDue(split.getProposedAmountDue());
                            dto.setIsPaid(split.getIsPaid());
                            dto.setExpenseId(expense.getId());
                            dto.setDueDate(expense.getDueDate());
                            dto.setExpenseTitle(expense.getTitle());
                            pendingSplits.add(dto);
                        }
                    });
        }

        for (ExpenseSplitDto splitDto : pendingSplits) {
            totalPending = totalPending.add(splitDto.getAmountDue());
        }

        List<ChoreResponse> houseChores = choreService.listChores(userId, houseId);
        List<DelayedChoreSummaryDto> delayedChores = houseChores.stream()
                .filter(chore -> chore.getStatus() == ChoreStatus.DELAYED)
                .map(chore -> new DelayedChoreSummaryDto(
                        chore.getId(),
                        chore.getDescription(),
                        chore.getEffectiveAssigneeName() != null
                                ? chore.getEffectiveAssigneeName()
                                : chore.getCurrentAssigneeName()))
                .toList();

        int mineThisMonth = pointService.getUserPointsSummary(userId).thisMonth();
        int houseThisMonth = pointService.getHouseMonthlyPoints(houseId);
        DashboardPointsDto points = new DashboardPointsDto(mineThisMonth, houseThisMonth);

        List<Grocery> unpurchasedGroceries = groceryRepository.findByHouseIdAndIsPurchasedFalse(houseId);
        Map<String, String> userNames = resolveGroceryUserNames(unpurchasedGroceries);
        List<GroceryResponse> pendingGroceries = unpurchasedGroceries.stream()
                .map(grocery -> GroceryResponse.fromEntity(grocery, userNames))
                .toList();

        int pendingMatches = matchRequestRepository
                .findByHouseIdAndStatusOrderByCreatedAtAsc(houseId, MatchStatus.PENDING)
                .size();

        DashboardResponse response = new DashboardResponse();
        response.setHouseId(house.getId());
        response.setHouseName(house.getName());
        response.setTotalPendingAmountForUser(totalPending);
        response.setPendingSplitsForUser(pendingSplits);
        response.setHouseChores(houseChores);
        response.setDelayedChores(delayedChores);
        response.setPoints(points);
        response.setPendingGroceries(pendingGroceries);
        response.setPendingMatchRequestsCount(pendingMatches);
        return response;
    }

    private Map<String, String> resolveGroceryUserNames(List<Grocery> groceries) {
        Set<String> userIds = new HashSet<>();
        for (Grocery grocery : groceries) {
            if (grocery.getCreatedById() != null && !grocery.getCreatedById().isBlank()) {
                userIds.add(grocery.getCreatedById());
            }
            if (grocery.getAssigneeId() != null && !grocery.getAssigneeId().isBlank()) {
                userIds.add(grocery.getAssigneeId());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            names.put(user.getId(), user.getName());
        }
        return names;
    }
}
