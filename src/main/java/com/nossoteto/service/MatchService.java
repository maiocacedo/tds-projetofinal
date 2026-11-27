package com.nossoteto.service;

import com.nossoteto.dto.CandidateCardDto;
import com.nossoteto.dto.CreateMatchRequestDto;
import com.nossoteto.dto.MatchRequestResponse;
import com.nossoteto.dto.MyMatchRequestDto;
import com.nossoteto.dto.UserProfileDto;
import com.nossoteto.dto.VoteMatchResponse;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.dto.VoterSummaryDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ConflictException;
import com.nossoteto.exception.ForbiddenOperationException;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ResidenceHistoryService residenceHistoryService;
    private final PointService pointService;

    public MatchService(MatchRequestRepository matchRequestRepository,
                        MatchVoteRepository matchVoteRepository,
                        HouseRepository houseRepository,
                        UserRepository userRepository,
                        UserProfileRepository userProfileRepository,
                        HouseAccessValidator houseAccessValidator,
                        CompatibilityCalculator compatibilityCalculator,
                        VotingEvaluator votingEvaluator,
                        ResidenceHistoryService residenceHistoryService,
                        PointService pointService) {
        this.matchRequestRepository = matchRequestRepository;
        this.matchVoteRepository = matchVoteRepository;
        this.houseRepository = houseRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.houseAccessValidator = houseAccessValidator;
        this.compatibilityCalculator = compatibilityCalculator;
        this.votingEvaluator = votingEvaluator;
        this.residenceHistoryService = residenceHistoryService;
        this.pointService = pointService;
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

        // Paused and full are independent settings, but either one closes the door to new requests.
        if (!Boolean.TRUE.equals(house.getIsAcceptingMatches())
                || house.getVacancies() == null || house.getVacancies() <= 0) {
            throw new BusinessRuleException("house is not accepting requests");
        }

        if (houseId.equals(user.getHouseId())) {
            throw new BusinessRuleException("User already belongs to this house");
        }
        if (user.getHouseId() != null) {
            throw new BusinessRuleException("User already belongs to a house; leave it before requesting another one");
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

    // Not readOnly on purpose: B6 hooks request expiration into these reads.
    @Transactional
    public List<MatchRequestResponse> listHouseMatchRequests(String userId, String houseId) {
        houseAccessValidator.requireHouseMember(userId, houseId);
        House house = houseRepository.findById(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("House not found: " + houseId));

        List<MatchRequest> requests = matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc(
                houseId, MatchStatus.PENDING);
        List<User> residents = userRepository.findByHouseIdOrderByIdAsc(houseId);

        return requests.stream()
                .map(req -> {
                    User candidate = userRepository.findById(req.getUserId()).orElse(null);
                    MatchRequestResponse response = buildMatchRequestResponse(req, candidate, house);
                    enrichForResidents(response, req, candidate, residents, userId);
                    return response;
                })
                .toList();
    }

    /**
     * "Minhas solicitações": every request of the caller, any status, newest first.
     * Not readOnly on purpose: B6 hooks request expiration into this read.
     */
    @Transactional
    public List<MyMatchRequestDto> listMyMatchRequests(String userId) {
        houseAccessValidator.requireAuthenticatedUser(userId);
        return matchRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toMyMatchRequest)
                .toList();
    }

    /**
     * The candidate gives up on a pending request. Uses the PENDING compare-and-set so a
     * concurrent acceptance cannot be overwritten with WITHDRAWN. Vacancies are untouched.
     */
    @Transactional
    public void withdrawMatchRequest(String userId, String requestId) {
        houseAccessValidator.requireAuthenticatedUser(userId);
        MatchRequest request = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Match request not found: " + requestId));
        if (!request.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("only the owner can perform this action");
        }
        if (request.getStatus() != MatchStatus.PENDING
                || matchRequestRepository.transitionStatusFromPending(requestId, MatchStatus.WITHDRAWN) == 0) {
            throw new BusinessRuleException("request is not pending");
        }
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
        String houseId = house.getId();

        Set<String> memberIds = userRepository.findByHouseIdOrderByIdAsc(houseId).stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        int totalMembers = memberIds.size();

        // Upsert: a resident can change their vote while the request is PENDING.
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

        // Votes from people who have since left the house no longer count.
        List<MatchVote> currentVotes = matchVoteRepository.findByRequestId(requestId).stream()
                .filter(v -> memberIds.contains(v.getVoterId()))
                .toList();
        int approveCount = (int) currentVotes.stream().filter(v -> v.getVote() == VoteType.APPROVE).count();
        int rejectCount = (int) currentVotes.stream().filter(v -> v.getVote() == VoteType.REJECT).count();

        VotingOutcome outcome = votingEvaluator.evaluate(house.getVotingRule(), totalMembers, approveCount, rejectCount);

        boolean hasOtherPending = false;
        List<String> otherPendingIds = List.of();

        if (outcome == VotingOutcome.APPROVED) {
            String candidateId = matchRequest.getUserId();
            User candidate = userRepository.findById(candidateId)
                    .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + candidateId));

            if (candidate.getHouseId() != null) {
                // The candidate kept this request open (RF06) but joined another house meanwhile:
                // close the request instead of silently moving them out of their current house.
                // No vacancy is consumed here. Conditional update: never overwrite a concurrent
                // ACCEPTED/WITHDRAWN.
                if (matchRequestRepository.transitionStatusFromPending(requestId, MatchStatus.CANCELLED) == 0) {
                    throw new ConflictException("request is no longer pending");
                }
                matchRequest.setStatus(MatchStatus.CANCELLED);
                return buildVoteResponse(requestId, matchRequest.getStatus(), approveCount, rejectCount,
                        totalMembers, false, List.of());
            }

            // First write of the acceptance. On 409 the whole transaction rolls back,
            // including the vote saved above: the request stays PENDING.
            reserveVacancyOrThrow(houseId);
            if (matchRequestRepository.transitionStatusFromPending(requestId, MatchStatus.ACCEPTED) == 0) {
                throw new ConflictException("request is no longer pending");
            }
            matchRequest.setStatus(MatchStatus.ACCEPTED);

            // The bulk updates cleared the persistence context: reload instead of reusing detached
            // entities, and never save `house` here (it would restore the old vacancy count).
            User acceptedCandidate = userRepository.findById(candidateId)
                    .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + candidateId));
            // Re-check after the reload: under READ COMMITTED another house may have accepted the
            // candidate concurrently. Throwing rolls back the vacancy and the status transition.
            if (acceptedCandidate.getHouseId() != null) {
                throw new ConflictException("candidate already belongs to a house");
            }
            acceptedCandidate.setHouseId(houseId);
            acceptedCandidate.setIsHouseAdmin(false);
            acceptedCandidate.setPendingWelcome(true);
            userRepository.save(acceptedCandidate);
            residenceHistoryService.recordEntry(candidateId, houseId);

            otherPendingIds = matchRequestRepository.findByUserIdAndStatus(candidateId, MatchStatus.PENDING)
                    .stream()
                    .map(MatchRequest::getId)
                    .filter(id -> !id.equals(requestId))
                    .toList();
            hasOtherPending = !otherPendingIds.isEmpty();
        } else if (outcome == VotingOutcome.REJECTED) {
            // Conditional update: never overwrite a concurrent ACCEPTED/WITHDRAWN.
            if (matchRequestRepository.transitionStatusFromPending(requestId, MatchStatus.REJECTED) == 0) {
                throw new ConflictException("request is no longer pending");
            }
            matchRequest.setStatus(MatchStatus.REJECTED);
        }

        return buildVoteResponse(requestId, matchRequest.getStatus(), approveCount, rejectCount,
                totalMembers, hasOtherPending, otherPendingIds);
    }

    /**
     * Atomically takes one vacancy of the house or fails with 409. Deliberately joins the
     * caller's transaction (no REQUIRES_NEW) so a later failure also gives the vacancy back.
     * Reused by B6.
     */
    void reserveVacancyOrThrow(String houseId) {
        if (houseRepository.decrementVacancyIfAvailable(houseId) == 0) {
            throw new ConflictException("house has no available vacancies");
        }
    }

    private VoteMatchResponse buildVoteResponse(String requestId, MatchStatus status, int approveCount,
                                                int rejectCount, int totalMembers, boolean hasOtherPending,
                                                List<String> otherPendingIds) {
        VoteMatchResponse response = new VoteMatchResponse();
        response.setRequestId(requestId);
        response.setStatus(status);
        response.setApproveVotes(approveCount);
        response.setRejectVotes(rejectCount);
        response.setTotalHouseMembers(totalMembers);
        response.setHasOtherPendingRequests(hasOtherPending);
        response.setOtherPendingRequestIds(otherPendingIds);
        return response;
    }

    @Transactional
    public Map<String, Object> cancelOtherPendingRequests(String userId) {
        User user = houseAccessValidator.requireAuthenticatedUser(userId);
        List<MatchRequest> pending = matchRequestRepository.findByUserIdAndStatus(userId, MatchStatus.PENDING);
        for (MatchRequest req : pending) {
            req.setStatus(MatchStatus.CANCELLED);
        }
        matchRequestRepository.saveAll(pending);
        // Answering "what about your other requests?" also dismisses the welcome screen.
        user.setPendingWelcome(false);
        userRepository.save(user);
        return Map.of("cancelledCount", pending.size());
    }

    private MyMatchRequestDto toMyMatchRequest(MatchRequest req) {
        String houseName = houseRepository.findById(req.getHouseId()).map(House::getName).orElse(null);
        // Only current residents vote; once accepted, the requester lives there but is not a voter.
        Set<String> voterIds = userRepository.findByHouseIdOrderByIdAsc(req.getHouseId()).stream()
                .map(User::getId)
                .filter(id -> !id.equals(req.getUserId()))
                .collect(Collectors.toSet());
        int votesCast = (int) matchVoteRepository.findByRequestId(req.getId()).stream()
                .filter(v -> voterIds.contains(v.getVoterId()))
                .count();
        return new MyMatchRequestDto(req.getId(), req.getHouseId(), houseName, req.getStatus(),
                req.getCreatedAt(), votesCast, voterIds.size(), null);
    }

    private void enrichForResidents(MatchRequestResponse response, MatchRequest req, User candidate,
                                    List<User> residents, String callerId) {
        Map<String, VoteType> votesByVoter = matchVoteRepository.findByRequestId(req.getId()).stream()
                .collect(Collectors.toMap(MatchVote::getVoterId, MatchVote::getVote, (a, b) -> b));

        // Who voted, never how: the direction stays private to each voter.
        List<VoterSummaryDto> voters = residents.stream()
                .filter(resident -> votesByVoter.containsKey(resident.getId()))
                .map(resident -> new VoterSummaryDto(resident.getId(), resident.getName()))
                .toList();
        VoteType myVote = votesByVoter.get(callerId);

        response.setCandidate(new CandidateCardDto(
                req.getUserId(),
                candidate != null ? candidate.getName() : null,
                response.getCompatibilityScore(),
                pointService.getUserPointsSummary(req.getUserId()).total(),
                residenceHistoryService.calculateTotalResidenceMonths(req.getUserId())));
        response.setVoters(voters);
        response.setMyVote(myVote != null ? myVote.name() : null);
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
