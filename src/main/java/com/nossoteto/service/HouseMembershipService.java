package com.nossoteto.service;

import com.nossoteto.dto.HouseMemberSummaryDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Chore;
import com.nossoteto.model.Grocery;
import com.nossoteto.model.HouseRemovalRequest;
import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.MatchStatus;
import com.nossoteto.model.enums.RemovalStatus;
import com.nossoteto.repository.ChoreRepository;
import com.nossoteto.repository.GroceryRepository;
import com.nossoteto.repository.HouseRemovalRequestRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.MatchRequestRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.util.AppTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles house admin management and resident departure (leaving voluntarily or removed by vote)
 * without leaving the house orphaned: admin rights, chores and grocery assignments are handed over
 * to the remaining residents.
 */
@Service
public class HouseMembershipService {

    private final UserRepository userRepository;
    private final HouseRepository houseRepository;
    private final ChoreRepository choreRepository;
    private final GroceryRepository groceryRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final HouseRemovalRequestRepository houseRemovalRequestRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final ResidenceHistoryService residenceHistoryService;
    private final Clock clock;

    public HouseMembershipService(UserRepository userRepository,
                                  HouseRepository houseRepository,
                                  ChoreRepository choreRepository,
                                  GroceryRepository groceryRepository,
                                  MatchRequestRepository matchRequestRepository,
                                  HouseRemovalRequestRepository houseRemovalRequestRepository,
                                  HouseAccessValidator houseAccessValidator,
                                  ResidenceHistoryService residenceHistoryService,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.houseRepository = houseRepository;
        this.choreRepository = choreRepository;
        this.groceryRepository = groceryRepository;
        this.matchRequestRepository = matchRequestRepository;
        this.houseRemovalRequestRepository = houseRemovalRequestRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.residenceHistoryService = residenceHistoryService;
        this.clock = clock;
    }

    @Transactional
    public HouseMemberSummaryDto updateMemberAdminStatus(String adminId,
                                                         String houseId,
                                                         String memberId,
                                                         Boolean isAdmin) {
        houseAccessValidator.requireHouseAdmin(adminId, houseId);
        if (isAdmin == null) {
            throw new BusinessRuleException("isAdmin is required");
        }
        User member = userRepository.findById(memberId)
                .filter(u -> houseId.equals(u.getHouseId()))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in this house: " + memberId));

        if (!isAdmin && Boolean.TRUE.equals(member.getIsHouseAdmin())) {
            boolean hasOtherAdmin = userRepository.findByHouseIdOrderByIdAsc(houseId).stream()
                    .anyMatch(u -> !u.getId().equals(memberId) && Boolean.TRUE.equals(u.getIsHouseAdmin()));
            if (!hasOtherAdmin) {
                throw new BusinessRuleException("house must keep at least one admin");
            }
        }

        member.setIsHouseAdmin(isAdmin);
        User saved = userRepository.save(member);
        return HouseMemberSummaryDto.fromEntity(saved, true);
    }

