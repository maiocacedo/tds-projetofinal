package com.nossoteto.service;

import com.nossoteto.dto.UserMeResponse;
import com.nossoteto.dto.UserProfileDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.UserProfile;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(houseAccessValidator, houseRepository, userProfileRepository);
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

        UserMeResponse response = userService.getCurrentUser("user-1");

        assertEquals("user-1", response.getId());
        assertEquals("Alice", response.getName());
        assertEquals("alice@test.com", response.getEmail());
        assertEquals("house-1", response.getHouseId());
        assertTrue(response.getIsHouseAdmin());
        assertEquals("República Solar", response.getHouseName());
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

        UserMeResponse response = userService.getCurrentUser("user-2");

        assertEquals("user-2", response.getId());
        assertNull(response.getHouseId());
        assertNull(response.getHouseName());
        assertNull(response.getProfile());
        verifyNoInteractions(houseRepository);
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
}
