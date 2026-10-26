package com.nossoteto.service;

import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseAccessValidatorTest {

    @Mock
    private UserRepository userRepository;

    private HouseAccessValidator houseAccessValidator;

    @BeforeEach
    void setUp() {
        houseAccessValidator = new HouseAccessValidator(userRepository);
    }

    @Test
    void requireAuthenticatedUserShouldReturnUserWhenExists() {
        User user = new User();
        user.setId("user-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        User result = houseAccessValidator.requireAuthenticatedUser("user-1");
        assertEquals("user-1", result.getId());
    }

    @Test
    void requireAuthenticatedUserShouldThrowNotFoundWhenMissing() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> houseAccessValidator.requireAuthenticatedUser("unknown"));
    }

    @Test
    void requireHouseMemberShouldReturnUserWhenBelongsToHouse() {
        User user = new User();
        user.setId("user-1");
        user.setHouseId("house-1");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        User result = houseAccessValidator.requireHouseMember("user-1", "house-1");
        assertEquals("house-1", result.getHouseId());
    }

    @Test
    void requireHouseMemberShouldThrowForbiddenWhenBelongsToAnotherHouseOrNone() {
        User userOtherHouse = new User();
        userOtherHouse.setId("user-1");
        userOtherHouse.setHouseId("house-2");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userOtherHouse));

        assertThrows(ForbiddenOperationException.class,
                () -> houseAccessValidator.requireHouseMember("user-1", "house-1"));

        User userWithoutHouse = new User();
        userWithoutHouse.setId("user-2");
        userWithoutHouse.setHouseId(null);
        when(userRepository.findById("user-2")).thenReturn(Optional.of(userWithoutHouse));

        assertThrows(ForbiddenOperationException.class,
                () -> houseAccessValidator.requireHouseMember("user-2", "house-1"));
    }

    @Test
    void requireHouseAdminShouldReturnUserWhenAdminOfHouse() {
        User admin = new User();
        admin.setId("user-1");
        admin.setHouseId("house-1");
        admin.setIsHouseAdmin(true);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin));

        User result = houseAccessValidator.requireHouseAdmin("user-1", "house-1");
        assertTrue(result.getIsHouseAdmin());
    }

    @Test
    void requireHouseAdminShouldThrowForbiddenWhenRegularMember() {
        User member = new User();
        member.setId("user-1");
        member.setHouseId("house-1");
        member.setIsHouseAdmin(false);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(member));

        assertThrows(ForbiddenOperationException.class,
                () -> houseAccessValidator.requireHouseAdmin("user-1", "house-1"));
    }
}
