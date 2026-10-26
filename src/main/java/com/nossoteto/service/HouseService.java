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
        if (user.getHouseId() != null) {
            throw new BusinessRuleException("User already belongs to a house; leave it before creating a new one");
        }
        validateHouseAttributes(request);

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

        List<HouseMemberSummaryDto> members = loadMembers(savedHouse.getId(), true);
        return HouseResponse.fromEntity(savedHouse, null, members);
    }

    /**
     * Partial update restricted to the house admin: only the fields present in the request are changed.
     */
    @Transactional
    public HouseResponse updateHouse(String userId, String houseId, CreateHouseRequest request) {
        houseAccessValidator.requireHouseAdmin(userId, houseId);
        if (request == null) {
            throw new BusinessRuleException("Request body is required");
        }
        if (request.getName() != null && request.getName().isBlank()) {
            throw new BusinessRuleException("House name cannot be blank");
        }
        validateHouseAttributes(request);

        House house = houseRepository.findById(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("House not found: " + houseId));

        if (request.getName() != null) house.setName(request.getName().trim());
        if (request.getAddress() != null) house.setAddress(request.getAddress());
        if (request.getDescription() != null) house.setDescription(request.getDescription());
        if (request.getBasicRules() != null) house.setBasicRules(request.getBasicRules());
        if (request.getRentAmount() != null) house.setRentAmount(request.getRentAmount());
        if (request.getAllowsPets() != null) house.setAllowsPets(request.getAllowsPets());
        if (request.getAllowsSmokers() != null) house.setAllowsSmokers(request.getAllowsSmokers());
        if (request.getExpectedOrganizationLevel() != null) {
            house.setExpectedOrganizationLevel(request.getExpectedOrganizationLevel());
        }
        if (request.getVisitorTolerance() != null) house.setVisitorTolerance(request.getVisitorTolerance());
        if (request.getHasFurniture() != null) house.setHasFurniture(request.getHasFurniture());
        if (request.getVotingRule() != null) house.setVotingRule(request.getVotingRule());
        if (request.getIsAcceptingMatches() != null) house.setIsAcceptingMatches(request.getIsAcceptingMatches());

        House saved = houseRepository.save(house);
        return HouseResponse.fromEntity(saved, null, loadMembers(houseId, true));
    }

    public List<HouseResponse> listOpenHouses(String userId,
                                              BigDecimal maxRent,
                                              Boolean allowsPets,
                                              Boolean allowsSmokers) {
        UserProfile profile = (userId != null && !userId.isBlank())
                ? userProfileRepository.findById(userId).orElse(null)
                : null;
        String viewerHouseId = findViewerHouseId(userId);

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
                    List<HouseMemberSummaryDto> members =
                            loadMembers(house.getId(), house.getId().equals(viewerHouseId));
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
        List<HouseMemberSummaryDto> members = loadMembers(houseId, houseId.equals(findViewerHouseId(userId)));
        return HouseResponse.fromEntity(house, score, members);
    }

    private String findViewerHouseId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userRepository.findById(userId).map(User::getHouseId).orElse(null);
    }

    // Residents' e-mails are only visible to people who live in the same house (RNF09).
    private List<HouseMemberSummaryDto> loadMembers(String houseId, boolean includeEmails) {
        return userRepository.findByHouseIdOrderByIdAsc(houseId).stream()
                .map(user -> HouseMemberSummaryDto.fromEntity(user, includeEmails))
                .toList();
    }

    private void validateHouseAttributes(CreateHouseRequest request) {
        if (request.getRentAmount() != null && request.getRentAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Rent amount cannot be negative");
        }
        validateScale(request.getExpectedOrganizationLevel(), "expectedOrganizationLevel");
        validateScale(request.getVisitorTolerance(), "visitorTolerance");
    }

    private void validateScale(Integer value, String fieldName) {
        if (value != null && (value < 1 || value > 5)) {
            throw new BusinessRuleException(fieldName + " must be between 1 and 5");
        }
    }
}
