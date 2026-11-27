package com.nossoteto.service;

import com.nossoteto.dto.CandidateCardDto;
import com.nossoteto.dto.ConfirmMoveRequest;
import com.nossoteto.dto.CreateMatchRequestDto;
import com.nossoteto.dto.MatchRequestResponse;
import com.nossoteto.dto.MyMatchRequestDto;
import com.nossoteto.dto.UserMeResponse;
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
import com.nossoteto.util.AppTime;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final HouseMembershipService houseMembershipService;
    private final UserService userService;
    private final Clock clock;

    static final long MOVE_RESERVATION_HOURS = 72;
    private static final List<MatchStatus> OPEN_STATUSES = List.of(MatchStatus.PENDING, MatchStatus.AWAITING_MOVE);

    public MatchService(MatchRequestRepository matchRequestRepository,
                        MatchVoteRepository matchVoteRepository,
                        HouseRepository houseRepository,
                        UserRepository userRepository,
                        UserProfileRepository userProfileRepository,
                        HouseAccessValidator houseAccessValidator,
                        CompatibilityCalculator compatibilityCalculator,
                        VotingEvaluator votingEvaluator,
                        ResidenceHistoryService residenceHistoryService,
                        PointService pointService,
                        HouseMembershipService houseMembershipService,
                        UserService userService,
                        Clock clock) {
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
        this.houseMembershipService = houseMembershipService;
        this.userService = userService;
        this.clock = clock;
    }

    @Transactional
    public MatchRequestResponse createMatchRequest(String userId, CreateMatchRequestDto dto) {
        // An overdue reservation must not keep blocking a new request to the same house.
        expireOverdueMoves();
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

        // A resident may ask for another house (B6 move); only their own house is refused.
        if (houseId.equals(user.getHouseId())) {
            throw new BusinessRuleException("user already belongs to this house");
        }

        if (matchRequestRepository.existsByUserIdAndHouseIdAndStatusIn(userId, houseId, OPEN_STATUSES)) {
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
        expireOverdueMoves();
        houseAccessValidator.requireHouseMember(userId, houseId);
        House house = houseRepository.findById(houseId)
                .orElseThrow(() -> new ResourceNotFoundException("House not found: " + houseId));

        // AWAITING_MOVE too: the admin must see the reservation to be able to retract it.
        List<MatchRequest> requests = matchRequestRepository.findByHouseIdAndStatusInOrderByCreatedAtAsc(
                houseId, OPEN_STATUSES);
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
        expireOverdueMoves();
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
        expireOverdueMoves();
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
                // The candidate lives in another house: reserve the vacancy for 72 h (AWAITING_MOVE)
                // and let them confirm the move. Nobody is moved out of their house here.
                LocalDateTime deadline = reserveMove(requestId, houseId, candidateId, candidate.getHouseId());
                matchRequest.setStatus(MatchStatus.AWAITING_MOVE);
                matchRequest.setMoveDeadline(deadline);
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

    private LocalDateTime reserveMove(String requestId, String houseId, String candidateId, String currentHouseId) {
        if (houseId.equals(currentHouseId)) {
            throw new ConflictException("candidate already belongs to this house");
        }
        reserveVacancyOrThrow(houseId);
        LocalDateTime deadline = AppTime.nowUtc(clock).plusHours(MOVE_RESERVATION_HOURS);
        if (matchRequestRepository.transitionPendingToAwaitingMove(requestId, deadline) == 0) {
            throw new ConflictException("request is no longer pending");
        }
        // Re-check after the bulk updates cleared the context: a concurrent move may have put the
        // candidate in this house already. Throwing rolls back the vacancy and the transition.
        User reloaded = userRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + candidateId));
        if (houseId.equals(reloaded.getHouseId())) {
            throw new ConflictException("candidate already belongs to this house");
        }
        return deadline;
    }

    /**
     * Turns overdue AWAITING_MOVE reservations into EXPIRED and gives each vacancy back. Runs
     * periodically and at the start of the match reads/actions; both paths give the same result.
     * The vacancy is only returned when this transaction actually expired the row, so the job
     * and a concurrent read never give it back twice. Never readOnly: it writes.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void expireOverdueMoves() {
        LocalDateTime now = AppTime.nowUtc(clock);
        List<MatchRequest> overdue = matchRequestRepository
                .findByStatusAndMoveDeadlineLessThanEqual(MatchStatus.AWAITING_MOVE, now);
        for (MatchRequest req : overdue) {
            if (matchRequestRepository.expireAwaitingMove(req.getId(), now) == 1) {
                houseRepository.incrementVacancy(req.getHouseId());
            }
        }
    }

    /**
     * Confirms an AWAITING_MOVE reservation: leaves the current house (same handover as "leave",
     * heir included) and joins the target house in a single transaction. Any failure, such as a
     * missing heir ("new admin required"), rolls back everything.
     */
    @Transactional
    public UserMeResponse confirmMove(String userId, String requestId, ConfirmMoveRequest body) {
        expireOverdueMoves();
        MatchRequest request = requireOwnAwaitingMove(userId, requestId);
        String targetHouseId = request.getHouseId();

        User user = houseAccessValidator.requireAuthenticatedUser(userId);
        if (userRepository.findByHouseIdOrderByIdAsc(targetHouseId).isEmpty()) {
            throw new ConflictException("house is no longer active");
        }

        String oldHouseId = user.getHouseId();
        if (oldHouseId != null) {
            if (oldHouseId.equals(targetHouseId)) {
                throw new ConflictException("user already belongs to this house");
            }
            // Not caught on purpose: a BusinessRuleException here must roll the whole move back.
            houseMembershipService.leaveHouse(userId, oldHouseId, body != null ? body.getNewAdminId() : null);
        }

        if (matchRequestRepository.transitionAwaitingMoveTo(requestId, MatchStatus.ACCEPTED) == 0) {
            throw new ConflictException("request is not awaiting move");
        }

        // The bulk update cleared the persistence context: reload before writing.
        User mover = houseAccessValidator.requireAuthenticatedUser(userId);
        mover.setHouseId(targetHouseId);
        mover.setIsHouseAdmin(false);
        mover.setPendingWelcome(false);
        userRepository.save(mover);
        residenceHistoryService.recordEntry(userId, targetHouseId);

        // Only one move can win: every other reservation of this user is cancelled and its vacancy returned.
        for (MatchRequest other : matchRequestRepository.findByUserIdAndStatus(userId, MatchStatus.AWAITING_MOVE)) {
            if (!other.getId().equals(requestId)
                    && matchRequestRepository.transitionAwaitingMoveTo(other.getId(), MatchStatus.CANCELLED) == 1) {
                houseRepository.incrementVacancy(other.getHouseId());
            }
        }

        return userService.getCurrentUser(userId);
    }

    /** The resident gives up on the move: WITHDRAWN and the vacancy goes back to the house. */
    @Transactional
    public void declineMove(String userId, String requestId) {
        expireOverdueMoves();
        MatchRequest request = requireOwnAwaitingMove(userId, requestId);
        releaseReservation(request, MatchStatus.WITHDRAWN);
    }

    /** The target house's admin takes the reservation back: CANCELLED and the vacancy is returned. */
    @Transactional
    public void retractMove(String userId, String requestId) {
        expireOverdueMoves();
        MatchRequest request = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Match request not found: " + requestId));
        houseAccessValidator.requireHouseAdmin(userId, request.getHouseId());
        if (request.getStatus() != MatchStatus.AWAITING_MOVE) {
            throw new ConflictException("request is not awaiting move");
        }
        releaseReservation(request, MatchStatus.CANCELLED);
    }

    private MatchRequest requireOwnAwaitingMove(String userId, String requestId) {
        MatchRequest request = matchRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Match request not found: " + requestId));
        if (!request.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("only the owner can perform this action");
        }
        if (request.getStatus() != MatchStatus.AWAITING_MOVE) {
            throw new ConflictException("request is not awaiting move");
        }
        return request;
    }

    private void releaseReservation(MatchRequest request, MatchStatus newStatus) {
        if (matchRequestRepository.transitionAwaitingMoveTo(request.getId(), newStatus) == 0) {
            throw new ConflictException("request is not awaiting move");
        }
        houseRepository.incrementVacancy(request.getHouseId());
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
                req.getCreatedAt(), votesCast, voterIds.size(), req.getMoveDeadline());
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
        response.setMoveDeadline(req.getMoveDeadline());
        return response;
    }
}
