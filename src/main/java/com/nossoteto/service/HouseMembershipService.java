package com.nossoteto.service;

import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Chore;
import com.nossoteto.model.Grocery;
import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.MatchStatus;
import com.nossoteto.repository.ChoreRepository;
import com.nossoteto.repository.GroceryRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.MatchRequestRepository;
import com.nossoteto.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles a resident leaving a house (on their own or removed by the admin) without leaving the house orphaned:
 * admin rights, chores and grocery assignments are handed over to the remaining residents.
 */
@Service
public class HouseMembershipService {

    private final UserRepository userRepository;
    private final HouseRepository houseRepository;
    private final ChoreRepository choreRepository;
    private final GroceryRepository groceryRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final HouseAccessValidator houseAccessValidator;

    public HouseMembershipService(UserRepository userRepository,
                                  HouseRepository houseRepository,
                                  ChoreRepository choreRepository,
                                  GroceryRepository groceryRepository,
                                  MatchRequestRepository matchRequestRepository,
                                  HouseAccessValidator houseAccessValidator) {
        this.userRepository = userRepository;
        this.houseRepository = houseRepository;
        this.choreRepository = choreRepository;
        this.groceryRepository = groceryRepository;
        this.matchRequestRepository = matchRequestRepository;
        this.houseAccessValidator = houseAccessValidator;
    }

    @Transactional
    public void leaveHouse(String userId, String houseId) {
        User member = houseAccessValidator.requireHouseMember(userId, houseId);
        detachMember(member, houseId);
    }

    @Transactional
    public void removeMember(String adminId, String houseId, String memberId) {
        houseAccessValidator.requireHouseAdmin(adminId, houseId);
        if (adminId.equals(memberId)) {
            throw new BusinessRuleException("Admins cannot remove themselves; use the leave endpoint instead");
        }
        User member = userRepository.findById(memberId)
                .filter(u -> houseId.equals(u.getHouseId()))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in this house: " + memberId));
        detachMember(member, houseId);
    }

    private void detachMember(User member, String houseId) {
        boolean wasAdmin = Boolean.TRUE.equals(member.getIsHouseAdmin());
        member.setHouseId(null);
        member.setIsHouseAdmin(false);
        userRepository.save(member);

        List<User> remaining = userRepository.findByHouseIdOrderByIdAsc(houseId).stream()
                .filter(u -> !u.getId().equals(member.getId()))
                .toList();

        if (remaining.isEmpty()) {
            closeEmptyHouse(houseId);
            return;
        }

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

    // Nobody is left to vote, so the house stops accepting candidates and pending requests are closed.
    private void closeEmptyHouse(String houseId) {
        houseRepository.findById(houseId).ifPresent(house -> {
            house.setIsAcceptingMatches(false);
            houseRepository.save(house);
        });
        List<MatchRequest> pending =
                matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc(houseId, MatchStatus.PENDING);
        pending.forEach(r -> r.setStatus(MatchStatus.CANCELLED));
        matchRequestRepository.saveAll(pending);
    }
}
