package com.nossoteto.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nossoteto.dto.ChoreResponse;
import com.nossoteto.dto.DashboardPointsDto;
import com.nossoteto.dto.DashboardResponse;
import com.nossoteto.dto.DelayedChoreSummaryDto;
import com.nossoteto.dto.ExpenseSplitDto;
import com.nossoteto.dto.GroceryResponse;
import com.nossoteto.dto.PointsSummaryDto;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Expense;
import com.nossoteto.model.ExpenseSplit;
import com.nossoteto.model.Grocery;
import com.nossoteto.model.House;
import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.MatchStatus;
import com.nossoteto.repository.ExpenseRepository;
import com.nossoteto.repository.ExpenseSplitRepository;
import com.nossoteto.repository.GroceryRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.MatchRequestRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private HouseRepository houseRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseSplitRepository expenseSplitRepository;

    @Mock
    private ChoreService choreService;

    @Mock
    private GroceryRepository groceryRepository;

    @Mock
    private MatchRequestRepository matchRequestRepository;

    @Mock
    private HouseAccessValidator houseAccessValidator;

    @Mock
    private PointService pointService;

    @Mock
    private UserRepository userRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                houseRepository,
                expenseRepository,
                expenseSplitRepository,
                choreService,
                groceryRepository,
                matchRequestRepository,
                houseAccessValidator,
                pointService,
                userRepository);
        lenient().when(pointService.getUserPointsSummary("u1")).thenReturn(new PointsSummaryDto(0, 0));
        lenient().when(pointService.getHouseMonthlyPoints("h1")).thenReturn(0);
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    private House house(String id, String name) {
        House h = new House();
        h.setId(id);
        h.setName(name);
        return h;
    }

    private Expense expense(String id, String title, LocalDate dueDate) {
        Expense e = new Expense();
        e.setId(id);
        e.setHouseId("h1");
        e.setTitle(title);
        e.setDueDate(dueDate);
        e.setTotalAmount(new BigDecimal("100.00"));
        return e;
    }

    private ExpenseSplit split(String id, String expenseId, String userId, String amount, boolean paid) {
        ExpenseSplit s = new ExpenseSplit();
        s.setId(id);
        s.setExpenseId(expenseId);
        s.setUserId(userId);
        s.setAmountDue(amount == null ? null : new BigDecimal(amount));
        s.setIsPaid(paid);
        return s;
    }

    private ChoreResponse chore(
            String id,
            String description,
            ChoreStatus status,
            String currentAssigneeName,
            String effectiveAssigneeName) {
        ChoreResponse c = new ChoreResponse();
        c.setId(id);
        c.setHouseId("h1");
        c.setDescription(description);
        c.setStatus(status);
        c.setCurrentAssigneeName(currentAssigneeName);
        c.setEffectiveAssigneeName(effectiveAssigneeName);
        return c;
    }

    private Grocery grocery(String id, String itemName, String createdById, String assigneeId) {
        Grocery g = new Grocery();
        g.setId(id);
        g.setHouseId("h1");
        g.setItemName(itemName);
        g.setQuantity(2);
        g.setIsPurchased(false);
        g.setCreatedById(createdById);
        g.setAssigneeId(assigneeId);
        return g;
    }

    private User user(String id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        return u;
    }

    @Test
    void getHouseDashboard_EnrichesPendingSplitsWithDueDateAndTitleIgnoringPaidAndZero() {
        when(houseRepository.findById("h1")).thenReturn(Optional.of(house("h1", "Casa Verde")));

        Expense earliest = expense("e1", "Internet", LocalDate.of(2026, 11, 28));
        Expense paid = expense("e2", "Água", LocalDate.of(2026, 11, 29));
        Expense zeroed = expense("e3", "Churrasco", LocalDate.of(2026, 11, 30));
        Expense later = expense("e4", "Luz", LocalDate.of(2026, 12, 5));

        when(expenseRepository.findByHouseIdOrderByDueDateAsc("h1"))
                .thenReturn(List.of(earliest, paid, zeroed, later));
        when(expenseSplitRepository.findByExpenseIdAndUserId("e1", "u1"))
                .thenReturn(Optional.of(split("s1", "e1", "u1", "45.50", false)));
        when(expenseSplitRepository.findByExpenseIdAndUserId("e2", "u1"))
                .thenReturn(Optional.of(split("s2", "e2", "u1", "30.00", true)));
        when(expenseSplitRepository.findByExpenseIdAndUserId("e3", "u1"))
                .thenReturn(Optional.of(split("s3", "e3", "u1", "0.00", false)));
        when(expenseSplitRepository.findByExpenseIdAndUserId("e4", "u1"))
                .thenReturn(Optional.of(split("s4", "e4", "u1", "104.25", false)));
        when(choreService.listChores("u1", "h1")).thenReturn(List.of());
        when(groceryRepository.findByHouseIdAndIsPurchasedFalse("h1")).thenReturn(List.of());
        when(matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc("h1", MatchStatus.PENDING))
                .thenReturn(List.of());

        DashboardResponse response = dashboardService.getHouseDashboard("u1", "h1");

        verify(houseAccessValidator).requireHouseMember("u1", "h1");
        verify(expenseRepository, times(1)).findByHouseIdOrderByDueDateAsc("h1");
        assertEquals(new BigDecimal("149.75"), response.getTotalPendingAmountForUser());
        assertEquals(2, response.getPendingSplitsForUser().size());

        ExpenseSplitDto first = response.getPendingSplitsForUser().get(0);
        assertEquals("s1", first.getId());
        assertEquals("e1", first.getExpenseId());
        assertEquals(LocalDate.of(2026, 11, 28), first.getDueDate());
        assertEquals("Internet", first.getExpenseTitle());
        assertEquals(new BigDecimal("45.50"), first.getAmountDue());

        ExpenseSplitDto second = response.getPendingSplitsForUser().get(1);
        assertEquals("s4", second.getId());
        assertEquals("e4", second.getExpenseId());
        assertEquals(LocalDate.of(2026, 12, 5), second.getDueDate());
        assertEquals("Luz", second.getExpenseTitle());
        assertEquals(new BigDecimal("104.25"), second.getAmountDue());
    }

    @Test
    void getHouseDashboard_ExtractsDelayedChoresFromSameChoreListPreservingHouseChores() {
        when(houseRepository.findById("h1")).thenReturn(Optional.of(house("h1", "Casa Verde")));
        when(expenseRepository.findByHouseIdOrderByDueDateAsc("h1")).thenReturn(List.of());

        ChoreResponse pending = chore("c1", "Lavar louça", ChoreStatus.PENDING, "Ana", "Ana");
        ChoreResponse delayedWithEffective = chore("c2", "Tirar lixo", ChoreStatus.DELAYED, "Ana", "Beto");
        ChoreResponse delayedFallback = chore("c3", "Limpar sala", ChoreStatus.DELAYED, "Carla", null);
        ChoreResponse completed = chore("c4", "Varrer quintal", ChoreStatus.COMPLETED, "Ana", "Ana");

        when(choreService.listChores("u1", "h1"))
                .thenReturn(List.of(pending, delayedWithEffective, delayedFallback, completed));
        when(groceryRepository.findByHouseIdAndIsPurchasedFalse("h1")).thenReturn(List.of());
        when(matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc("h1", MatchStatus.PENDING))
                .thenReturn(List.of());

        DashboardResponse response = dashboardService.getHouseDashboard("u1", "h1");

        verify(choreService, times(1)).listChores("u1", "h1");
        assertEquals(4, response.getHouseChores().size());
        assertEquals(2, response.getDelayedChores().size());

        DelayedChoreSummaryDto firstDelayed = response.getDelayedChores().get(0);
        assertEquals("c2", firstDelayed.id());
        assertEquals("Tirar lixo", firstDelayed.description());
        assertEquals("Beto", firstDelayed.assigneeName());

        DelayedChoreSummaryDto secondDelayed = response.getDelayedChores().get(1);
        assertEquals("c3", secondDelayed.id());
        assertEquals("Limpar sala", secondDelayed.description());
        assertEquals("Carla", secondDelayed.assigneeName());
    }

    @Test
    void getHouseDashboard_PopulatesMonthlyPointsWithoutRankingAndMatchCount() {
        when(houseRepository.findById("h1")).thenReturn(Optional.of(house("h1", "Casa Verde")));
        when(expenseRepository.findByHouseIdOrderByDueDateAsc("h1")).thenReturn(List.of());
        when(choreService.listChores("u1", "h1")).thenReturn(List.of());
        when(groceryRepository.findByHouseIdAndIsPurchasedFalse("h1")).thenReturn(List.of());
        when(matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc("h1", MatchStatus.PENDING))
                .thenReturn(List.of(new MatchRequest(), new MatchRequest()));
        when(pointService.getUserPointsSummary("u1")).thenReturn(new PointsSummaryDto(120, 35));
        when(pointService.getHouseMonthlyPoints("h1")).thenReturn(85);

        DashboardResponse response = dashboardService.getHouseDashboard("u1", "h1");

        DashboardPointsDto points = response.getPoints();
        assertNotNull(points);
        assertEquals(35, points.mineThisMonth());
        assertEquals(85, points.houseThisMonth());
        assertEquals(2, response.getPendingMatchRequestsCount());
    }

    @Test
    void getHouseDashboard_EnrichesUnpurchasedGroceriesWithUserRefsInBatch() {
        when(houseRepository.findById("h1")).thenReturn(Optional.of(house("h1", "Casa Verde")));
        when(expenseRepository.findByHouseIdOrderByDueDateAsc("h1")).thenReturn(List.of());
        when(choreService.listChores("u1", "h1")).thenReturn(List.of());
        when(matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc("h1", MatchStatus.PENDING))
                .thenReturn(List.of());

        Grocery g1 = grocery("g1", "Café", "u1", "u2");
        Grocery g2 = grocery("g2", "Leite", "u2", null);
        when(groceryRepository.findByHouseIdAndIsPurchasedFalse("h1")).thenReturn(List.of(g1, g2));
        when(userRepository.findAllById(Set.of("u1", "u2")))
                .thenReturn(List.of(user("u1", "Ana"), user("u2", "Beto")));

        DashboardResponse response = dashboardService.getHouseDashboard("u1", "h1");

        verify(groceryRepository, times(1)).findByHouseIdAndIsPurchasedFalse("h1");
        verify(groceryRepository, never()).findVisibleForHouse(any(), any());
        verify(userRepository, times(1)).findAllById(Set.of("u1", "u2"));

        assertEquals(2, response.getPendingGroceries().size());
        GroceryResponse first = response.getPendingGroceries().get(0);
        assertEquals("g1", first.getId());
        assertNotNull(first.getCreatedBy());
        assertEquals("u1", first.getCreatedBy().id());
        assertEquals("Ana", first.getCreatedBy().name());
        assertNotNull(first.getAssignee());
        assertEquals("u2", first.getAssignee().id());
        assertEquals("Beto", first.getAssignee().name());

        GroceryResponse second = response.getPendingGroceries().get(1);
        assertEquals("g2", second.getId());
        assertNotNull(second.getCreatedBy());
        assertEquals("Beto", second.getCreatedBy().name());
        assertNull(second.getAssignee());
    }

    @Test
    void getHouseDashboard_ThrowsWhenHouseNotFound() {
        when(houseRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> dashboardService.getHouseDashboard("u1", "missing"));
        verify(houseAccessValidator).requireHouseMember("u1", "missing");
    }

    @Test
    void expenseSplitDto_OmitsNullDueDateAndTitleInJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        ExpenseSplitDto withoutDashboardFields = new ExpenseSplitDto();
        withoutDashboardFields.setId("s1");
        withoutDashboardFields.setUserId("u1");
        withoutDashboardFields.setAmountDue(new BigDecimal("25.00"));
        withoutDashboardFields.setIsPaid(false);

        String plainJson = mapper.writeValueAsString(withoutDashboardFields);
        assertFalse(plainJson.contains("dueDate"));
        assertFalse(plainJson.contains("expenseTitle"));

        withoutDashboardFields.setDueDate(LocalDate.of(2026, 12, 1));
        withoutDashboardFields.setExpenseTitle("Aluguel");
        String enrichedJson = mapper.writeValueAsString(withoutDashboardFields);
        assertTrue(enrichedJson.contains("\"dueDate\""));
        assertTrue(enrichedJson.contains("\"expenseTitle\":\"Aluguel\""));
    }

    @Test
    void expenseSplitDto_SerializesExpenseIdOnlyWhenPresent() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        ExpenseSplitDto dto = new ExpenseSplitDto();
        dto.setId("s1");
        dto.setUserId("u1");
        dto.setAmountDue(new BigDecimal("25.00"));
        dto.setIsPaid(false);

        String withoutExpenseIdJson = mapper.writeValueAsString(dto);
        assertFalse(withoutExpenseIdJson.contains("expenseId"));

        dto.setExpenseId("e1");
        String withExpenseIdJson = mapper.writeValueAsString(dto);
        assertTrue(withExpenseIdJson.contains("\"expenseId\":\"e1\""));
    }
}
