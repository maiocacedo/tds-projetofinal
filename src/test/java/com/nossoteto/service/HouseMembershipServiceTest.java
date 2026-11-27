package com.nossoteto.service;

import com.nossoteto.dto.HouseMemberSummaryDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Chore;
import com.nossoteto.model.Grocery;
import com.nossoteto.model.House;
import com.nossoteto.model.HouseRemovalRequest;
import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.ChoreStatus;
import com.nossoteto.model.enums.ChoreType;
import com.nossoteto.model.enums.MatchStatus;
import com.nossoteto.model.enums.RemovalStatus;
import com.nossoteto.repository.ChoreRepository;
import com.nossoteto.repository.GroceryRepository;
import com.nossoteto.repository.HouseRemovalRequestRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.MatchRequestRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HouseMembershipServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final LocalDateTime FIXED_NOW_UTC = LocalDateTime.of(2026, 11, 27, 12, 0, 0);

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

    @Mock
    private HouseRemovalRequestRepository houseRemovalRequestRepository;

    @Mock
    private ResidenceHistoryService residenceHistoryService;

    private HouseMembershipService membershipService;

    @BeforeEach
    void setUp() {
        membershipService = new HouseMembershipService(
                userRepository,
                houseRepository,
                choreRepository,
                groceryRepository,
                matchRequestRepository,
                houseRemovalRequestRepository,
                new HouseAccessValidator(userRepository),
                residenceHistoryService,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    private User resident(String id, boolean admin) {
        User user = new User();
        user.setId(id);
        user.setName("Name " + id);
        user.setEmail(id + "@test.com");
        user.setHouseId("house-1");
        user.setIsHouseAdmin(admin);
        return user;
    }

    // ---------- updateMemberAdminStatus ----------

    @Test
    void updateMemberAdminStatusShouldPromoteResidentToAdmin() {
        User admin = resident("user-1", true);
        User member = resident("user-2", false);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(member));
        when(userRepository.save(member)).thenReturn(member);

        HouseMemberSummaryDto result = membershipService.updateMemberAdminStatus("user-1", "house-1", "user-2", true);

        assertEquals("user-2", result.getId());
        assertEquals("Name user-2", result.getName());
        assertEquals("user-2@test.com", result.getEmail());
        assertTrue(result.getIsHouseAdmin());
        assertTrue(member.getIsHouseAdmin());
        verify(userRepository).save(member);
    }

    @Test
    void updateMemberAdminStatusShouldDemoteAdminWhenAnotherAdminExists() {
        User admin1 = resident("user-1", true);
        User admin2 = resident("user-2", true);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin1));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin1, admin2));
        when(userRepository.save(admin1)).thenReturn(admin1);

        HouseMemberSummaryDto result = membershipService.updateMemberAdminStatus("user-1", "house-1", "user-1", false);

        assertFalse(result.getIsHouseAdmin());
        assertFalse(admin1.getIsHouseAdmin());
        verify(userRepository).save(admin1);
    }

    @Test
    void updateMemberAdminStatusShouldRejectDemotingLastAdmin() {
        User soleAdmin = resident("user-1", true);
        User member = resident("user-2", false);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(soleAdmin));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(soleAdmin, member));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipService.updateMemberAdminStatus("user-1", "house-1", "user-1", false));

        assertEquals("house must keep at least one admin", ex.getMessage());
        assertTrue(soleAdmin.getIsHouseAdmin());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMemberAdminStatusShouldRejectNullIsAdmin() {
        User admin = resident("user-1", true);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin));

        assertThrows(BusinessRuleException.class,
                () -> membershipService.updateMemberAdminStatus("user-1", "house-1", "user-2", null));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMemberAdminStatusShouldForbidNonAdmin() {
        when(userRepository.findById("user-2")).thenReturn(Optional.of(resident("user-2", false)));

        assertThrows(ForbiddenOperationException.class,
                () -> membershipService.updateMemberAdminStatus("user-2", "house-1", "user-3", true));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMemberAdminStatusShouldReturnNotFoundForStranger() {
        User stranger = new User();
        stranger.setId("user-9");
        stranger.setHouseId("other-house");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(resident("user-1", true)));
        when(userRepository.findById("user-9")).thenReturn(Optional.of(stranger));

        assertThrows(ResourceNotFoundException.class,
                () -> membershipService.updateMemberAdminStatus("user-1", "house-1", "user-9", true));
        verify(userRepository, never()).save(any());
    }

    // ---------- leaveHouse ----------

    @Test
    void lastAdminLeavingWithoutNewAdminShouldFailBeforeAnyWrite() {
        User admin = resident("user-1", true);
        User u2 = resident("user-2", false);
        User u3 = resident("user-3", false);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin, u2, u3));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipService.leaveHouse("user-1", "house-1", null));

        assertEquals("new admin required", ex.getMessage());
        assertEquals("house-1", admin.getHouseId());
        assertTrue(admin.getIsHouseAdmin());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(residenceHistoryService, choreRepository, groceryRepository, houseRemovalRequestRepository);
    }

    @Test
    void lastAdminLeavingWithSelfOrOutsiderNewAdminShouldFailBeforeAnyWrite() {
        User admin = resident("user-1", true);
        User u2 = resident("user-2", false);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin, u2));

        BusinessRuleException selfEx = assertThrows(BusinessRuleException.class,
                () -> membershipService.leaveHouse("user-1", "house-1", "user-1"));
        assertEquals("new admin required", selfEx.getMessage());

        BusinessRuleException outsiderEx = assertThrows(BusinessRuleException.class,
                () -> membershipService.leaveHouse("user-1", "house-1", "outsider"));
        assertEquals("new admin required", outsiderEx.getMessage());

        BusinessRuleException blankEx = assertThrows(BusinessRuleException.class,
                () -> membershipService.leaveHouse("user-1", "house-1", "   "));
        assertEquals("new admin required", blankEx.getMessage());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(residenceHistoryService, choreRepository, groceryRepository);
    }

    @Test
    void lastAdminLeavingWithChosenHeirShouldPromoteHeirCancelOpenRemovalsAndHandOverChoresAndGroceries() {
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

        HouseRemovalRequest proposedByAdmin = new HouseRemovalRequest();
        proposedByAdmin.setId("rem-1");
        proposedByAdmin.setHouseId("house-1");
        proposedByAdmin.setProposedById("user-1");
        proposedByAdmin.setTargetUserId("user-2");
        proposedByAdmin.setStatus(RemovalStatus.OPEN);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin, u2, u3));
        when(choreRepository.findByHouseId("house-1")).thenReturn(List.of(adminChore, otherChore));
        when(groceryRepository.findByHouseIdAndIsPurchasedFalse("house-1")).thenReturn(List.of(adminItem));
        when(houseRemovalRequestRepository.findOpenInvolvingUser("house-1", "user-1"))
                .thenReturn(List.of(proposedByAdmin));

        // Choose u3 (not u2) as the heir to prove explicit choice takes precedence over deterministic fallback
        membershipService.leaveHouse("user-1", "house-1", "user-3");

        assertNull(admin.getHouseId());
        assertFalse(admin.getIsHouseAdmin());
        assertFalse(u2.getIsHouseAdmin(), "u2 should NOT be auto-promoted when u3 was chosen");
        assertTrue(u3.getIsHouseAdmin(), "chosen heir u3 should become admin");
        assertEquals("user-2", adminChore.getCurrentAssigneeId());
        assertEquals("user-3", otherChore.getCurrentAssigneeId());
        assertNull(adminItem.getAssigneeId());
        assertEquals(RemovalStatus.CANCELLED, proposedByAdmin.getStatus());
        assertEquals(FIXED_NOW_UTC, proposedByAdmin.getResolvedAt());
        verify(userRepository).save(u3);
        verify(userRepository).save(admin);
        verify(houseRemovalRequestRepository).saveAll(List.of(proposedByAdmin));
        verify(residenceHistoryService).recordExit("user-1", "house-1");
    }

    @Test
    void adminLeavingWhenAnotherAdminExistsShouldNotRequireNewAdmin() {
        User admin1 = resident("user-1", true);
        User admin2 = resident("user-2", true);
        User member = resident("user-3", false);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin1));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin1, admin2, member));
        when(choreRepository.findByHouseId("house-1")).thenReturn(List.of());
        when(groceryRepository.findByHouseIdAndIsPurchasedFalse("house-1")).thenReturn(List.of());
        when(houseRemovalRequestRepository.findOpenInvolvingUser("house-1", "user-1")).thenReturn(List.of());

        membershipService.leaveHouse("user-1", "house-1", null);

        assertNull(admin1.getHouseId());
        assertTrue(admin2.getIsHouseAdmin());
        assertFalse(member.getIsHouseAdmin());
        verify(residenceHistoryService).recordExit("user-1", "house-1");
    }

    @Test
    void nonAdminTargetLeavingShouldCancelOpenRemovalAgainstThem() {
        User admin = resident("user-1", true);
        User target = resident("user-2", false);

        HouseRemovalRequest removalAgainstTarget = new HouseRemovalRequest();
        removalAgainstTarget.setId("rem-target");
        removalAgainstTarget.setHouseId("house-1");
        removalAgainstTarget.setProposedById("user-1");
        removalAgainstTarget.setTargetUserId("user-2");
        removalAgainstTarget.setStatus(RemovalStatus.OPEN);

        when(userRepository.findById("user-2")).thenReturn(Optional.of(target));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin, target));
        when(choreRepository.findByHouseId("house-1")).thenReturn(List.of());
        when(groceryRepository.findByHouseIdAndIsPurchasedFalse("house-1")).thenReturn(List.of());
        when(houseRemovalRequestRepository.findOpenInvolvingUser("house-1", "user-2"))
                .thenReturn(List.of(removalAgainstTarget));

        membershipService.leaveHouse("user-2", "house-1", null);

        assertNull(target.getHouseId());
        assertEquals(RemovalStatus.CANCELLED, removalAgainstTarget.getStatus());
        assertEquals(FIXED_NOW_UTC, removalAgainstTarget.getResolvedAt());
        verify(houseRemovalRequestRepository).saveAll(List.of(removalAgainstTarget));
    }

    @Test
    void lastResidentLeavingShouldCloseHouseAndCancelPendingMatchesAndOpenRemovals() {
        User lastResident = resident("user-1", true);

        House house = new House();
        house.setId("house-1");
        house.setIsAcceptingMatches(true);

        MatchRequest pending = new MatchRequest();
        pending.setId("req-1");
        pending.setHouseId("house-1");
        pending.setStatus(MatchStatus.PENDING);

        HouseRemovalRequest openRemoval = new HouseRemovalRequest();
        openRemoval.setId("rem-1");
        openRemoval.setHouseId("house-1");
        openRemoval.setStatus(RemovalStatus.OPEN);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(lastResident));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(lastResident));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc("house-1", MatchStatus.PENDING))
                .thenReturn(List.of(pending));
        when(houseRemovalRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc("house-1", RemovalStatus.OPEN))
                .thenReturn(List.of(openRemoval));

        membershipService.leaveHouse("user-1", "house-1", null);

        assertNull(lastResident.getHouseId());
        assertFalse(house.getIsAcceptingMatches());
        assertEquals(MatchStatus.CANCELLED, pending.getStatus());
        assertEquals(RemovalStatus.CANCELLED, openRemoval.getStatus());
        assertEquals(FIXED_NOW_UTC, openRemoval.getResolvedAt());
        verify(houseRepository).save(house);
        verify(houseRemovalRequestRepository).saveAll(List.of(openRemoval));
        verify(residenceHistoryService).recordExit("user-1", "house-1");
        verifyNoInteractions(choreRepository, groceryRepository);
    }

    @Test
    void leaveHouseShouldBeForbiddenForNonResidents() {
        User outsider = new User();
        outsider.setId("outsider");
        outsider.setHouseId("other-house");
        when(userRepository.findById("outsider")).thenReturn(Optional.of(outsider));

        assertThrows(ForbiddenOperationException.class,
                () -> membershipService.leaveHouse("outsider", "house-1", null));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(residenceHistoryService);
    }

    @Test
    void detachMemberShouldPromoteDeterministicSuccessorWhenSoleAdminIsRemoved() {
        User soleAdmin = resident("user-2", true);
        User u1 = resident("user-1", false);
        User u3 = resident("user-3", false);

        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(u1, soleAdmin, u3));
        when(choreRepository.findByHouseId("house-1")).thenReturn(List.of());
        when(groceryRepository.findByHouseIdAndIsPurchasedFalse("house-1")).thenReturn(List.of());
        when(houseRemovalRequestRepository.findOpenInvolvingUser("house-1", "user-2")).thenReturn(List.of());

        membershipService.detachMember(soleAdmin, "house-1");

        assertNull(soleAdmin.getHouseId());
        assertFalse(soleAdmin.getIsHouseAdmin());
        assertFalse(u1.getIsHouseAdmin());
        assertTrue(u3.getIsHouseAdmin(), "next resident by id after user-2 (user-3) should be promoted");
        verify(userRepository).save(u3);
        verify(residenceHistoryService).recordExit("user-2", "house-1");
    }
}
