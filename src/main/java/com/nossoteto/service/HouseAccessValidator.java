package com.nossoteto.service;

import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class HouseAccessValidator {

    private final UserRepository userRepository;

    public HouseAccessValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireAuthenticatedUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    public User requireHouseMember(String userId, String houseId) {
        User user = requireAuthenticatedUser(userId);
        if (houseId == null || !houseId.equals(user.getHouseId())) {
            throw new ForbiddenOperationException("User does not belong to this house");
        }
        return user;
    }

    public User requireHouseAdmin(String userId, String houseId) {
        User user = requireHouseMember(userId, houseId);
        if (!Boolean.TRUE.equals(user.getIsHouseAdmin())) {
            throw new ForbiddenOperationException("User is not an administrator of this house");
        }
        return user;
    }
}
