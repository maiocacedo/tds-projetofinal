package com.nossoteto.service;

import com.nossoteto.dto.CreateMatchRequestDto;
import com.nossoteto.dto.MatchRequestResponse;
import com.nossoteto.dto.UserProfileDto;
import com.nossoteto.dto.VoteMatchResponse;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.House;
import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.MatchVote;
import com.nossoteto.model.User;
import com.nossoteto.model.UserProfile;
import com.nossoteto.model.enums.MatchStatus;
import com.nossoteto.model.enums.VoteType;
import com.nossoteto.model.enums.VotingOutcome;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.MatchRequestRepository;
import com.nossoteto.repository.MatchVoteRepository;
import com.nossoteto.repository.UserProfileRepository;
import com.nossoteto.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MatchService {

    private final MatchRequestRepository matchRequestRepository;
    private final MatchVoteRepository matchVoteRepository;
    private final HouseRepository houseRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final HouseAccessValidator houseAccessValidator;
    private final CompatibilityCalculator compatibilityCalculator;
    private final VotingEvaluator votingEvaluator;

    public MatchService(MatchRequestRepository matchRequestRepository,
                        MatchVoteRepository matchVoteRepository,
                        HouseRepository houseRepository,
                        UserRepository userRepository,
                        UserProfileRepository userProfileRepository,
                        HouseAccessValidator houseAccessValidator,
                        CompatibilityCalculator compatibilityCalculator,
                        VotingEvaluator votingEvaluator) {
        this.matchRequestRepository = matchRequestRepository;
        this.matchVoteRepository = matchVoteRepository;
        this.houseRepository = houseRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.compatibilityCalculator = compatibilityCalculator;
        this.votingEvaluator = votingEvaluator;
    }

    @Transactional
    public MatchRequestResponse createMatchRequest(String userId, CreateMatchRequestDto dto) {
        User user = houseAccessValidator.requireAuthenticatedUser(userId);
        if (dto == null || dto.getHouseId() == null || dto.getHouseId().isBlank()) {
            throw new BusinessRuleException("House ID is required");
        }
        String houseId = dto.getHouseId().trim();

        House house = houseRepository.findById(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("House not found: " + houseId));

        if (!Boolean.TRUE.equals(house.getIsAcceptingMatches())) {
            throw new BusinessRuleException("House is not accepting matches");
        }

        if (houseId.equals(user.getHouseId())) {
            throw new BusinessRuleException("User already belongs to this house");
        }

        if (matchRequestRepository.existsByUserIdAndHouseIdAndStatus(userId, houseId, MatchStatus.PENDING)) {
            throw new BusinessRuleException("Match request already pending for this house");
        }

        MatchRequest matchRequest = new MatchRequest();
        matchRequest.setId(UUID.randomUUID().toString());
        matchRequest.setUserId(userId);
        matchRequest.setHouseId(houseId);
        matchRequest.setStatus(MatchStatus.PENDING);
        matchRequest.setCreatedAt(LocalDateTime.now());

        MatchRequest saved = matchRequestRepository.save(matchRequest);
        return buildMatchRequestResponse(saved, user, house);
    }

    public List<MatchRequestResponse> listHouseMatchRequests(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        House house = houseRepository.findById(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("House not found: " + houseId));

        List<MatchRequest> requests = matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc(
                houseId, MatchStatus.PENDING);

        return requests.stream()
                .map(req -> {
                    User candidate = userRepository.findById(req.getUserId()).orElse(null);
                    return buildMatchRequestResponse(req, candidate, house);
                })
                .toList();
    }

    @Transactional
    public VoteMatchResponse voteOnMatchRequest(String voterId, String requestId, VoteRequestDto dto) {
        MatchRequest matchRequest = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Match request not found: " + requestId));

        if (matchRequest.getStatus() != MatchStatus.PENDING) {
            throw new BusinessRuleException("Match request is no longer pending");
        }

        houseAccessValidator.requireHouseMember(voterId, matchRequest.getHouseId());

        VoteType voteType = VoteType.fromString(dto != null ? dto.getVote() : null);

        House house = houseRepository.findById(matchRequest.getHouseId())
                .orElseThrow(() -> new ResourceNotFoundException("House not found: " + matchRequest.getHouseId()));

        int totalMembers = (int) userRepository.countByHouseId(house.getId());

        MatchVote matchVote = matchVoteRepository.findByRequestIdAndVoterId(requestId, voterId)
                .orElseGet(() -> {
                    MatchVote v = new MatchVote();
                    v.setId(UUID.randomUUID().toString());
                    v.setRequestId(requestId);
                    v.setVoterId(voterId);
                    return v;
                });
        matchVote.setVote(voteType);
        matchVoteRepository.save(matchVote);

        List<MatchVote> allVotes = matchVoteRepository.findByRequestId(requestId);
        int approveCount = (int) allVotes.stream().filter(v -> v.getVote() == VoteType.APPROVE).count();
        int rejectCount = (int) allVotes.stream().filter(v -> v.getVote() == VoteType.REJECT).count();

        VotingOutcome outcome = votingEvaluator.evaluate(house.getVotingRule(), totalMembers, approveCount, rejectCount);

        boolean hasOtherPending = false;
        List<String> otherPendingIds = List.of();

        if (outcome == VotingOutcome.APPROVED) {
            matchRequest.setStatus(MatchStatus.ACCEPTED);
            matchRequestRepository.save(matchRequest);

            User candidate = userRepository.findById(matchRequest.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + matchRequest.getUserId()));
            candidate.setHouseId(house.getId());
            candidate.setIsHouseAdmin(false);
            userRepository.save(candidate);

            otherPendingIds = matchRequestRepository.findByUserIdAndStatus(candidate.getId(), MatchStatus.PENDING)
                    .stream()
                    .map(MatchRequest::getId)
                    .filter(id -> !id.equals(requestId))
                    .toList();
            hasOtherPending = !otherPendingIds.isEmpty();
        } else if (outcome == VotingOutcome.REJECTED) {
            matchRequest.setStatus(MatchStatus.REJECTED);
            matchRequestRepository.save(matchRequest);
        }

        VoteMatchResponse response = new VoteMatchResponse();
        response.setRequestId(requestId);
        response.setStatus(matchRequest.getStatus());
        response.setApproveVotes(approveCount);
        response.setRejectVotes(rejectCount);
        response.setTotalHouseMembers(totalMembers);
        response.setHasOtherPendingRequests(hasOtherPending);
        response.setOtherPendingRequestIds(otherPendingIds);
        return response;
    }

    @Transactional
    public Map<String, Object> cancelOtherPendingRequests(String userId) {
        houseAccessValidator.requireAuthenticatedUser(userId);
        List<MatchRequest> pending = matchRequestRepository.findByUserIdAndStatus(userId, MatchStatus.PENDING);
        for (MatchRequest req : pending) {
            req.setStatus(MatchStatus.CANCELLED);
        }
        matchRequestRepository.saveAll(pending);
        return Map.of("cancelledCount", pending.size());
    }

    private MatchRequestResponse buildMatchRequestResponse(MatchRequest req, User candidate, House house) {
        UserProfile profile = userProfileRepository.findById(req.getUserId()).orElse(null);
        Integer score = compatibilityCalculator.calculateScore(profile, house);

        MatchRequestResponse response = new MatchRequestResponse();
        response.setId(req.getId());
        response.setUserId(req.getUserId());
        if (candidate != null) {
            response.setUserName(candidate.getName());
            response.setUserEmail(candidate.getEmail());
        }
        response.setHouseId(req.getHouseId());
        response.setStatus(req.getStatus());
        response.setCompatibilityScore(score);
        response.setUserProfile(UserProfileDto.fromEntity(profile));
        response.setCreatedAt(req.getCreatedAt());
        return response;
    }
}
