package com.nossoteto.service;

import com.nossoteto.dto.ChoreResponse;
import com.nossoteto.dto.ChoreSwapDto;
import com.nossoteto.dto.CreateChoreRequest;
import com.nossoteto.dto.CreateChoreSwapRequest;
import com.nossoteto.dto.UpdateRotationRequest;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Chore;
import com.nossoteto.model.ChoreSwap;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.ChoreFrequency;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.ChoreSwapStatus;
import com.nossoteto.model.enums.ChoreType;
import com.nossoteto.model.enums.PointReason;
import com.nossoteto.model.enums.PointSourceType;
import com.nossoteto.repository.ChoreRepository;
import com.nossoteto.repository.ChoreSwapRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChoreServiceTest {

    /** Friday 2026-11-27 12:00 in São Paulo: ISO week 2026-W48. */
    private static final Instant FRIDAY = Instant.parse("2026-11-27T15:00:00Z");
    private static final LocalDate FRIDAY_DATE = LocalDate.of(2026, 11, 27);
    private static final String WEEK = "2026-W48";

    @Mock private ChoreRepository choreRepository;
    @Mock private ChoreSwapRepository choreSwapRepository;
    @Mock private UserRepository userRepository;
    @Mock private HouseRepository houseRepository;
    @Mock private HouseAccessValidator houseAccessValidator;
    @Mock private PointService pointService;

    private ChoreService choreService;

    private final User u1 = member("user-1", 1);
    private final User u2 = member("user-2", 2);
    private final User u3 = member("user-3", 3);

    @BeforeEach
    void setUp() {
        choreService = new ChoreService(choreRepository, choreSwapRepository, userRepository, houseRepository,
                houseAccessValidator, pointService, Clock.fixed(FRIDAY, ZoneOffset.UTC));
        lenient().when(choreRepository.save(any(Chore.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(choreRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(choreSwapRepository.save(any(ChoreSwap.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static User member(String id, int joinedDay) {
        User user = new User();
        user.setId(id);
        user.setName("Nome " + id);
        user.setHouseId("house-1");
        user.setCreatedAt(LocalDateTime.of(2026, 1, joinedDay, 10, 0));
        return user;
    }

    private void residents(User... users) {
        lenient().when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(users));
    }

    private Chore chore(String id, ChoreType type, ChoreStatus status, String assigneeId) {
        Chore chore = new Chore();
        chore.setId(id);
        chore.setHouseId("house-1");
        chore.setDescription("Tarefa " + id);
        chore.setChoreType(type);
        chore.setCurrentAssigneeId(assigneeId);
        chore.setStatus(status);
        chore.setLastResetDate(FRIDAY_DATE.minusDays(7));
        if (status == ChoreStatus.COMPLETED) {
            chore.setCompletionsThisCycle(1);
        }
        return chore;
    }

    private Chore stored(Chore chore) {
        lenient().when(choreRepository.findById(chore.getId())).thenReturn(Optional.of(chore));
        return chore;
    }

    private void houseChores(Chore... chores) {
        when(choreRepository.findByHouseId("house-1")).thenReturn(new ArrayList<>(List.of(chores)));
    }

    // ------------------------------------------------------------------ create

    @Test
    void createChore_DefaultsAssigneeToCreatorFrequencyOnceAndJoinOrderRotation() {
        residents(u2, u1);
        when(houseAccessValidator.requireHouseMember("user-1", "house-1")).thenReturn(u1);

        CreateChoreRequest request = new CreateChoreRequest();
        request.setDescription("Lavar louça");
        request.setChoreType("ROTATIVA");

        ChoreResponse response = choreService.createChore("user-1", "house-1", request);

        assertNotNull(response.getId());
        assertEquals("house-1", response.getHouseId());
        assertEquals("Lavar louça", response.getDescription());
        assertEquals(ChoreType.ROTATIONAL, response.getChoreType());
        assertEquals("user-1", response.getCurrentAssigneeId());
        assertEquals("user-1", response.getEffectiveAssigneeId());
        assertEquals("user-2", response.getNextAssigneeId());
        assertEquals(ChoreStatus.PENDING, response.getStatus());
        assertEquals(FRIDAY_DATE, response.getLastResetDate());
        assertEquals(ChoreFrequency.ONCE, response.getFrequency());
        assertEquals(1, response.getRequiredCompletions());
        assertEquals(0, response.getCompletionsThisCycle());
        assertFalse(response.isLinkedToGroceries());
        assertEquals(List.of("user-1", "user-2"), response.getRotationOrder());
        assertEquals(1, response.getCycleNumber());
    }

    @Test
    void createChore_AcceptsFrequencyRotationOrderAndGroceriesShortcut() {
        residents(u1, u2, u3);
        when(houseAccessValidator.requireHouseMember("user-1", "house-1")).thenReturn(u1);

        CreateChoreRequest request = new CreateChoreRequest();
        request.setDescription("Comprar gás");
        request.setChoreType("ROTATIONAL");
        request.setFrequency("thrice");
        request.setRotationOrder(List.of("user-3", "user-1", "user-2"));
        request.setLinkedToGroceries(true);

        ChoreResponse response = choreService.createChore("user-1", "house-1", request);

        assertEquals(ChoreFrequency.THRICE, response.getFrequency());
        assertEquals(3, response.getRequiredCompletions());
        assertTrue(response.isLinkedToGroceries());
        assertEquals(List.of("user-3", "user-1", "user-2"), response.getRotationOrder());
        assertEquals("user-3", response.getCurrentAssigneeId(), "first of the given order starts");
        assertEquals("user-1", response.getNextAssigneeId());
    }

    @Test
    void createChore_RejectsRotationOrderThatIsNotExactlyTheResidents() {
        residents(u1, u2);
        when(houseAccessValidator.requireHouseMember("user-1", "house-1")).thenReturn(u1);

        for (List<String> order : List.of(
                List.of("user-1"),
                List.of("user-1", "user-1"),
                List.of("user-1", "user-2", "outsider"),
                List.of("user-1", "outsider"))) {
            CreateChoreRequest request = new CreateChoreRequest();
            request.setDescription("Varrer");
            request.setChoreType("ROTATIONAL");
            request.setRotationOrder(order);
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> choreService.createChore("user-1", "house-1", request));
            assertEquals("rotation order must match current house members", ex.getMessage());
        }
        verify(choreRepository, never()).save(any());
    }

    @Test
    void createChore_RejectsUnknownFrequency() {
        residents(u1);
        when(houseAccessValidator.requireHouseMember("user-1", "house-1")).thenReturn(u1);
        CreateChoreRequest request = new CreateChoreRequest();
        request.setDescription("Varrer");
        request.setChoreType("FIXED");
        request.setFrequency("HOURLY");

        assertThrows(BusinessRuleException.class, () -> choreService.createChore("user-1", "house-1", request));
    }

    @Test
    void createChore_ThrowsWhenAssigneeNotInHouse() {
        residents(u1);
        User outsider = new User();
        outsider.setId("user-9");
        outsider.setHouseId("other-house");

        when(houseAccessValidator.requireHouseMember("user-1", "house-1")).thenReturn(u1);
        when(userRepository.findById("user-9")).thenReturn(Optional.of(outsider));

        CreateChoreRequest request = new CreateChoreRequest();
        request.setDescription("Limpar banheiro");
        request.setChoreType("FIXA");
        request.setAssigneeId("user-9");

        assertThrows(BusinessRuleException.class, () -> choreService.createChore("user-1", "house-1", request));
    }

    @Test
    void frequenciesRequireOneTwoThreeSevenAndOneCompletions() {
        assertEquals(1, ChoreFrequency.ONCE.requiredCompletions());
        assertEquals(2, ChoreFrequency.TWICE.requiredCompletions());
        assertEquals(3, ChoreFrequency.THRICE.requiredCompletions());
        assertEquals(7, ChoreFrequency.DAILY.requiredCompletions());
        assertEquals(1, ChoreFrequency.BIWEEKLY.requiredCompletions());
    }

    // ------------------------------------------------------------------ complete / uncomplete / reopen

    @Test
    void completeChore_OnceMarksCompletedAndSecondCompletionIsRejected() {
        residents(u1, u2);
        Chore chore = stored(chore("c-1", ChoreType.FIXED, ChoreStatus.PENDING, "user-1"));

        ChoreResponse response = choreService.completeChore("user-1", "house-1", "c-1");

        assertEquals(ChoreStatus.COMPLETED, response.getStatus());
        assertEquals(1, response.getCompletionsThisCycle());
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> choreService.completeChore("user-1", "house-1", "c-1"));
        assertEquals("chore already completed for this cycle", ex.getMessage());
        assertEquals(1, chore.getCompletionsThisCycle());
    }

    @Test
    void completeChore_ThriceCountsEachCompletionUntilThree() {
        residents(u1, u2);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1");
        chore.setFrequency(ChoreFrequency.THRICE);
        stored(chore);

        ChoreResponse first = choreService.completeChore("user-1", "house-1", "c-1");
        assertEquals(ChoreStatus.PENDING, first.getStatus());
        assertEquals(1, first.getCompletionsThisCycle());
        assertEquals(3, first.getRequiredCompletions());

        ChoreResponse second = choreService.completeChore("user-1", "house-1", "c-1");
        assertEquals(ChoreStatus.PENDING, second.getStatus());
        assertEquals(2, second.getCompletionsThisCycle());

        ChoreResponse third = choreService.completeChore("user-1", "house-1", "c-1");
        assertEquals(ChoreStatus.COMPLETED, third.getStatus());
        assertEquals(3, third.getCompletionsThisCycle());

        assertThrows(BusinessRuleException.class, () -> choreService.completeChore("user-1", "house-1", "c-1"));
    }

    @Test
    void completeChore_TwiceAndDailyNeedTwoAndSevenCompletions() {
        residents(u1);
        Chore twice = chore("c-2", ChoreType.FIXED, ChoreStatus.PENDING, "user-1");
        twice.setFrequency(ChoreFrequency.TWICE);
        stored(twice);
        Chore daily = chore("c-7", ChoreType.FIXED, ChoreStatus.PENDING, "user-1");
        daily.setFrequency(ChoreFrequency.DAILY);
        stored(daily);

        choreService.completeChore("user-1", "house-1", "c-2");
        assertEquals(ChoreStatus.PENDING, twice.getStatus());
        choreService.completeChore("user-1", "house-1", "c-2");
        assertEquals(ChoreStatus.COMPLETED, twice.getStatus());

        for (int i = 1; i <= 6; i++) {
            choreService.completeChore("user-1", "house-1", "c-7");
            assertEquals(ChoreStatus.PENDING, daily.getStatus(), "completion " + i + " of 7");
        }
        choreService.completeChore("user-1", "house-1", "c-7");
        assertEquals(ChoreStatus.COMPLETED, daily.getStatus());
    }

    @Test
    void completeChore_OnlyTheEffectiveAssigneeCanComplete() {
        residents(u1, u2);
        stored(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1"));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> choreService.completeChore("user-2", "house-1", "c-1"));
        assertEquals("only the assignee can complete", ex.getMessage());
    }

    @Test
    void completeChore_ThrowsWhenChoreBelongsToDifferentHouse() {
        Chore chore = new Chore();
        chore.setId("chore-1");
        chore.setHouseId("other-house");
        when(choreRepository.findById("chore-1")).thenReturn(Optional.of(chore));

        assertThrows(ResourceNotFoundException.class,
                () -> choreService.completeChore("user-1", "house-1", "chore-1"));
    }

    @Test
    void uncompleteChore_UndoesLastCompletionBackToPending() {
        residents(u1, u2);
        Chore chore = stored(chore("c-1", ChoreType.FIXED, ChoreStatus.COMPLETED, "user-1"));

        ChoreResponse response = choreService.uncompleteChore("user-1", "house-1", "c-1");

        assertEquals(ChoreStatus.PENDING, response.getStatus());
        assertEquals(0, chore.getCompletionsThisCycle());
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> choreService.uncompleteChore("user-1", "house-1", "c-1"));
        assertEquals("chore has no completions to undo", ex.getMessage());
    }

    @Test
    void uncompleteChore_ReturnsToDelayedWhenTheChoreWasDelayedThisCycle() {
        residents(u1, u2);
        Chore chore = chore("c-1", ChoreType.FIXED, ChoreStatus.COMPLETED, "user-1");
        chore.setDelayedThisCycle(true);
        stored(chore);

        assertEquals(ChoreStatus.DELAYED, choreService.uncompleteChore("user-1", "house-1", "c-1").getStatus());
    }

    @Test
    void uncompleteChore_OnlyTheAssignee() {
        residents(u1, u2);
        stored(chore("c-1", ChoreType.FIXED, ChoreStatus.COMPLETED, "user-1"));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> choreService.uncompleteChore("user-2", "house-1", "c-1"));
        assertEquals("only the assignee can complete", ex.getMessage());
    }

    @Test
    void reopenChore_ByAnotherResidentGoesDelayedAndRecordsWhoReopened() {
        residents(u1, u2);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1");
        chore.setFrequency(ChoreFrequency.THRICE);
        chore.setCompletionsThisCycle(3);
        chore.setStatus(ChoreStatus.COMPLETED);
        stored(chore);

        ChoreResponse response = choreService.reopenChore("user-2", "house-1", "c-1");

        assertEquals(ChoreStatus.DELAYED, response.getStatus());
        assertTrue(response.isDelayedThisCycle());
        assertEquals(2, response.getCompletionsThisCycle());
        assertEquals("user-2", response.getReopenedById());
        assertEquals("Nome user-2", response.getReopenedByName());
        assertEquals(LocalDateTime.ofInstant(FRIDAY, ZoneOffset.UTC), response.getReopenedAt());
        assertEquals("user-1", response.getEffectiveAssigneeId(), "it stays with the assignee");
    }

    @Test
    void reopenChore_AssigneeCannotReopenOwnAndOnlyCompletedCanBeReopened() {
        residents(u1, u2);
        stored(chore("c-1", ChoreType.FIXED, ChoreStatus.COMPLETED, "user-1"));
        stored(chore("c-2", ChoreType.FIXED, ChoreStatus.PENDING, "user-1"));

        ForbiddenOperationException own = assertThrows(ForbiddenOperationException.class,
                () -> choreService.reopenChore("user-1", "house-1", "c-1"));
        assertEquals("assignee cannot reopen own chore", own.getMessage());
        BusinessRuleException notDone = assertThrows(BusinessRuleException.class,
                () -> choreService.reopenChore("user-2", "house-1", "c-2"));
        assertEquals("chore is not completed", notDone.getMessage());
    }

    // ------------------------------------------------------------------ rotation order

    @Test
    void updateRotation_AdminSetsExactPermutationOfResidents() {
        residents(u1, u2, u3);
        stored(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1"));
        UpdateRotationRequest request = new UpdateRotationRequest();
        request.setOrder(List.of("user-1", "user-3", "user-2"));

        ChoreResponse response = choreService.updateRotation("user-1", "house-1", "c-1", request);

        verify(houseAccessValidator).requireHouseAdmin("user-1", "house-1");
        assertEquals(List.of("user-1", "user-3", "user-2"), response.getRotationOrder());
        assertEquals("user-3", response.getNextAssigneeId());
        assertEquals("user-1", response.getCurrentAssigneeId());
    }

    @Test
    void updateRotation_RejectsDuplicatesMissingAndExtraIds() {
        residents(u1, u2);
        stored(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1"));

        for (List<String> order : List.of(
                List.of("user-1"), List.of("user-2", "user-2"), List.of("user-1", "user-2", "user-3"))) {
            UpdateRotationRequest request = new UpdateRotationRequest();
            request.setOrder(order);
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> choreService.updateRotation("user-1", "house-1", "c-1", request));
            assertEquals("rotation order must match current house members", ex.getMessage());
        }
        assertThrows(BusinessRuleException.class,
                () -> choreService.updateRotation("user-1", "house-1", "c-1", new UpdateRotationRequest()));
    }

    @Test
    void updateRotation_IsRestrictedToHouseAdmin() {
        doThrow(new ForbiddenOperationException("User is not an administrator of this house"))
                .when(houseAccessValidator).requireHouseAdmin("user-2", "house-1");

        assertThrows(ForbiddenOperationException.class,
                () -> choreService.updateRotation("user-2", "house-1", "c-1", new UpdateRotationRequest()));
        verifyNoInteractions(choreRepository);
    }

    // ------------------------------------------------------------------ closing the cycle

    @Test
    void close_UnfinishedRotationalChoreBecomesDelayedAndStaysWithSameAssignee() {
        residents(u1, u2);
        houseChores(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1"));

        ChoreResponse r1 = choreService.rotateHouseChores("user-1", "house-1").get(0);

        assertEquals(ChoreStatus.DELAYED, r1.getStatus());
        assertTrue(r1.isDelayedThisCycle());
        assertEquals("user-1", r1.getCurrentAssigneeId());
        assertEquals(1, r1.getCycleNumber());
        assertEquals(FRIDAY_DATE, r1.getLastResetDate());
        verifyNoInteractions(pointService);
    }

    @Test
    void close_AlreadyDelayedChoreStaysDelayedUntilCompleted() {
        residents(u1, u2);
        houseChores(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.DELAYED, "user-1"));

        ChoreResponse r1 = choreService.rotateHouseChores("user-1", "house-1").get(0);

        assertEquals(ChoreStatus.DELAYED, r1.getStatus());
        assertEquals("user-1", r1.getCurrentAssigneeId());
    }

    @Test
    void close_CompletedOnTimeCreditsTwentyPointsResetsCycleAndRotates() {
        residents(u1, u2);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.COMPLETED, "user-1");
        houseChores(chore);

        ChoreResponse r1 = choreService.rotateHouseChores("user-1", "house-1").get(0);

        verify(pointService).creditIfAbsent("user-1", "house-1", PointReason.CHORE_ON_TIME, 20,
                PointSourceType.CHORE, "c-1", "cycle-1", "Tarefa c-1");
        assertEquals(ChoreStatus.PENDING, r1.getStatus());
        assertEquals("user-2", r1.getCurrentAssigneeId());
        assertEquals(2, r1.getCycleNumber());
        assertEquals(0, r1.getCompletionsThisCycle());
        assertEquals(WEEK, chore.getLastClosedWeek());
    }

    @Test
    void close_CompletedAfterBeingDelayedRotatesWithoutPoints() {
        residents(u1, u2);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.COMPLETED, "user-1");
        chore.setDelayedThisCycle(true);
        chore.setReopenedById("user-2");
        houseChores(chore);

        ChoreResponse r1 = choreService.rotateHouseChores("user-1", "house-1").get(0);

        verifyNoInteractions(pointService);
        assertEquals("user-2", r1.getCurrentAssigneeId());
        assertFalse(r1.isDelayedThisCycle());
        assertNull(r1.getReopenedById());
    }

    @Test
    void close_CompletedFixedChoreResetsToPendingWithSameAssignee() {
        residents(u1, u2);
        houseChores(chore("c-2", ChoreType.FIXED, ChoreStatus.COMPLETED, "user-1"));

        ChoreResponse r2 = choreService.rotateHouseChores("user-1", "house-1").get(0);

        assertEquals(ChoreStatus.PENDING, r2.getStatus());
        assertEquals("user-1", r2.getCurrentAssigneeId());
        assertEquals("user-1", r2.getNextAssigneeId());
        assertEquals(FRIDAY_DATE, r2.getLastResetDate());
    }

    @Test
    void close_FollowsSavedRotationOrder() {
        residents(u1, u2, u3);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.COMPLETED, "user-1");
        chore.assignRotationOrderIds(List.of("user-1", "user-3", "user-2"));
        houseChores(chore);

        assertEquals("user-3", choreService.rotateHouseChores("user-1", "house-1").get(0).getCurrentAssigneeId());
    }

    @Test
    void close_BiweeklyOnlyClosesOnTheSecondWeek() {
        residents(u1, u2);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.COMPLETED, "user-1");
        chore.setFrequency(ChoreFrequency.BIWEEKLY);
        houseChores(chore);

        ChoreResponse firstWeek = choreService.rotateHouseChores("user-1", "house-1").get(0);
        assertEquals(ChoreStatus.COMPLETED, firstWeek.getStatus());
        assertEquals("user-1", firstWeek.getCurrentAssigneeId());
        assertEquals(1, chore.getWeeksInCycle());
        verifyNoInteractions(pointService);

        ChoreResponse secondWeek = choreService.rotateHouseChores("user-1", "house-1").get(0);
        assertEquals(ChoreStatus.PENDING, secondWeek.getStatus());
        assertEquals("user-2", secondWeek.getCurrentAssigneeId());
        assertEquals(0, chore.getWeeksInCycle());
        verify(pointService).creditIfAbsent(eq("user-1"), eq("house-1"), eq(PointReason.CHORE_ON_TIME), eq(20),
                eq(PointSourceType.CHORE), eq("c-1"), eq("cycle-1"), anyString());
    }

    @Test
    void close_BiweeklyFirstWeekDoesNotMarkUnfinishedAsDelayed() {
        residents(u1, u2);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1");
        chore.setFrequency(ChoreFrequency.BIWEEKLY);
        houseChores(chore);

        ChoreResponse firstWeek = choreService.rotateHouseChores("user-1", "house-1").get(0);

        assertEquals(ChoreStatus.PENDING, firstWeek.getStatus());
        assertFalse(firstWeek.isDelayedThisCycle());
    }

    @Test
    void scheduledJob_SkipsChoresAlreadyClosedThisIsoWeekButManualCloseAlwaysRuns() {
        residents(u1, u2);
        House house = new House();
        house.setId("house-1");
        when(houseRepository.findAll()).thenReturn(List.of(house));
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.COMPLETED, "user-1");
        chore.setLastClosedWeek(WEEK);
        houseChores(chore);

        choreService.rotateAllHousesScheduled();
        assertEquals("user-1", chore.getCurrentAssigneeId(), "same ISO week: the job must not close again");
        assertEquals(ChoreStatus.COMPLETED, chore.getStatus());

        choreService.rotateHouseChores("user-1", "house-1");
        assertEquals("user-2", chore.getCurrentAssigneeId(), "the manual close is never skipped");
    }

    @Test
    void scheduledJob_ClosesChoresLastClosedInAnEarlierWeek() {
        residents(u1, u2);
        House house = new House();
        house.setId("house-1");
        when(houseRepository.findAll()).thenReturn(List.of(house));
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.COMPLETED, "user-1");
        chore.setLastClosedWeek("2026-W47");
        houseChores(chore);

        choreService.rotateAllHousesScheduled();

        assertEquals("user-2", chore.getCurrentAssigneeId());
        assertEquals(WEEK, chore.getLastClosedWeek());
    }

    @Test
    void close_SwapCreditsTheTemporaryAssigneeRotatesFromOwnerAndCancelsPendingSwaps() {
        residents(u1, u2, u3);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.COMPLETED, "user-1");
        chore.setTemporaryAssigneeId("user-3");
        houseChores(chore);
        ChoreSwap pending = new ChoreSwap();
        pending.setId("s-1");
        pending.setStatus(ChoreSwapStatus.PENDING);
        when(choreSwapRepository.findByStatusInvolvingChore(ChoreSwapStatus.PENDING, "c-1"))
                .thenReturn(List.of(pending));

        ChoreResponse r1 = choreService.rotateHouseChores("user-1", "house-1").get(0);

        verify(pointService).creditIfAbsent(eq("user-3"), eq("house-1"), eq(PointReason.CHORE_ON_TIME), eq(20),
                eq(PointSourceType.CHORE), eq("c-1"), eq("cycle-1"), anyString());
        assertEquals("user-2", r1.getCurrentAssigneeId(), "rotation moves from the slot owner, not the swap");
        assertNull(chore.getTemporaryAssigneeId());
        assertEquals(ChoreSwapStatus.CANCELLED, pending.getStatus());
    }

    @Test
    void close_IsRestrictedToHouseAdmin() {
        doThrow(new ForbiddenOperationException("User is not an administrator of this house"))
                .when(houseAccessValidator).requireHouseAdmin("user-2", "house-1");

        assertThrows(ForbiddenOperationException.class,
                () -> choreService.rotateHouseChores("user-2", "house-1"));
        verifyNoInteractions(choreRepository);
    }

    @Test
    void isoWeekKey_UsesWeekBasedYear() {
        assertEquals("2026-W48", ChoreService.isoWeekKey(LocalDate.of(2026, 11, 27)));
        assertEquals("2026-W48", ChoreService.isoWeekKey(LocalDate.of(2026, 11, 29)));
        assertEquals("2026-W49", ChoreService.isoWeekKey(LocalDate.of(2026, 11, 30)));
        assertEquals("2026-W53", ChoreService.isoWeekKey(LocalDate.of(2027, 1, 1)));
        assertEquals("2026-W01", ChoreService.isoWeekKey(LocalDate.of(2025, 12, 29)));
    }

    // ------------------------------------------------------------------ resilience to former residents

    @Test
    void formerResidentInRotationOrderIsSkippedAndNewcomerIsAppended() {
        residents(u1, u2, u3);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.COMPLETED, "user-1");
        chore.assignRotationOrderIds(List.of("user-1", "gone", "user-2"));
        houseChores(chore);

        ChoreResponse response = choreService.rotateHouseChores("user-1", "house-1").get(0);

        assertEquals("user-2", response.getCurrentAssigneeId());
        assertEquals(List.of("user-1", "user-2", "user-3"), response.getRotationOrder());
        assertEquals("user-3", response.getNextAssigneeId());
    }

    @Test
    void temporaryAssigneeWhoLeftIsIgnoredAndOwnerIsEffective() {
        residents(u1, u2);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1");
        chore.setTemporaryAssigneeId("gone");
        stored(chore);

        ChoreResponse response = choreService.completeChore("user-1", "house-1", "c-1");

        assertEquals("user-1", response.getEffectiveAssigneeId());
        assertNull(response.getTemporaryAssigneeId());
        assertEquals(ChoreStatus.COMPLETED, response.getStatus());
    }

    @Test
    void ownerWhoLeftFallsBackToFirstOfEffectiveOrder() {
        residents(u2, u3);
        Chore chore = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "gone");
        stored(chore);

        assertThrows(ForbiddenOperationException.class, () -> choreService.completeChore("user-3", "house-1", "c-1"));
        assertEquals(ChoreStatus.COMPLETED, choreService.completeChore("user-2", "house-1", "c-1").getStatus());
    }

    // ------------------------------------------------------------------ swaps

    private CreateChoreSwapRequest swapTo(String targetChoreId) {
        CreateChoreSwapRequest request = new CreateChoreSwapRequest();
        request.setTargetChoreId(targetChoreId);
        return request;
    }

    @Test
    void createSwap_OwnerProposesToTheTargetChoreAssignee() {
        residents(u1, u2);
        stored(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1"));
        stored(chore("c-2", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-2"));

        ChoreSwapDto dto = choreService.createSwap("user-1", "house-1", "c-1", swapTo("c-2"));

        assertEquals(ChoreSwapStatus.PENDING, dto.status());
        assertEquals("user-1", dto.requesterId());
        assertEquals("user-2", dto.targetUserId());
        assertEquals("Nome user-2", dto.targetUserName());
        assertEquals(1, dto.cycleNumber());
        assertEquals("Tarefa c-2", dto.targetChoreDescription());
    }

    @Test
    void createSwap_RejectsInvalidCombinations() {
        residents(u1, u2);
        stored(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1"));
        stored(chore("c-mine", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1"));
        Chore elsewhere = chore("c-x", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-2");
        elsewhere.setHouseId("other-house");
        stored(elsewhere);
        stored(chore("c-2", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-2"));

        for (String target : new String[] {"c-1", "c-mine", "c-x", "missing", null}) {
            lenient().when(choreRepository.findById("missing")).thenReturn(Optional.empty());
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> choreService.createSwap("user-1", "house-1", "c-1", swapTo(target)));
            assertEquals("invalid chore swap", ex.getMessage());
        }
        // Only the assignee of the requesting chore can propose it.
        assertThrows(BusinessRuleException.class,
                () -> choreService.createSwap("user-2", "house-1", "c-1", swapTo("c-2")));
        // A chore already in a pending swap cannot get a second one.
        lenient().when(choreSwapRepository.findByStatusInvolvingChore(ChoreSwapStatus.PENDING, "c-2"))
                .thenReturn(List.of(new ChoreSwap()));
        assertThrows(BusinessRuleException.class,
                () -> choreService.createSwap("user-1", "house-1", "c-1", swapTo("c-2")));
        verify(choreSwapRepository, never()).save(any());
    }

    private ChoreSwap pendingSwap() {
        ChoreSwap swap = new ChoreSwap();
        swap.setId("s-1");
        swap.setHouseId("house-1");
        swap.setRequesterChoreId("c-1");
        swap.setTargetChoreId("c-2");
        swap.setRequesterId("user-1");
        swap.setTargetUserId("user-2");
        swap.setStatus(ChoreSwapStatus.PENDING);
        swap.setCycleNumber(1);
        swap.setCreatedAt(LocalDateTime.ofInstant(FRIDAY, ZoneOffset.UTC));
        when(choreSwapRepository.findById("s-1")).thenReturn(Optional.of(swap));
        return swap;
    }

    @Test
    void acceptSwap_SetsTemporaryAssigneesWithoutTouchingTheRotation() {
        residents(u1, u2);
        Chore mine = stored(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1"));
        Chore theirs = stored(chore("c-2", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-2"));
        pendingSwap();

        ChoreSwapDto dto = choreService.acceptSwap("user-2", "house-1", "s-1");

        assertEquals(ChoreSwapStatus.ACCEPTED, dto.status());
        assertEquals("user-2", mine.getTemporaryAssigneeId());
        assertEquals("user-1", theirs.getTemporaryAssigneeId());
        assertEquals("user-1", mine.getCurrentAssigneeId());
        assertEquals("user-2", theirs.getCurrentAssigneeId());
        assertEquals("user-2", ChoreService.effectiveAssignee(mine, new ChoreService.HouseMembers(List.of(u1, u2))));
    }

    @Test
    void acceptSwap_RejectedWhenTheRequesterChoreCycleAlreadyClosed() {
        residents(u1, u2);
        Chore mine = chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1");
        mine.setCycleNumber(2);
        stored(mine);
        stored(chore("c-2", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-2"));
        pendingSwap();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> choreService.acceptSwap("user-2", "house-1", "s-1"));
        assertEquals("invalid chore swap", ex.getMessage());
    }

    @Test
    void answeringSwap_OnlyTargetAndOnlyWhilePending() {
        residents(u1, u2);
        ChoreSwap swap = pendingSwap();

        assertThrows(ForbiddenOperationException.class, () -> choreService.acceptSwap("user-1", "house-1", "s-1"));
        assertThrows(ForbiddenOperationException.class, () -> choreService.rejectSwap("user-1", "house-1", "s-1"));

        assertEquals(ChoreSwapStatus.REJECTED, choreService.rejectSwap("user-2", "house-1", "s-1").status());
        assertEquals(ChoreSwapStatus.REJECTED, swap.getStatus());
        assertThrows(BusinessRuleException.class, () -> choreService.acceptSwap("user-2", "house-1", "s-1"));
        assertThrows(BusinessRuleException.class, () -> choreService.rejectSwap("user-2", "house-1", "s-1"));
    }

    @Test
    void listChores_ExposesPendingSwapOnBothChores() {
        residents(u1, u2);
        houseChores(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1"),
                chore("c-2", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-2"));
        ChoreSwap swap = new ChoreSwap();
        swap.setId("s-1");
        swap.setHouseId("house-1");
        swap.setRequesterChoreId("c-1");
        swap.setTargetChoreId("c-2");
        swap.setRequesterId("user-1");
        swap.setTargetUserId("user-2");
        swap.setStatus(ChoreSwapStatus.PENDING);
        swap.setCycleNumber(1);
        when(choreSwapRepository.findByHouseIdAndStatusOrderByCreatedAtAsc("house-1", ChoreSwapStatus.PENDING))
                .thenReturn(List.of(swap));

        List<ChoreResponse> chores = choreService.listChores("user-1", "house-1");

        assertEquals("s-1", chores.get(0).getPendingSwap().id());
        assertEquals("s-1", chores.get(1).getPendingSwaps().get(0).id());
        assertEquals("Tarefa c-2", chores.get(0).getPendingSwap().targetChoreDescription());
    }

    @Test
    void rotateAllHousesScheduled_ClosesEveryHouse() {
        House h1 = new House();
        h1.setId("house-1");
        when(houseRepository.findAll()).thenReturn(List.of(h1));
        when(choreRepository.findByHouseId("house-1")).thenReturn(List.of());

        choreService.rotateAllHousesScheduled();

        verify(choreRepository).findByHouseId("house-1");
        verify(pointService, never()).creditIfAbsent(anyString(), anyString(), any(), anyInt(), any(), anyString(),
                anyString(), anyString());
        ArgumentCaptor<List<Chore>> saved = ArgumentCaptor.forClass(List.class);
        verify(choreRepository).saveAll(saved.capture());
        assertTrue(saved.getValue().isEmpty());
    }
}
