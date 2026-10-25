package com.nossoteto.service;

import com.nossoteto.dto.ChoreResponse;
import com.nossoteto.dto.CreateChoreRequest;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Chore;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.ChoreType;
import com.nossoteto.repository.ChoreRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChoreServiceTest {

    @Mock
    private ChoreRepository choreRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HouseRepository houseRepository;

    @Mock
    private HouseAccessValidator houseAccessValidator;

    private ChoreService choreService;

    @BeforeEach
    void setUp() {
        choreService = new ChoreService(choreRepository, userRepository, houseRepository, houseAccessValidator);
    }

    @Test
    void createChore_DefaultsAssigneeToCreatorAndStatusPending() {
        User creator = new User();
        creator.setId("user-1");
        creator.setHouseId("house-1");

        when(houseAccessValidator.requireHouseMember("user-1", "house-1")).thenReturn(creator);
        when(choreRepository.save(any(Chore.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateChoreRequest request = new CreateChoreRequest();
        request.setDescription("Lavar louça");
        request.setChoreType("ROTATIVA");

        ChoreResponse response = choreService.createChore("user-1", "house-1", request);

        assertNotNull(response.getId());
        assertEquals("house-1", response.getHouseId());
        assertEquals("Lavar louça", response.getDescription());
        assertEquals(ChoreType.ROTATIONAL, response.getChoreType());
        assertEquals("user-1", response.getCurrentAssigneeId());
        assertEquals(ChoreStatus.PENDING, response.getStatus());
        assertEquals(LocalDate.now(), response.getLastResetDate());
    }

    @Test
    void createChore_ThrowsWhenAssigneeNotInHouse() {
        User creator = new User();
        creator.setId("user-1");
        creator.setHouseId("house-1");

        User outsider = new User();
        outsider.setId("user-2");
        outsider.setHouseId("other-house");

        when(houseAccessValidator.requireHouseMember("user-1", "house-1")).thenReturn(creator);
        when(userRepository.findById("user-2")).thenReturn(Optional.of(outsider));

        CreateChoreRequest request = new CreateChoreRequest();
        request.setDescription("Limpar banheiro");
        request.setChoreType("FIXA");
        request.setAssigneeId("user-2");

        assertThrows(BusinessRuleException.class, () ->
                choreService.createChore("user-1", "house-1", request));
    }

    @Test
    void completeChore_MarksStatusCompleted() {
        Chore chore = new Chore();
        chore.setId("chore-1");
        chore.setHouseId("house-1");
        chore.setDescription("Tirar lixo");
        chore.setChoreType(ChoreType.FIXED);
        chore.setCurrentAssigneeId("user-1");
        chore.setStatus(ChoreStatus.PENDING);
        chore.setLastResetDate(LocalDate.now());

        when(choreRepository.findById("chore-1")).thenReturn(Optional.of(chore));
        when(choreRepository.save(any(Chore.class))).thenAnswer(inv -> inv.getArgument(0));

        ChoreResponse response = choreService.completeChore("user-1", "house-1", "chore-1");

        assertEquals(ChoreStatus.COMPLETED, response.getStatus());
    }

    @Test
    void completeChore_ThrowsWhenChoreBelongsToDifferentHouse() {
        Chore chore = new Chore();
        chore.setId("chore-1");
        chore.setHouseId("other-house");

        when(choreRepository.findById("chore-1")).thenReturn(Optional.of(chore));

        assertThrows(ResourceNotFoundException.class, () ->
                choreService.completeChore("user-1", "house-1", "chore-1"));
    }

    private Chore chore(String id, ChoreType type, ChoreStatus status, String assigneeId) {
        Chore chore = new Chore();
        chore.setId(id);
        chore.setHouseId("house-1");
        chore.setDescription("Tarefa " + id);
        chore.setChoreType(type);
        chore.setCurrentAssigneeId(assigneeId);
        chore.setStatus(status);
        chore.setLastResetDate(LocalDate.now().minusDays(7));
        return chore;
    }

    private void stubTwoMembersAndChores(Chore... chores) {
        User u1 = new User();
        u1.setId("user-1");
        u1.setHouseId("house-1");

        User u2 = new User();
        u2.setId("user-2");
        u2.setHouseId("house-1");

        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(u1, u2));
        when(choreRepository.findByHouseId("house-1")).thenReturn(List.of(chores));
        when(choreRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rotateHouseChores_UnfinishedRotationalChoreBecomesDelayedAndStaysWithSameAssignee() {
        stubTwoMembersAndChores(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.PENDING, "user-1"));

        ChoreResponse r1 = choreService.rotateHouseChores("user-1", "house-1").get(0);

        assertEquals(ChoreStatus.DELAYED, r1.getStatus());
        assertEquals("user-1", r1.getCurrentAssigneeId());
        assertEquals(LocalDate.now(), r1.getLastResetDate());
    }

    @Test
    void rotateHouseChores_AlreadyDelayedChoreStaysDelayedUntilCompleted() {
        stubTwoMembersAndChores(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.DELAYED, "user-1"));

        ChoreResponse r1 = choreService.rotateHouseChores("user-1", "house-1").get(0);

        assertEquals(ChoreStatus.DELAYED, r1.getStatus());
        assertEquals("user-1", r1.getCurrentAssigneeId());
    }

    @Test
    void rotateHouseChores_CompletedRotationalChoreResetsToPendingAndRotates() {
        stubTwoMembersAndChores(chore("c-1", ChoreType.ROTATIONAL, ChoreStatus.COMPLETED, "user-1"));

        ChoreResponse r1 = choreService.rotateHouseChores("user-1", "house-1").get(0);

        assertEquals(ChoreStatus.PENDING, r1.getStatus());
        assertEquals("user-2", r1.getCurrentAssigneeId());
    }

    @Test
    void rotateHouseChores_CompletedFixedChoreResetsToPendingWithSameAssignee() {
        stubTwoMembersAndChores(chore("c-2", ChoreType.FIXED, ChoreStatus.COMPLETED, "user-1"));

        ChoreResponse r2 = choreService.rotateHouseChores("user-1", "house-1").get(0);

        assertEquals(ChoreStatus.PENDING, r2.getStatus());
        assertEquals("user-1", r2.getCurrentAssigneeId());
        assertEquals(LocalDate.now(), r2.getLastResetDate());
    }

    @Test
    void rotateHouseChores_IsRestrictedToHouseAdmin() {
        doThrow(new ForbiddenOperationException("User is not an administrator of this house"))
                .when(houseAccessValidator).requireHouseAdmin("user-2", "house-1");

        assertThrows(ForbiddenOperationException.class,
                () -> choreService.rotateHouseChores("user-2", "house-1"));
        verifyNoInteractions(choreRepository);
    }

    @Test
    void processWeeklyCycleForAllHouses_RotatesAllHouses() {
        House h1 = new House();
        h1.setId("house-1");
        when(houseRepository.findAll()).thenReturn(List.of(h1));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of());
        when(choreRepository.findByHouseId("house-1")).thenReturn(List.of());

        choreService.processWeeklyCycleForAllHouses();

        verify(choreRepository).findByHouseId("house-1");
    }
}
