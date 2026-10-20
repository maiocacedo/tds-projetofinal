package com.nossoteto.service;

import com.nossoteto.dto.ChoreResponse;
import com.nossoteto.dto.DashboardResponse;
import com.nossoteto.dto.ExpenseSplitDto;
import com.nossoteto.dto.GroceryResponse;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Expense;
import com.nossoteto.model.ExpenseSplit;
import com.nossoteto.model.House;
import com.nossoteto.model.enums.MatchStatus;
import com.nossoteto.repository.ExpenseRepository;
import com.nossoteto.repository.ExpenseSplitRepository;
import com.nossoteto.repository.GroceryRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.MatchRequestRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final HouseRepository houseRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final ChoreService choreService;
    private final GroceryRepository groceryRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final HouseAccessValidator houseAccessValidator;

    public DashboardService(
            HouseRepository houseRepository,
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            ChoreService choreService,
            GroceryRepository groceryRepository,
            MatchRequestRepository matchRequestRepository,
            HouseAccessValidator houseAccessValidator) {
        this.houseRepository = houseRepository;
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.choreService = choreService;
        this.groceryRepository = groceryRepository;
        this.matchRequestRepository = matchRequestRepository;
        this.houseAccessValidator = houseAccessValidator;
    }

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
                        if (!Boolean.TRUE.equals(split.getIsPaid())) {
                            ExpenseSplitDto dto = new ExpenseSplitDto();
                            dto.setId(split.getId());
                            dto.setUserId(split.getUserId());
                            dto.setAmountDue(split.getAmountDue());
                            dto.setProposedAmountDue(split.getProposedAmountDue());
                            dto.setIsPaid(split.getIsPaid());
                            pendingSplits.add(dto);
                        }
                    });
        }

        for (ExpenseSplitDto splitDto : pendingSplits) {
            if (splitDto.getAmountDue() != null) {
                totalPending = totalPending.add(splitDto.getAmountDue());
            }
        }

        List<ChoreResponse> houseChores = choreService.listChores(userId, houseId);
        List<GroceryResponse> pendingGroceries = groceryRepository.findByHouseIdAndIsPurchasedFalse(houseId)
                .stream()
                .map(GroceryResponse::fromEntity)
                .collect(Collectors.toList());

        int pendingMatches = matchRequestRepository
                .findByHouseIdAndStatusOrderByCreatedAtAsc(houseId, MatchStatus.PENDING)
                .size();

        DashboardResponse response = new DashboardResponse();
        response.setHouseId(house.getId());
        response.setHouseName(house.getName());
        response.setTotalPendingAmountForUser(totalPending);
        response.setPendingSplitsForUser(pendingSplits);
        response.setHouseChores(houseChores);
        response.setPendingGroceries(pendingGroceries);
        response.setPendingMatchRequestsCount(pendingMatches);
        return response;
    }
}
