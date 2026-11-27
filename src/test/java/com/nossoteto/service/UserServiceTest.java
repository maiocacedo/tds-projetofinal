package com.nossoteto.service;

import com.nossoteto.dto.PointsSummaryDto;
import com.nossoteto.dto.UserMeResponse;
import com.nossoteto.dto.UserProfileDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.UserProfile;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserProfileRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private HouseAccessValidator houseAccessValidator;

    @Mock
    private HouseRepository houseRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PointService pointService;

    @Mock
    private ResidenceHistoryService residenceHistoryService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                houseAccessValidator,
                houseRepository,
                userProfileRepository,
                userRepository,
                pointService,
                residenceHistoryService
        );
    }

    @Test
    void getCurrentUserShouldReturnUserWithHouseAndProfile() {
        User user = new User();
        user.setId("user-1");
        user.setName("Alice");
        user.setEmail("alice@test.com");
        user.setHouseId("house-1");
        user.setIsHouseAdmin(true);

        House house = new House();
        house.setId("house-1");
        house.setName("República Solar");

        UserProfile profile = new UserProfile();
        profile.setUserId("user-1");
        profile.setBudget(new BigDecimal("1500.00"));
        profile.setHasPets(true);
        profile.setIsSmoker(false);
        profile.setProfession("Dev");
        profile.setBio("Calma e organizada");
        profile.setAllergies("Poeira");
        profile.setOrganizationLevel(5);
        profile.setVisitorTolerance(3);
        profile.setHasFurniture(true);

        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(user);
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(profile));
        when(userRepository.findPermissionNamesByUserId("user-1"))
                .thenReturn(List.of("ROLE_MATCH", "ROLE_MANAGE_HOUSE", "ROLE_MANAGE_PROFILE"));
        when(pointService.getUserPointsSummary("user-1")).thenReturn(new PointsSummaryDto(45, 20));
        when(residenceHistoryService.calculateTotalResidenceMonths("user-1")).thenReturn(4);

        UserMeResponse response = userService.getCurrentUser("user-1");

        assertEquals("user-1", response.getId());
        assertEquals("Alice", response.getName());
        assertEquals("alice@test.com", response.getEmail());
        assertEquals("house-1", response.getHouseId());
        assertTrue(response.getIsHouseAdmin());
        assertEquals("República Solar", response.getHouseName());
        assertTrue(response.isProfileComplete());
        assertEquals(List.of("ROLE_MANAGE_HOUSE", "ROLE_MANAGE_PROFILE", "ROLE_MATCH"), response.getPermissions());
        assertEquals(new PointsSummaryDto(45, 20), response.getPoints());
        assertEquals(4, response.getResidenceMonths());
        assertNotNull(response.getProfile());
        assertEquals(new BigDecimal("1500.00"), response.getProfile().getBudget());
        assertTrue(response.getProfile().getHasPets());
        assertFalse(response.getProfile().getIsSmoker());
        assertEquals("Dev", response.getProfile().getProfession());
        assertEquals("Calma e organizada", response.getProfile().getBio());
        assertEquals("Poeira", response.getProfile().getAllergies());
        assertEquals(5, response.getProfile().getOrganizationLevel());
        assertEquals(3, response.getProfile().getVisitorTolerance());
        assertTrue(response.getProfile().getHasFurniture());
    }

    @Test
    void getCurrentUserShouldHandleUserWithoutHouseAndWithoutProfile() {
        User user = new User();
        user.setId("user-2");
        user.setName("Bob");
        user.setEmail("bob@test.com");
        user.setHouseId(null);
        user.setIsHouseAdmin(false);

        when(houseAccessValidator.requireAuthenticatedUser("user-2")).thenReturn(user);
        when(userProfileRepository.findById("user-2")).thenReturn(Optional.empty());
        when(userRepository.findPermissionNamesByUserId("user-2")).thenReturn(List.of());
        when(pointService.getUserPointsSummary("user-2")).thenReturn(new PointsSummaryDto(0, 0));
        when(residenceHistoryService.calculateTotalResidenceMonths("user-2")).thenReturn(null);

        UserMeResponse response = userService.getCurrentUser("user-2");

        assertEquals("user-2", response.getId());
        assertNull(response.getHouseId());
        assertNull(response.getHouseName());
        assertNull(response.getProfile());
        assertFalse(response.isProfileComplete());
        assertEquals(List.of(), response.getPermissions());
        assertEquals(new PointsSummaryDto(0, 0), response.getPoints());
        assertNull(response.getResidenceMonths());
        verifyNoInteractions(houseRepository);
    }

    @Test
    void getCurrentUserShouldMarkProfileIncompleteWhenRequiredFieldIsMissing() {
        User user = new User();
        user.setId("user-3");
        user.setName("Clara");
        user.setEmail("clara@test.com");
        user.setIsHouseAdmin(false);

        UserProfile incomplete = new UserProfile();
        incomplete.setUserId("user-3");
        incomplete.setBudget(new BigDecimal("1000.00"));
        incomplete.setHasPets(false);
        incomplete.setIsSmoker(false);
        incomplete.setOrganizationLevel(4);
        incomplete.setVisitorTolerance(null);

        when(houseAccessValidator.requireAuthenticatedUser("user-3")).thenReturn(user);
        when(userProfileRepository.findById("user-3")).thenReturn(Optional.of(incomplete));
        when(userRepository.findPermissionNamesByUserId("user-3")).thenReturn(List.of("ROLE_MANAGE_PROFILE"));
        when(pointService.getUserPointsSummary("user-3")).thenReturn(new PointsSummaryDto(0, 0));
        when(residenceHistoryService.calculateTotalResidenceMonths("user-3")).thenReturn(0);

        UserMeResponse response = userService.getCurrentUser("user-3");

        assertFalse(response.isProfileComplete());
        assertEquals(0, response.getResidenceMonths());
    }

    @Test
    void updateProfileShouldCreateOrUpdateProfileWhenValid() {
        User user = new User();
        user.setId("user-1");

        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(user);
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfileDto dto = new UserProfileDto();
        dto.setBudget(new BigDecimal("1200.50"));
        dto.setHasPets(false);
        dto.setIsSmoker(true);
        dto.setProfession("Estudante");
        dto.setBio("Estudo à noite");
        dto.setAllergies("Nenhuma");
        dto.setOrganizationLevel(4);
        dto.setVisitorTolerance(2);
        dto.setHasFurniture(false);

        UserProfileDto saved = userService.updateProfile("user-1", dto);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());

        UserProfile entity = captor.getValue();
        assertEquals("user-1", entity.getUserId());
        assertEquals(new BigDecimal("1200.50"), entity.getBudget());
        assertFalse(entity.getHasPets());
        assertTrue(entity.getIsSmoker());
        assertEquals(4, entity.getOrganizationLevel());
        assertEquals(2, entity.getVisitorTolerance());

        assertEquals(new BigDecimal("1200.50"), saved.getBudget());
        assertEquals(4, saved.getOrganizationLevel());
        assertEquals(2, saved.getVisitorTolerance());
    }

    @Test
    void updateProfileShouldThrowBusinessRuleExceptionWhenOrganizationLevelOutOfBounds() {
        User user = new User();
        user.setId("user-1");
        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(user);

        UserProfileDto dtoLow = new UserProfileDto();
        dtoLow.setOrganizationLevel(0);
        assertThrows(BusinessRuleException.class, () -> userService.updateProfile("user-1", dtoLow));

        UserProfileDto dtoHigh = new UserProfileDto();
        dtoHigh.setOrganizationLevel(6);
        assertThrows(BusinessRuleException.class, () -> userService.updateProfile("user-1", dtoHigh));
    }

    @Test
    void updateProfileShouldThrowBusinessRuleExceptionWhenVisitorToleranceOutOfBounds() {
        User user = new User();
        user.setId("user-1");
        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(user);

        UserProfileDto dtoLow = new UserProfileDto();
        dtoLow.setVisitorTolerance(0);
        assertThrows(BusinessRuleException.class, () -> userService.updateProfile("user-1", dtoLow));

        UserProfileDto dtoHigh = new UserProfileDto();
        dtoHigh.setVisitorTolerance(6);
        assertThrows(BusinessRuleException.class, () -> userService.updateProfile("user-1", dtoHigh));
    }

    // ---------- B5: boas-vindas ----------

    @Test
    void getCurrentUserShouldExposePendingWelcome() {
        User user = new User();
        user.setId("user-1");
        user.setPendingWelcome(true);

        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(user);
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.empty());
        when(pointService.getUserPointsSummary("user-1")).thenReturn(new PointsSummaryDto(0, 0));

        UserMeResponse response = userService.getCurrentUser("user-1");

        assertTrue(response.isPendingWelcome());
    }

    @Test
    void getCurrentUserShouldTreatNullPendingWelcomeAsFalse() {
        User user = new User();
        user.setId("user-1");
        user.setPendingWelcome(null);

        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(user);
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.empty());
        when(pointService.getUserPointsSummary("user-1")).thenReturn(new PointsSummaryDto(0, 0));

        assertFalse(userService.getCurrentUser("user-1").isPendingWelcome());
    }

    @Test
    void acknowledgeWelcomeShouldClearPendingWelcomeAndSave() {
        User user = new User();
        user.setId("user-1");
        user.setPendingWelcome(true);
        when(houseAccessValidator.requireAuthenticatedUser("user-1")).thenReturn(user);

        userService.acknowledgeWelcome("user-1");

        assertFalse(user.getPendingWelcome());
        verify(userRepository).save(user);
    }
}
