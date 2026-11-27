package com.nossoteto.service;

import com.nossoteto.dto.CreateRemovalRequestDto;
import com.nossoteto.dto.RemovalRequestResponse;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.dto.VoterSummaryDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ConflictException;
import com.nossoteto.exception.ForbiddenOperationException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.House;
import com.nossoteto.model.HouseRemovalRequest;
import com.nossoteto.model.HouseRemovalVote;
import com.nossoteto.model.User;
import com.nossoteto.model.enums.RemovalStatus;
import com.nossoteto.model.enums.VoteType;
import com.nossoteto.model.enums.VotingOutcome;
import com.nossoteto.repository.HouseRemovalRequestRepository;
import com.nossoteto.repository.HouseRemovalVoteRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.util.AppTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HouseRemovalService {

    private static final int MAX_REASON_LENGTH = 500;

    private final HouseRemovalRequestRepository removalRequestRepository;
    private final HouseRemovalVoteRepository removalVoteRepository;
    private final HouseRepository houseRepository;
    private final UserRepository userRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final VotingEvaluator votingEvaluator;
    private final HouseMembershipService houseMembershipService;
    private final Clock clock;

    public HouseRemovalService(HouseRemovalRequestRepository removalRequestRepository,
                               HouseRemovalVoteRepository removalVoteRepository,
                               HouseRepository houseRepository,
                               UserRepository userRepository,
                               HouseAccessValidator houseAccessValidator,
                               VotingEvaluator votingEvaluator,
                               HouseMembershipService houseMembershipService,
                               Clock clock) {
        this.removalRequestRepository = removalRequestRepository;
        this.removalVoteRepository = removalVoteRepository;
        this.houseRepository = houseRepository;
        this.userRepository = userRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.votingEvaluator = votingEvaluator;
        this.houseMembershipService = houseMembershipService;
        this.clock = clock;
    }

    @Transactional
    public RemovalRequestResponse createRemovalRequest(String adminId, String houseId, CreateRemovalRequestDto dto) {
        houseAccessValidator.requireHouseAdmin(adminId, houseId);
        if (dto == null || dto.getMemberId() == null || dto.getMemberId().isBlank()) {
            throw new BusinessRuleException("memberId is required");
        }
        String targetUserId = dto.getMemberId().trim();
        if (adminId.equals(targetUserId)) {
            throw new BusinessRuleException("cannot propose removal of yourself");
        }

        String normalizedReason = normalizeReason(dto.getReason());

        userRepository.findById(targetUserId)
                .filter(u -> houseId.equals(u.getHouseId()))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in this house: " + targetUserId));

        if (removalRequestRepository.existsByHouseIdAndTargetUserIdAndStatus(
                houseId, targetUserId, RemovalStatus.OPEN)) {
            throw new ConflictException("removal already open for this member");
        }

        HouseRemovalRequest request = new HouseRemovalRequest();
        request.setId(UUID.randomUUID().toString());
        request.setHouseId(houseId);
        request.setTargetUserId(targetUserId);
        request.setProposedById(adminId);
        request.setReason(normalizedReason);
        request.setStatus(RemovalStatus.OPEN);
        request.setCreatedAt(AppTime.nowUtc(clock));
        request.setResolvedAt(null);

        HouseRemovalRequest saved = removalRequestRepository.save(request);
        List<User> residents = userRepository.findByHouseIdOrderByIdAsc(houseId);
        return buildResponse(saved, residents, List.of(), adminId);
    }

    @Transactional(readOnly = true)
    public List<RemovalRequestResponse> listOpenRemovals(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        List<HouseRemovalRequest> openRemovals =
                removalRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc(houseId, RemovalStatus.OPEN);
        List<User> residents = userRepository.findByHouseIdOrderByIdAsc(houseId);

        return openRemovals.stream()
                .map(req -> buildResponse(req, residents, removalVoteRepository.findByRemovalId(req.getId()), userId))
                .toList();
    }

    @Transactional
    public RemovalRequestResponse voteOnRemoval(String voterId, String houseId, String removalId, VoteRequestDto dto) {
        houseAccessValidator.requireHouseMember(voterId, houseId);
        HouseRemovalRequest removal = removalRequestRepository.findByIdAndHouseId(removalId, houseId)
                .orElseThrow(() -> new ResourceNotFoundException("Removal request not found: " + removalId));

        if (voterId.equals(removal.getTargetUserId())) {
            throw new ForbiddenOperationException("target member cannot vote on own removal");
        }
        if (removal.getStatus() != RemovalStatus.OPEN) {
            throw new BusinessRuleException("removal request is not open");
        }

        VoteType voteType = VoteType.fromString(dto != null ? dto.getVote() : null);

        House house = houseRepository.findById(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("House not found: " + houseId));

        List<User> residents = userRepository.findByHouseIdOrderByIdAsc(houseId);
        List<User> eligibleResidents = residents.stream()
                .filter(u -> !u.getId().equals(removal.getTargetUserId()))
                .toList();
        Set<String> eligibleIds = eligibleResidents.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        LocalDateTime now = AppTime.nowUtc(clock);
        HouseRemovalVote vote = removalVoteRepository.findByRemovalIdAndVoterId(removalId, voterId)
                .orElseGet(() -> {
                    HouseRemovalVote v = new HouseRemovalVote();
                    v.setId(UUID.randomUUID().toString());
                    v.setRemovalId(removalId);
                    v.setVoterId(voterId);
                    v.setCreatedAt(now);
                    return v;
                });
        vote.setVote(voteType);
        removalVoteRepository.save(vote);

        List<HouseRemovalVote> allVotes = removalVoteRepository.findByRemovalId(removalId);
        List<HouseRemovalVote> eligibleVotes = allVotes.stream()
                .filter(v -> eligibleIds.contains(v.getVoterId()))
                .toList();
        int approveCount = (int) eligibleVotes.stream().filter(v -> v.getVote() == VoteType.APPROVE).count();
        int rejectCount = (int) eligibleVotes.stream().filter(v -> v.getVote() == VoteType.REJECT).count();

        VotingOutcome outcome = votingEvaluator.evaluate(
                house.getVotingRule(), eligibleResidents.size(), approveCount, rejectCount);

        if (outcome == VotingOutcome.APPROVED) {
            if (removalRequestRepository.transitionStatusFromOpen(removalId, RemovalStatus.APPROVED, now) == 0) {
                throw new ConflictException("removal request is no longer open");
            }
            removal.setStatus(RemovalStatus.APPROVED);
            removal.setResolvedAt(now);

            // Bulk update cleared the persistence context: reload target before detaching.
            User target = userRepository.findById(removal.getTargetUserId()).orElse(null);
            if (target != null && houseId.equals(target.getHouseId())) {
                houseMembershipService.detachMember(target, houseId);
            }
        } else if (outcome == VotingOutcome.REJECTED) {
            if (removalRequestRepository.transitionStatusFromOpen(removalId, RemovalStatus.REJECTED, now) == 0) {
                throw new ConflictException("removal request is no longer open");
            }
            removal.setStatus(RemovalStatus.REJECTED);
            removal.setResolvedAt(now);
        }

        return buildResponse(removal, residents, allVotes, voterId);
    }

    @Transactional
    public void cancelRemovalRequest(String userId, String houseId, String removalId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        HouseRemovalRequest removal = removalRequestRepository.findByIdAndHouseId(removalId, houseId)
                .orElseThrow(() -> new ResourceNotFoundException("Removal request not found: " + removalId));

        if (!removal.getProposedById().equals(userId)) {
            throw new ForbiddenOperationException("only the owner can perform this action");
        }
        LocalDateTime now = AppTime.nowUtc(clock);
        if (removal.getStatus() != RemovalStatus.OPEN
                || removalRequestRepository.transitionStatusFromOpen(removalId, RemovalStatus.CANCELLED, now) == 0) {
            throw new BusinessRuleException("removal request is not open");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new BusinessRuleException("reason must be at most 500 characters");
        }
        return trimmed;
    }

    private RemovalRequestResponse buildResponse(HouseRemovalRequest removal,
                                                 List<User> residents,
                                                 List<HouseRemovalVote> votes,
                                                 String callerId) {
        Map<String, User> residentById = residents.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<User> eligibleResidents = residents.stream()
                .filter(u -> !u.getId().equals(removal.getTargetUserId()))
                .toList();
        Set<String> eligibleIds = eligibleResidents.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        Map<String, VoteType> votesByVoter = votes.stream()
                .filter(v -> eligibleIds.contains(v.getVoterId()))
                .collect(Collectors.toMap(HouseRemovalVote::getVoterId, HouseRemovalVote::getVote, (a, b) -> b));

        // Who voted, never how: direction stays private to the caller's own myVote.
        List<VoterSummaryDto> voters = eligibleResidents.stream()
                .filter(r -> votesByVoter.containsKey(r.getId()))
                .map(r -> new VoterSummaryDto(r.getId(), r.getName()))
                .toList();

        VoteType myVote = votesByVoter.get(callerId);
        String targetUserName = resolveUserName(removal.getTargetUserId(), residentById);
        String proposedByName = resolveUserName(removal.getProposedById(), residentById);

        return new RemovalRequestResponse(
                removal.getId(),
                removal.getHouseId(),
                removal.getTargetUserId(),
                targetUserName,
                removal.getProposedById(),
                proposedByName,
                removal.getReason(),
                removal.getStatus(),
                removal.getCreatedAt(),
                removal.getResolvedAt(),
                voters,
                myVote != null ? myVote.name() : null,
                voters.size(),
                eligibleResidents.size()
        );
    }

    private String resolveUserName(String userId, Map<String, User> residentById) {
        User cached = residentById.get(userId);
        if (cached != null) {
            return cached.getName();
        }
        return userRepository.findById(userId).map(User::getName).orElse(null);
    }
}
