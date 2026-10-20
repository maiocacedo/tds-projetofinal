package com.nossoteto.service;

import com.nossoteto.dto.UserMeResponse;
import com.nossoteto.dto.UserProfileDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.UserProfile;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final HouseAccessValidator houseAccessValidator;
    private final HouseRepository houseRepository;
    private final UserProfileRepository userProfileRepository;

    public UserService(HouseAccessValidator houseAccessValidator,
                       HouseRepository houseRepository,
                       UserProfileRepository userProfileRepository) {
        this.houseAccessValidator = houseAccessValidator;
        this.houseRepository = houseRepository;
        this.userProfileRepository = userProfileRepository;
    }

    public UserMeResponse getCurrentUser(String userId) {
        User user = houseAccessValidator.requireAuthenticatedUser(userId);
        String houseName = null;
        if (user.getHouseId() != null) {
            houseName = houseRepository.findById(user.getHouseId())
                    .map(House::getName)
                    .orElse(null);
        }
        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        return new UserMeResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getHouseId(),
                user.getIsHouseAdmin(),
                houseName,
                UserProfileDto.fromEntity(profile)
        );
    }

    @Transactional
    public UserProfileDto updateProfile(String userId, UserProfileDto dto) {
        houseAccessValidator.requireAuthenticatedUser(userId);
        validateScale(dto.getOrganizationLevel(), "organizationLevel");
        validateScale(dto.getVisitorTolerance(), "visitorTolerance");

        UserProfile profile = userProfileRepository.findById(userId).orElseGet(() -> {
            UserProfile created = new UserProfile();
            created.setUserId(userId);
            return created;
        });

        profile.setBudget(dto.getBudget());
        if (dto.getHasPets() != null) {
            profile.setHasPets(dto.getHasPets());
        }
        if (dto.getIsSmoker() != null) {
            profile.setIsSmoker(dto.getIsSmoker());
        }
        profile.setProfession(dto.getProfession());
        profile.setBio(dto.getBio());
        profile.setAllergies(dto.getAllergies());
        profile.setOrganizationLevel(dto.getOrganizationLevel());
        profile.setVisitorTolerance(dto.getVisitorTolerance());
        if (dto.getHasFurniture() != null) {
            profile.setHasFurniture(dto.getHasFurniture());
        }

        UserProfile saved = userProfileRepository.save(profile);
        return UserProfileDto.fromEntity(saved);
    }

    private void validateScale(Integer value, String fieldName) {
        if (value != null && (value < 1 || value > 5)) {
            throw new BusinessRuleException(fieldName + " must be between 1 and 5");
        }
    }
}