    /**
     * Leaves the house. Uses Propagation.REQUIRED so B6 can invoke it inside the confirm-move
     * transaction and roll everything back if validation fails.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void leaveHouse(String userId, String houseId, String newAdminId) {
        User member = houseAccessValidator.requireHouseMember(userId, houseId);

        List<User> remaining = userRepository.findByHouseIdOrderByIdAsc(houseId).stream()
                .filter(u -> !u.getId().equals(userId))
                .toList();

        if (remaining.isEmpty()) {
            detachMember(member, houseId);
            return;
        }

        boolean wasAdmin = Boolean.TRUE.equals(member.getIsHouseAdmin());
        boolean hasOtherAdmin = remaining.stream()
                .anyMatch(u -> Boolean.TRUE.equals(u.getIsHouseAdmin()));

        User chosenAdmin = null;
        if (wasAdmin && !hasOtherAdmin) {
            if (newAdminId == null || newAdminId.isBlank() || newAdminId.trim().equals(userId)) {
                throw new BusinessRuleException("new admin required");
            }
            String heirId = newAdminId.trim();
            chosenAdmin = remaining.stream()
                    .filter(u -> heirId.equals(u.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("new admin required"));
        }

        if (chosenAdmin != null) {
            chosenAdmin.setIsHouseAdmin(true);
            userRepository.save(chosenAdmin);
        }

        detachMember(member, houseId);
    }

    /**
     * Package-private handover used by both leaveHouse and HouseRemovalService (on APPROVED vote).
     * Guarantees the house never ends up without an admin while residents remain.
     */
    void detachMember(User member, String houseId) {
        boolean wasAdmin = Boolean.TRUE.equals(member.getIsHouseAdmin());
        member.setHouseId(null);
        member.setIsHouseAdmin(false);
        userRepository.save(member);
        residenceHistoryService.recordExit(member.getId(), houseId);

        List<User> remaining = userRepository.findByHouseIdOrderByIdAsc(houseId).stream()
                .filter(u -> !u.getId().equals(member.getId()))
                .toList();

        if (remaining.isEmpty()) {
            closeEmptyHouse(houseId);
            return;
        }

        cancelOpenRemovalsInvolving(houseId, member.getId());

        // Same deterministic order used by the chore rotation: the next resident after the one leaving.
        User successor = remaining.stream()
                .filter(u -> u.getId().compareTo(member.getId()) > 0)
                .findFirst()
                .orElse(remaining.get(0));

        if (wasAdmin && remaining.stream().noneMatch(u -> Boolean.TRUE.equals(u.getIsHouseAdmin()))) {
            successor.setIsHouseAdmin(true);
            userRepository.save(successor);
        }

        List<Chore> orphanChores = choreRepository.findByHouseId(houseId).stream()
                .filter(c -> member.getId().equals(c.getCurrentAssigneeId()))
                .toList();
        orphanChores.forEach(c -> c.setCurrentAssigneeId(successor.getId()));
        choreRepository.saveAll(orphanChores);

        List<Grocery> orphanGroceries = groceryRepository.findByHouseIdAndIsPurchasedFalse(houseId).stream()
                .filter(g -> member.getId().equals(g.getAssigneeId()))
                .toList();
        orphanGroceries.forEach(g -> g.setAssigneeId(null));
        groceryRepository.saveAll(orphanGroceries);
    }

    private void cancelOpenRemovalsInvolving(String houseId, String userId) {
        List<HouseRemovalRequest> openInvolving =
                houseRemovalRequestRepository.findOpenInvolvingUser(houseId, userId);
        if (openInvolving.isEmpty()) {
            return;
        }
        LocalDateTime now = AppTime.nowUtc(clock);
        openInvolving.forEach(r -> {
            r.setStatus(RemovalStatus.CANCELLED);
            r.setResolvedAt(now);
        });
        houseRemovalRequestRepository.saveAll(openInvolving);
    }

    // Nobody is left to vote, so the house stops accepting candidates and open requests are closed.
    private void closeEmptyHouse(String houseId) {
        houseRepository.findById(houseId).ifPresent(house -> {
            house.setIsAcceptingMatches(false);
            houseRepository.save(house);
        });
        List<MatchRequest> pending =
                matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc(houseId, MatchStatus.PENDING);
        pending.forEach(r -> r.setStatus(MatchStatus.CANCELLED));
        matchRequestRepository.saveAll(pending);

        List<HouseRemovalRequest> openRemovals =
                houseRemovalRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc(houseId, RemovalStatus.OPEN);
        if (!openRemovals.isEmpty()) {
            LocalDateTime now = AppTime.nowUtc(clock);
            openRemovals.forEach(r -> {
                r.setStatus(RemovalStatus.CANCELLED);
                r.setResolvedAt(now);
            });
            houseRemovalRequestRepository.saveAll(openRemovals);
        }
    }
}
