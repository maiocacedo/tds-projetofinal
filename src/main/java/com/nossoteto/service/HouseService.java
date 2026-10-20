package com.nossoteto.service;

import com.nossoteto.dto.CreateHouseRequest;
import com.nossoteto.dto.HouseMemberSummaryDto;
import com.nossoteto.dto.HouseResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.House;
import com.nossoteto.model.User;
import com.nossoteto.model.UserProfile;
import com.nossoteto.model.enums.VotingRule;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserProfileRepository;
import com.nossoteto.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class HouseService {

    private final HouseRepository houseRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final CompatibilityCalculator compatibilityCalculator;

    public HouseService(HouseRepository houseRepository,
                        UserRepository userRepository,
                        UserProfileRepository userProfileRepository,
                        HouseAccessValidator houseAccessValidator,
                        CompatibilityCalculator compatibilityCalculator) {
        this.houseRepository = houseRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.compatibilityCalculator = compatibilityCalculator;
    }

    @Transactional
    public HouseResponse createHouse(String userId, CreateHouseRequest request) {
        User user = houseAccessValidator.requireAuthenticatedUser(userId);
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BusinessRuleException("House name is required");
        }

        House house = new House();
        house.setId(UUID.randomUUID().toString());
        house.setName(request.getName().trim());
        house.setAddress(request.getAddress());
        house.setDescription(request.getDescription());
        house.setBasicRules(request.getBasicRules());
        house.setRentAmount(request.getRentAmount());
        house.setAllowsPets(request.getAllowsPets() != null ? request.getAllowsPets() : true);
        house.setAllowsSmokers(request.getAllowsSmokers() != null ? request.getAllowsSmokers() : false);
        house.setExpectedOrganizationLevel(
                request.getExpectedOrganizationLevel() != null ? request.getExpectedOrganizationLevel() : 3);
        house.setVisitorTolerance(
                request.getVisitorTolerance() != null ? request.getVisitorTolerance() : 3);
        house.setHasFurniture(request.getHasFurniture() != null ? request.getHasFurniture() : false);
        house.setVotingRule(request.getVotingRule() != null ? request.getVotingRule() : VotingRule.UNANIMITY);
        house.setIsAcceptingMatches(
                request.getIsAcceptingMatches() != null ? request.getIsAcceptingMatches() : true);

        House savedHouse = houseRepository.save(house);

        user.setHouseId(savedHouse.getId());
        user.setIsHouseAdmin(true);
        userRepository.save(user);

        List<HouseMemberSummaryDto> members = loadMembers(savedHouse.getId());
        return HouseResponse.fromEntity(savedHouse, null, members);
    }

    public List<HouseResponse> listOpenHouses(String userId,
                                              BigDecimal maxRent,
                                              Boolean allowsPets,
                                              Boolean allowsSmokers) {
        UserProfile profile = (userId != null && !userId.isBlank())
                ? userProfileRepository.findById(userId).orElse(null)
                : null;

        return houseRepository.findByIsAcceptingMatchesTrue().stream()
                .filter(house -> maxRent == null
                        || house.getRentAmount() == null
                        || house.getRentAmount().compareTo(maxRent) <= 0)
                .filter(house -> allowsPets == null
                        || Objects.equals(house.getAllowsPets(), allowsPets))
                .filter(house -> allowsSmokers == null
                        || Objects.equals(house.getAllowsSmokers(), allowsSmokers))
                .map(house -> {
                    Integer score = compatibilityCalculator.calculateScore(profile, house);
                    List<HouseMemberSummaryDto> members = loadMembers(house.getId());
                    return HouseResponse.fromEntity(house, score, members);
                })
                .toList();
    }

    public HouseResponse getHouseDetails(String userId, String houseId) {
        House house = houseRepository.findById(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("House not found: " + houseId));

        UserProfile profile = (userId != null && !userId.isBlank())
                ? userProfileRepository.findById(userId).orElse(null)
                : null;

        Integer score = compatibilityCalculator.calculateScore(profile, house);
        List<HouseMemberSummaryDto> members = loadMembers(houseId);
        return HouseResponse.fromEntity(house, score, members);
    }

    private List<HouseMemberSummaryDto> loadMembers(String houseId) {
        return userRepository.findByHouseIdOrderByIdAsc(houseId).stream()
                .map(HouseMemberSummaryDto::fromEntity)
                .toList();
    }
}
