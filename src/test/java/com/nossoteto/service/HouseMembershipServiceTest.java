package com.nossoteto.service;

import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Chore;
import com.nossoteto.model.Grocery;
import com.nossoteto.model.House;
import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.ChoreType;
import com.nossoteto.model.enums.MatchStatus;
import com.nossoteto.repository.ChoreRepository;
import com.nossoteto.repository.GroceryRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.MatchRequestRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HouseMembershipServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private HouseRepository houseRepository;

    @Mock
    private ChoreRepository choreRepository;

    @Mock
    private GroceryRepository groceryRepository;

    @Mock
    private MatchRequestRepository matchRequestRepository;

    private HouseMembershipService membershipService;

    @BeforeEach
    void setUp() {
        membershipService = new HouseMembershipService(
                userRepository,
                houseRepository,
                choreRepository,
                groceryRepository,
                matchRequestRepository,
                new HouseAccessValidator(userRepository)
        );
    }

    private User resident(String id, boolean admin) {
        User user = new User();
        user.setId(id);
        user.setHouseId("house-1");
        user.setIsHouseAdmin(admin);
        return user;
    }

    @Test
    void adminLeavingShouldHandOverAdminChoresAndGroceriesToNextResident() {
        User admin = resident("user-1", true);
        User u2 = resident("user-2", false);
        User u3 = resident("user-3", false);

        Chore adminChore = new Chore();
        adminChore.setId("c-1");
        adminChore.setHouseId("house-1");
        adminChore.setChoreType(ChoreType.ROTATIONAL);
        adminChore.setStatus(ChoreStatus.PENDING);
        adminChore.setCurrentAssigneeId("user-1");
        adminChore.setLastResetDate(LocalDate.now());

        Chore otherChore = new Chore();
        otherChore.setId("c-2");
        otherChore.setHouseId("house-1");
        otherChore.setCurrentAssigneeId("user-3");

        Grocery adminItem = new Grocery();
        adminItem.setId("g-1");
        adminItem.setHouseId("house-1");
        adminItem.setAssigneeId("user-1");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(u2, u3));
        when(choreRepository.findByHouseId("house-1")).thenReturn(List.of(adminChore, otherChore));
        when(groceryRepository.findByHouseIdAndIsPurchasedFalse("house-1")).thenReturn(List.of(adminItem));

        membershipService.leaveHouse("user-1", "house-1");

        assertNull(admin.getHouseId());
        assertFalse(admin.getIsHouseAdmin());
        assertTrue(u2.getIsHouseAdmin(), "next resident should become admin");
        assertFalse(u3.getIsHouseAdmin());
        assertEquals("user-2", adminChore.getCurrentAssigneeId());
        assertEquals("user-3", otherChore.getCurrentAssigneeId());
        assertNull(adminItem.getAssigneeId());
        verify(userRepository).save(admin);
        verify(userRepository).save(u2);
    }

    @Test
    void lastResidentLeavingShouldCloseHouseAndCancelPendingRequests() {
        User lastResident = resident("user-1", true);

        House house = new House();
        house.setId("house-1");
        house.setIsAcceptingMatches(true);

        MatchRequest pending = new MatchRequest();
        pending.setId("req-1");
        pending.setHouseId("house-1");
        pending.setStatus(MatchStatus.PENDING);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(lastResident));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of());
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc("house-1", MatchStatus.PENDING))
                .thenReturn(List.of(pending));

        membershipService.leaveHouse("user-1", "house-1");

        assertNull(lastResident.getHouseId());
        assertFalse(house.getIsAcceptingMatches());
        assertEquals(MatchStatus.CANCELLED, pending.getStatus());
        verify(houseRepository).save(house);
        verifyNoInteractions(choreRepository, groceryRepository);
    }

    @Test
    void leaveHouseShouldBeForbiddenForNonResidents() {
        User outsider = new User();
        outsider.setId("outsider");
        outsider.setHouseId("other-house");
        when(userRepository.findById("outsider")).thenReturn(Optional.of(outsider));

        assertThrows(ForbiddenOperationException.class,
                () -> membershipService.leaveHouse("outsider", "house-1"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminShouldRemoveResidentAndKeepOwnAdminRole() {
        User admin = resident("user-1", true);
        User u2 = resident("user-2", false);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(u2));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin));

        membershipService.removeMember("user-1", "house-1", "user-2");

        assertNull(u2.getHouseId());
        assertTrue(admin.getIsHouseAdmin());
        verify(userRepository).save(u2);
        verify(userRepository, never()).save(admin);
    }

    @Test
    void nonAdminShouldNotRemoveResidents() {
        when(userRepository.findById("user-2")).thenReturn(Optional.of(resident("user-2", false)));

        assertThrows(ForbiddenOperationException.class,
                () -> membershipService.removeMember("user-2", "house-1", "user-3"));
    }

    @Test
    void adminShouldNotRemoveThemselves() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(resident("user-1", true)));

        assertThrows(BusinessRuleException.class,
                () -> membershipService.removeMember("user-1", "house-1", "user-1"));
    }

    @Test
    void removingSomeoneFromAnotherHouseShouldReturnNotFound() {
        User stranger = new User();
        stranger.setId("user-9");
        stranger.setHouseId("other-house");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(resident("user-1", true)));
        when(userRepository.findById("user-9")).thenReturn(Optional.of(stranger));

        assertThrows(ResourceNotFoundException.class,
                () -> membershipService.removeMember("user-1", "house-1", "user-9"));
        assertEquals("other-house", stranger.getHouseId());
    }
}
