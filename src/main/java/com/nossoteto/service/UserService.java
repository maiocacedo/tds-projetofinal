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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final HouseAccessValidator houseAccessValidator;
    private final HouseRepository houseRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final ResidenceHistoryService residenceHistoryService;

    public UserService(HouseAccessValidator houseAccessValidator,
                       HouseRepository houseRepository,
                       UserProfileRepository userProfileRepository,
                       UserRepository userRepository,
                       PointService pointService,
                       ResidenceHistoryService residenceHistoryService) {
        this.houseAccessValidator = houseAccessValidator;
        this.houseRepository = houseRepository;
        this.userProfileRepository = userProfileRepository;
        this.userRepository = userRepository;
        this.pointService = pointService;
        this.residenceHistoryService = residenceHistoryService;
    }

    @Transactional(readOnly = true)
    public UserMeResponse getCurrentUser(String userId) {
        User user = houseAccessValidator.requireAuthenticatedUser(userId);
        String houseName = null;
        if (user.getHouseId() != null) {
            houseName = houseRepository.findById(user.getHouseId())
                    .map(House::getName)
                    .orElse(null);
        }
        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        boolean profileComplete = CompatibilityCalculator.isProfileComplete(profile);
        List<String> rawPermissions = userRepository.findPermissionNamesByUserId(userId);
        List<String> permissions = rawPermissions == null
                ? List.of()
                : rawPermissions.stream().sorted().toList();
        PointsSummaryDto points = pointService.getUserPointsSummary(userId);
        Integer residenceMonths = residenceHistoryService.calculateTotalResidenceMonths(userId);
        return new UserMeResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getHouseId(),
                user.getIsHouseAdmin(),
                houseName,
                UserProfileDto.fromEntity(profile),
                profileComplete,
                permissions,
                points,
                residenceMonths,
                Boolean.TRUE.equals(user.getPendingWelcome())
        );
    }

    @Transactional
    public void acknowledgeWelcome(String userId) {
        User user = houseAccessValidator.requireAuthenticatedUser(userId);
        user.setPendingWelcome(false);
        userRepository.save(user);
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
