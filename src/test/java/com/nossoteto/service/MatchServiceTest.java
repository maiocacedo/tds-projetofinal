package com.nossoteto.service;

import com.nossoteto.dto.ConfirmMoveRequest;
import com.nossoteto.dto.CreateMatchRequestDto;
import com.nossoteto.dto.CandidateCardDto;
import com.nossoteto.dto.MatchRequestResponse;
import com.nossoteto.dto.MyMatchRequestDto;
import com.nossoteto.dto.UserMeResponse;
import com.nossoteto.dto.PointsSummaryDto;
import com.nossoteto.dto.VoterSummaryDto;
import com.nossoteto.dto.VoteMatchResponse;
import com.nossoteto.dto.VoteRequestDto;
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
import com.nossoteto.model.enums.VotingRule;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.MatchRequestRepository;
import com.nossoteto.repository.MatchVoteRepository;
import com.nossoteto.repository.UserProfileRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRequestRepository matchRequestRepository;

    @Mock
    private MatchVoteRepository matchVoteRepository;

    @Mock
    private HouseRepository houseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ResidenceHistoryService residenceHistoryService;

    @Mock
    private PointService pointService;

    @Mock
    private HouseMembershipService houseMembershipService;

    @Mock
    private UserService userService;

    private static final Instant FIXED_NOW = Instant.parse("2026-11-27T12:00:00Z");
    private static final LocalDateTime FIXED_NOW_UTC = LocalDateTime.ofInstant(FIXED_NOW, ZoneOffset.UTC);
    private static final List<MatchStatus> OPEN = List.of(MatchStatus.PENDING, MatchStatus.AWAITING_MOVE);

    private HouseAccessValidator houseAccessValidator;
    private CompatibilityCalculator compatibilityCalculator;
    private VotingEvaluator votingEvaluator;
    private MatchService matchService;

    @BeforeEach
    void setUp() {
        houseAccessValidator = new HouseAccessValidator(userRepository);
        compatibilityCalculator = new CompatibilityCalculator();
        votingEvaluator = new VotingEvaluator();
        matchService = new MatchService(
                matchRequestRepository,
                matchVoteRepository,
                houseRepository,
                userRepository,
                userProfileRepository,
                houseAccessValidator,
                compatibilityCalculator,
                votingEvaluator,
                residenceHistoryService,
                pointService,
                houseMembershipService,
                userService,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    private User member(String id) {
        User user = new User();
        user.setId(id);
        user.setHouseId("house-1");
        return user;
    }

    @Test
    void createMatchRequestShouldSavePendingRequestWithCompatibilityScore() {
        User candidate = new User();
        candidate.setId("candidate-1");
        candidate.setName("Carlos");
        candidate.setEmail("carlos@test.com");

        House house = new House();
        house.setId("house-1");
        house.setName("República Solar");
        house.setIsAcceptingMatches(true);
        house.setRentAmount(new BigDecimal("1200.00"));
        house.setAllowsPets(true);
        house.setAllowsSmokers(false);
        house.setExpectedOrganizationLevel(4);
        house.setVisitorTolerance(3);

        UserProfile profile = new UserProfile();
        profile.setUserId("candidate-1");
        profile.setBudget(new BigDecimal("1500.00"));
        profile.setHasPets(false);
        profile.setIsSmoker(false);
        profile.setOrganizationLevel(4);
        profile.setVisitorTolerance(3);

        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(matchRequestRepository.existsByUserIdAndHouseIdAndStatusIn("candidate-1", "house-1", OPEN))
                .thenReturn(false);
        when(userProfileRepository.findById("candidate-1")).thenReturn(Optional.of(profile));
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMatchRequestDto dto = new CreateMatchRequestDto();
        dto.setHouseId("house-1");

        MatchRequestResponse response = matchService.createMatchRequest("candidate-1", dto);

        assertNotNull(response.getId());
        assertEquals("candidate-1", response.getUserId());
        assertEquals("Carlos", response.getUserName());
        assertEquals("carlos@test.com", response.getUserEmail());
        assertEquals("house-1", response.getHouseId());
        assertEquals(MatchStatus.PENDING, response.getStatus());
        assertEquals(100, response.getCompatibilityScore());
        assertNotNull(response.getCreatedAt());
    }

    @Test
    void createMatchRequestShouldRejectDuplicatePendingRequest() {
        User candidate = new User();
        candidate.setId("candidate-1");

        House house = new House();
        house.setId("house-1");
        house.setIsAcceptingMatches(true);

        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(matchRequestRepository.existsByUserIdAndHouseIdAndStatusIn("candidate-1", "house-1", OPEN))
                .thenReturn(true);

        CreateMatchRequestDto dto = new CreateMatchRequestDto();
        dto.setHouseId("house-1");

        assertThrows(BusinessRuleException.class, () -> matchService.createMatchRequest("candidate-1", dto));
        verify(matchRequestRepository, never()).save(any());
    }

    @Test
    void listHouseMatchRequestsShouldThrow403WhenUserIsNotHouseMember() {
        User outsider = new User();
        outsider.setId("outsider-1");
        outsider.setHouseId("other-house");

        when(userRepository.findById("outsider-1")).thenReturn(Optional.of(outsider));

        assertThrows(ForbiddenOperationException.class,
                () -> matchService.listHouseMatchRequests("outsider-1", "house-1"));
    }

    @Test
    void listHouseMatchRequestsShouldReturnEnrichedPendingRequestsForMember() {
        User member = new User();
        member.setId("member-1");
        member.setHouseId("house-1");

        House house = new House();
        house.setId("house-1");
        house.setRentAmount(new BigDecimal("1000.00"));
        house.setAllowsPets(false);
        house.setAllowsSmokers(false);
        house.setExpectedOrganizationLevel(4);
        house.setVisitorTolerance(3);

        MatchRequest req = new MatchRequest();
        req.setId("req-1");
        req.setUserId("candidate-1");
        req.setHouseId("house-1");
        req.setStatus(MatchStatus.PENDING);
        req.setCreatedAt(LocalDateTime.now());

        User candidate = new User();
        candidate.setId("candidate-1");
        candidate.setName("Ana");
        candidate.setEmail("ana@test.com");

        UserProfile profile = new UserProfile();
        profile.setUserId("candidate-1");
        profile.setBudget(new BigDecimal("1200.00"));
        profile.setHasPets(true); // conflict with house allowsPets=false (-20)
        profile.setIsSmoker(false);
        profile.setOrganizationLevel(4);
        profile.setVisitorTolerance(3);

        when(userRepository.findById("member-1")).thenReturn(Optional.of(member));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(matchRequestRepository.findByHouseIdAndStatusInOrderByCreatedAtAsc("house-1", OPEN))
                .thenReturn(List.of(req));
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(userProfileRepository.findById("candidate-1")).thenReturn(Optional.of(profile));
        when(pointService.getUserPointsSummary("candidate-1")).thenReturn(new PointsSummaryDto(30, 10));

        List<MatchRequestResponse> list = matchService.listHouseMatchRequests("member-1", "house-1");

        assertEquals(1, list.size());
        assertEquals("req-1", list.get(0).getId());
        assertEquals("Ana", list.get(0).getUserName());
        assertEquals(80, list.get(0).getCompatibilityScore());
        assertNotNull(list.get(0).getUserProfile());
    }

    @Test
    void voteOnMatchRequestWithUnanimityShouldApproveWhenAllMembersApproveAndReportOtherPendingRequests() {
        MatchRequest req = new MatchRequest();
        req.setId("req-1");
        req.setUserId("candidate-1");
        req.setHouseId("house-1");
        req.setStatus(MatchStatus.PENDING);

        User voter2 = new User();
        voter2.setId("voter-2");
        voter2.setHouseId("house-1");

        User candidate = new User();
        candidate.setId("candidate-1");
        candidate.setName("Candidato");

        House house = new House();
        house.setId("house-1");
        house.setVotingRule(VotingRule.UNANIMITY);

        MatchVote vote1 = new MatchVote();
        vote1.setId("v-1");
        vote1.setRequestId("req-1");
        vote1.setVoterId("voter-1");
        vote1.setVote(VoteType.APPROVE);

        MatchVote vote2 = new MatchVote();
        vote2.setId("v-2");
        vote2.setRequestId("req-1");
        vote2.setVoterId("voter-2");
        vote2.setVote(VoteType.APPROVE);

        MatchRequest otherReq = new MatchRequest();
        otherReq.setId("req-other");
        otherReq.setUserId("candidate-1");
        otherReq.setHouseId("house-2");
        otherReq.setStatus(MatchStatus.PENDING);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-2")).thenReturn(Optional.of(voter2));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(member("voter-1"), voter2));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-2")).thenReturn(Optional.empty());
        when(matchVoteRepository.save(any(MatchVote.class))).thenReturn(vote2);
        when(matchVoteRepository.findByRequestId("req-1")).thenReturn(List.of(vote1, vote2));
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(matchRequestRepository.findByUserIdAndStatus("candidate-1", MatchStatus.PENDING))
                .thenReturn(List.of(otherReq));
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(1);
        when(matchRequestRepository.transitionStatusFromPending("req-1", MatchStatus.ACCEPTED)).thenReturn(1);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APROVAR");

        VoteMatchResponse response = matchService.voteOnMatchRequest("voter-2", "req-1", voteDto);

        assertEquals(MatchStatus.ACCEPTED, response.getStatus());
        assertEquals(2, response.getApproveVotes());
        assertEquals(0, response.getRejectVotes());
        assertEquals(2, response.getTotalHouseMembers());
        assertTrue(response.isHasOtherPendingRequests());
        assertEquals(List.of("req-other"), response.getOtherPendingRequestIds());

        assertEquals("house-1", candidate.getHouseId());
        assertFalse(candidate.getIsHouseAdmin());
        assertTrue(candidate.getPendingWelcome());
        verify(userRepository).save(candidate);
        verify(houseRepository).decrementVacancyIfAvailable("house-1");
        verify(matchRequestRepository).transitionStatusFromPending("req-1", MatchStatus.ACCEPTED);
        verify(matchRequestRepository, never()).save(any(MatchRequest.class));
        // The atomic decrement already updated the row: saving the stale House would undo it.
        verify(houseRepository, never()).save(any(House.class));
        verify(residenceHistoryService).recordEntry("candidate-1", "house-1");
    }

    @Test
    void voteOnMatchRequestWithUnanimityShouldRejectImmediatelyOnFirstRejectVote() {
        MatchRequest req = new MatchRequest();
        req.setId("req-1");
        req.setUserId("candidate-1");
        req.setHouseId("house-1");
        req.setStatus(MatchStatus.PENDING);

        User voter1 = new User();
        voter1.setId("voter-1");
        voter1.setHouseId("house-1");

        House house = new House();
        house.setId("house-1");
        house.setVotingRule(VotingRule.UNANIMITY);

        MatchVote rejectVote = new MatchVote();
        rejectVote.setId("v-1");
        rejectVote.setRequestId("req-1");
        rejectVote.setVoterId("voter-1");
        rejectVote.setVote(VoteType.REJECT);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-1")).thenReturn(Optional.of(voter1));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1"))
                .thenReturn(List.of(voter1, member("voter-2"), member("voter-3")));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-1")).thenReturn(Optional.empty());
        when(matchVoteRepository.save(any(MatchVote.class))).thenReturn(rejectVote);
        when(matchVoteRepository.findByRequestId("req-1")).thenReturn(List.of(rejectVote));
        when(matchRequestRepository.transitionStatusFromPending("req-1", MatchStatus.REJECTED)).thenReturn(1);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("REJEITAR");

        VoteMatchResponse response = matchService.voteOnMatchRequest("voter-1", "req-1", voteDto);

        assertEquals(MatchStatus.REJECTED, response.getStatus());
        assertEquals(0, response.getApproveVotes());
        assertEquals(1, response.getRejectVotes());
        assertEquals(3, response.getTotalHouseMembers());
        verify(matchRequestRepository).transitionStatusFromPending("req-1", MatchStatus.REJECTED);
        verify(matchRequestRepository, never()).save(any(MatchRequest.class));
        verifyNoInteractions(residenceHistoryService);
    }

    @Test
    void voteOnMatchRequestWithMajorityShouldApproveWithTwoOfThreeVotes() {
        MatchRequest req = new MatchRequest();
        req.setId("req-1");
        req.setUserId("candidate-1");
        req.setHouseId("house-1");
        req.setStatus(MatchStatus.PENDING);

        User voter2 = new User();
        voter2.setId("voter-2");
        voter2.setHouseId("house-1");

        User candidate = new User();
        candidate.setId("candidate-1");

        House house = new House();
        house.setId("house-1");
        house.setVotingRule(VotingRule.MAJORITY);

        MatchVote v1 = new MatchVote();
        v1.setVoterId("voter-1");
        v1.setVote(VoteType.APPROVE);
        MatchVote v2 = new MatchVote();
        v2.setVoterId("voter-2");
        v2.setVote(VoteType.APPROVE);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-2")).thenReturn(Optional.of(voter2));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1"))
                .thenReturn(List.of(member("voter-1"), voter2, member("voter-3")));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-2")).thenReturn(Optional.empty());
        when(matchVoteRepository.findByRequestId("req-1")).thenReturn(List.of(v1, v2));
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(matchRequestRepository.findByUserIdAndStatus("candidate-1", MatchStatus.PENDING))
                .thenReturn(List.of());
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(1);
        when(matchRequestRepository.transitionStatusFromPending("req-1", MatchStatus.ACCEPTED)).thenReturn(1);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APPROVE");

        VoteMatchResponse response = matchService.voteOnMatchRequest("voter-2", "req-1", voteDto);

        assertEquals(MatchStatus.ACCEPTED, response.getStatus());
        assertEquals("house-1", candidate.getHouseId());
        assertFalse(response.isHasOtherPendingRequests());
        assertTrue(response.getOtherPendingRequestIds().isEmpty());
        verify(residenceHistoryService).recordEntry("candidate-1", "house-1");
    }

    @Test
    void cancelOtherPendingRequestsShouldCancelAllPendingRequestsOfUser() {
        User user = new User();
        user.setId("candidate-1");

        MatchRequest r1 = new MatchRequest();
        r1.setId("req-1");
        r1.setUserId("candidate-1");
        r1.setStatus(MatchStatus.PENDING);

        MatchRequest r2 = new MatchRequest();
        r2.setId("req-2");
        r2.setUserId("candidate-1");
        r2.setStatus(MatchStatus.PENDING);

        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(user));
        when(matchRequestRepository.findByUserIdAndStatus("candidate-1", MatchStatus.PENDING))
                .thenReturn(List.of(r1, r2));

        Map<String, Object> result = matchService.cancelOtherPendingRequests("candidate-1");

        assertEquals(2, result.get("cancelledCount"));
        assertEquals(MatchStatus.CANCELLED, r1.getStatus());
        assertEquals(MatchStatus.CANCELLED, r2.getStatus());
        verify(matchRequestRepository).saveAll(List.of(r1, r2));
    }

    @Test
    void cancelOtherPendingRequestsShouldAlsoClearPendingWelcome() {
        User user = new User();
        user.setId("candidate-1");
        user.setHouseId("house-1");
        user.setPendingWelcome(true);

        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(user));
        when(matchRequestRepository.findByUserIdAndStatus("candidate-1", MatchStatus.PENDING))
                .thenReturn(List.of());

        matchService.cancelOtherPendingRequests("candidate-1");

        assertFalse(user.getPendingWelcome());
        verify(userRepository).save(user);
    }

    @Test
    void createMatchRequestShouldAllowResidentToRequestAnotherHouse() {
        User resident = named("resident-1", "Morador", "house-A");
        House house = openHouse("house-1", 1, VotingRule.UNANIMITY);

        when(userRepository.findById("resident-1")).thenReturn(Optional.of(resident));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(matchRequestRepository.existsByUserIdAndHouseIdAndStatusIn("resident-1", "house-1", OPEN))
                .thenReturn(false);
        when(matchRequestRepository.save(any(MatchRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMatchRequestDto dto = new CreateMatchRequestDto();
        dto.setHouseId("house-1");

        MatchRequestResponse response = matchService.createMatchRequest("resident-1", dto);

        assertEquals(MatchStatus.PENDING, response.getStatus());
        assertEquals("house-1", response.getHouseId());
        verify(matchRequestRepository).save(any(MatchRequest.class));
    }

    @Test
    void createMatchRequestShouldRejectResidentAskingForTheirOwnHouse() {
        User resident = named("resident-1", "Morador", "house-1");
        House house = openHouse("house-1", 1, VotingRule.UNANIMITY);

        when(userRepository.findById("resident-1")).thenReturn(Optional.of(resident));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));

        CreateMatchRequestDto dto = new CreateMatchRequestDto();
        dto.setHouseId("house-1");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> matchService.createMatchRequest("resident-1", dto));
        assertEquals("user already belongs to this house", ex.getMessage());
        verify(matchRequestRepository, never()).save(any());
    }

    private void stubSingleApproval(MatchRequest req, User voter, House house, User candidate) {
        when(matchRequestRepository.findById(req.getId())).thenReturn(Optional.of(req));
        when(userRepository.findById(voter.getId())).thenReturn(Optional.of(voter));
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc(house.getId())).thenReturn(List.of(voter));
        when(matchVoteRepository.findByRequestIdAndVoterId(req.getId(), voter.getId())).thenReturn(Optional.empty());
        when(matchVoteRepository.findByRequestId(req.getId()))
                .thenReturn(List.of(matchVote(req.getId(), voter.getId(), VoteType.APPROVE)));
        when(userRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
    }

    private VoteRequestDto approveDto() {
        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APPROVE");
        return voteDto;
    }

    @Test
    void approvalOfResidentShouldReserveVacancyAndAwaitMoveFor72Hours() {
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User voter = member("voter-1");
        User candidate = named("candidate-1", "Candidato", "house-A");
        candidate.setIsHouseAdmin(true);
        House house = openHouse("house-1", 1, VotingRule.UNANIMITY);
        stubSingleApproval(req, voter, house, candidate);
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(1);
        when(matchRequestRepository.transitionPendingToAwaitingMove("req-1", FIXED_NOW_UTC.plusHours(72)))
                .thenReturn(1);

        VoteMatchResponse response = matchService.voteOnMatchRequest("voter-1", "req-1", approveDto());

        assertEquals(MatchStatus.AWAITING_MOVE, response.getStatus());
        assertEquals(MatchStatus.AWAITING_MOVE, req.getStatus());
        assertEquals(FIXED_NOW_UTC.plusHours(72), req.getMoveDeadline());
        // Nothing moves until the resident confirms.
        assertEquals("house-A", candidate.getHouseId());
        assertTrue(candidate.getIsHouseAdmin());
        assertFalse(candidate.getPendingWelcome());
        verify(houseRepository).decrementVacancyIfAvailable("house-1");
        verify(matchRequestRepository).transitionPendingToAwaitingMove("req-1", FIXED_NOW_UTC.plusHours(72));
        verify(matchRequestRepository, never()).transitionStatusFromPending(any(), any());
        verify(userRepository, never()).save(any());
        verify(houseRepository, never()).save(any());
        verifyNoInteractions(residenceHistoryService);
    }

    @Test
    void approvalOfResidentShouldThrowConflictWhenHouseHasNoVacancyLeft() {
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User voter = member("voter-1");
        User candidate = named("candidate-1", "Candidato", "house-A");
        House house = openHouse("house-1", 0, VotingRule.UNANIMITY);
        stubSingleApproval(req, voter, house, candidate);
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(0);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> matchService.voteOnMatchRequest("voter-1", "req-1", approveDto()));
        assertEquals("house has no available vacancies", ex.getMessage());
        verify(matchRequestRepository, never()).transitionPendingToAwaitingMove(any(), any());
    }

    @Test
    void awaitingMoveBranchShouldThrowConflictWhenRequestStoppedBeingPendingConcurrently() {
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User voter = member("voter-1");
        User candidate = named("candidate-1", "Candidato", "house-A");
        House house = openHouse("house-1", 1, VotingRule.UNANIMITY);
        stubSingleApproval(req, voter, house, candidate);
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(1);
        // A concurrent vote/withdrawal already closed the request: the rollback gives the vacancy back.
        when(matchRequestRepository.transitionPendingToAwaitingMove(eq("req-1"), any())).thenReturn(0);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> matchService.voteOnMatchRequest("voter-1", "req-1", approveDto()));
        assertEquals("request is no longer pending", ex.getMessage());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(residenceHistoryService);
    }

    @Test
    void awaitingMoveBranchShouldThrowConflictWhenCandidateAlreadyMovedIntoThisHouseAfterReload() {
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User voter = member("voter-1");
        User candidate = named("candidate-1", "Candidato", "house-A");
        House house = openHouse("house-1", 1, VotingRule.UNANIMITY);
        stubSingleApproval(req, voter, house, candidate);
        when(userRepository.findById("candidate-1"))
                .thenReturn(Optional.of(candidate), Optional.of(named("candidate-1", "Candidato", "house-1")));
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(1);
        when(matchRequestRepository.transitionPendingToAwaitingMove(eq("req-1"), any())).thenReturn(1);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> matchService.voteOnMatchRequest("voter-1", "req-1", approveDto()));
        assertEquals("candidate already belongs to this house", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectBranchShouldThrowConflictWhenRequestStoppedBeingPendingConcurrently() {
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User voter = member("voter-1");
        House house = openHouse("house-1", 1, VotingRule.UNANIMITY);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-1")).thenReturn(Optional.of(voter));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(voter, member("voter-2")));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-1")).thenReturn(Optional.empty());
        when(matchVoteRepository.findByRequestId("req-1"))
                .thenReturn(List.of(matchVote("req-1", "voter-1", VoteType.REJECT)));
        // A concurrent ACCEPTED/WITHDRAWN already closed the request.
        when(matchRequestRepository.transitionStatusFromPending("req-1", MatchStatus.REJECTED)).thenReturn(0);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("REJECT");

        ConflictException ex = assertThrows(ConflictException.class,
                () -> matchService.voteOnMatchRequest("voter-1", "req-1", voteDto));
        assertEquals("request is no longer pending", ex.getMessage());
        verify(matchRequestRepository).transitionStatusFromPending("req-1", MatchStatus.REJECTED);
        verify(matchRequestRepository, never()).save(any(MatchRequest.class));
        verify(houseRepository, never()).decrementVacancyIfAvailable(any());
    }

    @Test
    void approvalShouldThrowConflictWhenCandidateJoinedAnotherHouseAfterReload() {
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User voter = member("voter-1");
        User candidateBefore = named("candidate-1", "Candidato", null);
        // Reloaded after the bulk updates: another house accepted them concurrently (READ COMMITTED).
        User candidateAfterReload = named("candidate-1", "Candidato", "house-other");
        House house = openHouse("house-1", 1, VotingRule.UNANIMITY);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-1")).thenReturn(Optional.of(voter));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(voter));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-1")).thenReturn(Optional.empty());
        when(matchVoteRepository.findByRequestId("req-1"))
                .thenReturn(List.of(matchVote("req-1", "voter-1", VoteType.APPROVE)));
        when(userRepository.findById("candidate-1"))
                .thenReturn(Optional.of(candidateBefore), Optional.of(candidateAfterReload));
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(1);
        when(matchRequestRepository.transitionStatusFromPending("req-1", MatchStatus.ACCEPTED)).thenReturn(1);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APPROVE");

        ConflictException ex = assertThrows(ConflictException.class,
                () -> matchService.voteOnMatchRequest("voter-1", "req-1", voteDto));
        assertEquals("candidate already belongs to a house", ex.getMessage());
        // The exception rolls back the vacancy and the ACCEPTED transition; the candidate is untouched.
        assertEquals("house-other", candidateAfterReload.getHouseId());
        assertFalse(candidateAfterReload.getPendingWelcome());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(residenceHistoryService);
    }

    @Test
    void voteShouldIgnoreVotesFromFormerResidents() {
        MatchRequest req = new MatchRequest();
        req.setId("req-1");
        req.setUserId("candidate-1");
        req.setHouseId("house-1");
        req.setStatus(MatchStatus.PENDING);

        User voter1 = member("voter-1");
        User voter2 = member("voter-2");

        House house = new House();
        house.setId("house-1");
        house.setVotingRule(VotingRule.MAJORITY);

        MatchVote currentVote = new MatchVote();
        currentVote.setVoterId("voter-1");
        currentVote.setVote(VoteType.APPROVE);

        MatchVote formerResidentVote = new MatchVote();
        formerResidentVote.setVoterId("former-1");
        formerResidentVote.setVote(VoteType.APPROVE);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-1")).thenReturn(Optional.of(voter1));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(voter1, voter2));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-1")).thenReturn(Optional.empty());
        when(matchVoteRepository.findByRequestId("req-1")).thenReturn(List.of(currentVote, formerResidentVote));

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APROVAR");

        VoteMatchResponse response = matchService.voteOnMatchRequest("voter-1", "req-1", voteDto);

        // 1 of 2 current residents approved: not a majority yet, the former resident's vote is ignored.
        assertEquals(MatchStatus.PENDING, response.getStatus());
        assertEquals(1, response.getApproveVotes());
        assertEquals(2, response.getTotalHouseMembers());
    }

    // ---------- B5: vagas, aceite atômico, minhas solicitações, desistência, card, voto trocável ----------

    private House openHouse(String id, int vacancies, VotingRule rule) {
        House house = new House();
        house.setId(id);
        house.setName("Casa " + id);
        house.setIsAcceptingMatches(true);
        house.setVacancies(vacancies);
        house.setVotingRule(rule);
        return house;
    }

    private MatchRequest pendingRequest(String id, String userId, String houseId) {
        MatchRequest req = new MatchRequest();
        req.setId(id);
        req.setUserId(userId);
        req.setHouseId(houseId);
        req.setStatus(MatchStatus.PENDING);
        req.setCreatedAt(LocalDateTime.now());
        return req;
    }

    private MatchVote matchVote(String requestId, String voterId, VoteType type) {
        MatchVote v = new MatchVote();
        v.setId("v-" + voterId);
        v.setRequestId(requestId);
        v.setVoterId(voterId);
        v.setVote(type);
        return v;
    }

    private User named(String id, String name, String houseId) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setHouseId(houseId);
        return user;
    }

    @Test
    void newEntitiesShouldDefaultToOneVacancyAndNoPendingWelcome() {
        assertEquals(1, new House().getVacancies());
        assertFalse(new User().getPendingWelcome());
    }

    @Test
    void createMatchRequestShouldRefuseHouseWithoutVacancies() {
        User candidate = named("candidate-1", "Carlos", null);
        House house = openHouse("house-1", 0, VotingRule.UNANIMITY);

        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));

        CreateMatchRequestDto dto = new CreateMatchRequestDto();
        dto.setHouseId("house-1");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> matchService.createMatchRequest("candidate-1", dto));
        assertEquals("house is not accepting requests", ex.getMessage());
        verify(matchRequestRepository, never()).save(any());
    }

    @Test
    void createMatchRequestShouldRefusePausedHouseEvenWithVacancies() {
        User candidate = named("candidate-1", "Carlos", null);
        House house = openHouse("house-1", 3, VotingRule.UNANIMITY);
        house.setIsAcceptingMatches(false);

        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));

        CreateMatchRequestDto dto = new CreateMatchRequestDto();
        dto.setHouseId("house-1");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> matchService.createMatchRequest("candidate-1", dto));
        assertEquals("house is not accepting requests", ex.getMessage());
    }

    @Test
    void approvalShouldThrowConflictAndNotAcceptCandidateWhenNoVacancyLeft() {
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User voter = member("voter-1");
        User candidate = named("candidate-1", "Candidato", null);
        House house = openHouse("house-1", 0, VotingRule.UNANIMITY);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-1")).thenReturn(Optional.of(voter));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(voter));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-1")).thenReturn(Optional.empty());
        when(matchVoteRepository.findByRequestId("req-1"))
                .thenReturn(List.of(matchVote("req-1", "voter-1", VoteType.APPROVE)));
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(0);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APPROVE");

        ConflictException ex = assertThrows(ConflictException.class,
                () -> matchService.voteOnMatchRequest("voter-1", "req-1", voteDto));
        assertEquals("house has no available vacancies", ex.getMessage());

        assertNull(candidate.getHouseId());
        assertFalse(candidate.getPendingWelcome());
        verify(matchRequestRepository, never()).transitionStatusFromPending(any(), any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(residenceHistoryService);
    }

    @Test
    void approvalShouldThrowConflictWhenRequestWasClosedConcurrently() {
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User voter = member("voter-1");
        User candidate = named("candidate-1", "Candidato", null);
        House house = openHouse("house-1", 1, VotingRule.UNANIMITY);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-1")).thenReturn(Optional.of(voter));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(voter));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-1")).thenReturn(Optional.empty());
        when(matchVoteRepository.findByRequestId("req-1"))
                .thenReturn(List.of(matchVote("req-1", "voter-1", VoteType.APPROVE)));
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(1);
        when(matchRequestRepository.transitionStatusFromPending("req-1", MatchStatus.ACCEPTED)).thenReturn(0);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APPROVE");

        ConflictException ex = assertThrows(ConflictException.class,
                () -> matchService.voteOnMatchRequest("voter-1", "req-1", voteDto));
        assertEquals("request is no longer pending", ex.getMessage());
        assertNull(candidate.getHouseId());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(residenceHistoryService);
    }

    @Test
    void reserveVacancyOrThrowShouldPassOnlyWhenARowWasDecremented() {
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(1);
        assertDoesNotThrow(() -> matchService.reserveVacancyOrThrow("house-1"));

        when(houseRepository.decrementVacancyIfAvailable("house-2")).thenReturn(0);
        ConflictException ex = assertThrows(ConflictException.class,
                () -> matchService.reserveVacancyOrThrow("house-2"));
        assertEquals("house has no available vacancies", ex.getMessage());
    }

    @Test
    void voteSwapFromRejectToApproveShouldUpsertAndReevaluateUnderMajority() {
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User voter1 = member("voter-1");
        User candidate = named("candidate-1", "Candidato", null);
        House house = openHouse("house-1", 2, VotingRule.MAJORITY);

        // voter-1 had rejected earlier; voter-2 approved. 3 residents.
        MatchVote previous = matchVote("req-1", "voter-1", VoteType.REJECT);
        MatchVote other = matchVote("req-1", "voter-2", VoteType.APPROVE);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-1")).thenReturn(Optional.of(voter1));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1"))
                .thenReturn(List.of(voter1, member("voter-2"), member("voter-3")));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-1")).thenReturn(Optional.of(previous));
        when(matchVoteRepository.findByRequestId("req-1")).thenReturn(List.of(previous, other));
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(houseRepository.decrementVacancyIfAvailable("house-1")).thenReturn(1);
        when(matchRequestRepository.transitionStatusFromPending("req-1", MatchStatus.ACCEPTED)).thenReturn(1);
        when(matchRequestRepository.findByUserIdAndStatus("candidate-1", MatchStatus.PENDING)).thenReturn(List.of());

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APPROVE");

        VoteMatchResponse response = matchService.voteOnMatchRequest("voter-1", "req-1", voteDto);

        ArgumentCaptor<MatchVote> saved = ArgumentCaptor.forClass(MatchVote.class);
        verify(matchVoteRepository).save(saved.capture());
        assertSame(previous, saved.getValue(), "the existing vote must be updated, not duplicated");
        assertEquals(VoteType.APPROVE, previous.getVote());
        assertEquals(MatchStatus.ACCEPTED, response.getStatus());
        assertEquals(2, response.getApproveVotes());
        assertEquals(0, response.getRejectVotes());
        assertTrue(candidate.getPendingWelcome());
    }

    @Test
    void voteSwapFromApproveToRejectShouldReevaluateUnderUnanimity() {
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User voter1 = member("voter-1");
        House house = openHouse("house-1", 1, VotingRule.UNANIMITY);

        MatchVote previous = matchVote("req-1", "voter-1", VoteType.APPROVE);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-1")).thenReturn(Optional.of(voter1));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(voter1, member("voter-2")));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-1")).thenReturn(Optional.of(previous));
        when(matchVoteRepository.findByRequestId("req-1")).thenReturn(List.of(previous));
        when(matchRequestRepository.transitionStatusFromPending("req-1", MatchStatus.REJECTED)).thenReturn(1);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("REJECT");

        VoteMatchResponse response = matchService.voteOnMatchRequest("voter-1", "req-1", voteDto);

        verify(matchVoteRepository).save(previous);
        assertEquals(VoteType.REJECT, previous.getVote());
        assertEquals(MatchStatus.REJECTED, response.getStatus());
        assertEquals(0, response.getApproveVotes());
        assertEquals(1, response.getRejectVotes());
        verify(houseRepository, never()).decrementVacancyIfAvailable(any());
    }

    @Test
    void listMyMatchRequestsShouldReturnAllStatusesWithVotesFromCurrentResidentsOnly() {
        User me = named("candidate-1", "Eu", "house-2");
        MatchRequest newest = pendingRequest("req-new", "candidate-1", "house-1");
        MatchRequest older = pendingRequest("req-old", "candidate-1", "house-2");
        older.setStatus(MatchStatus.ACCEPTED);
        older.setMoveDeadline(FIXED_NOW_UTC.plusHours(1));

        House house1 = openHouse("house-1", 1, VotingRule.UNANIMITY);
        House house2 = openHouse("house-2", 0, VotingRule.UNANIMITY);

        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(me));
        when(matchRequestRepository.findByUserIdOrderByCreatedAtDesc("candidate-1"))
                .thenReturn(List.of(newest, older));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house1));
        when(houseRepository.findById("house-2")).thenReturn(Optional.of(house2));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1"))
                .thenReturn(List.of(member("m-1"), member("m-2"), member("m-3")));
        // The accepted candidate now lives in house-2 and must not count as one of its voters.
        when(userRepository.findByHouseIdOrderByIdAsc("house-2"))
                .thenReturn(List.of(named("m-9", "Morador", "house-2"), me));
        when(matchVoteRepository.findByRequestId("req-new")).thenReturn(List.of(
                matchVote("req-new", "m-1", VoteType.APPROVE),
                matchVote("req-new", "former-1", VoteType.APPROVE)));
        when(matchVoteRepository.findByRequestId("req-old")).thenReturn(List.of(
                matchVote("req-old", "m-9", VoteType.APPROVE)));

        List<MyMatchRequestDto> mine = matchService.listMyMatchRequests("candidate-1");

        assertEquals(2, mine.size());
        MyMatchRequestDto first = mine.get(0);
        assertEquals("req-new", first.id());
        assertEquals("house-1", first.houseId());
        assertEquals("Casa house-1", first.houseName());
        assertEquals(MatchStatus.PENDING, first.status());
        assertNotNull(first.createdAt());
        assertEquals(1, first.votesCast());
        assertEquals(3, first.votersTotal());
        assertNull(first.moveDeadline());

        MyMatchRequestDto second = mine.get(1);
        assertEquals("req-old", second.id());
        assertEquals(MatchStatus.ACCEPTED, second.status());
        assertEquals(FIXED_NOW_UTC.plusHours(1), second.moveDeadline());
        assertEquals(1, second.votesCast());
        assertEquals(1, second.votersTotal());
    }

    @Test
    void withdrawShouldReturn404WhenRequestDoesNotExist() {
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(named("candidate-1", "Eu", null)));
        when(matchRequestRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> matchService.withdrawMatchRequest("candidate-1", "missing"));
    }

    @Test
    void withdrawShouldReturn403WhenCallerIsNotTheOwner() {
        when(userRepository.findById("intruder")).thenReturn(Optional.of(named("intruder", "X", null)));
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        req.setStatus(MatchStatus.REJECTED);
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> matchService.withdrawMatchRequest("intruder", "req-1"));
        assertEquals("only the owner can perform this action", ex.getMessage());
        verify(matchRequestRepository, never()).transitionStatusFromPending(any(), any());
    }

    @Test
    void withdrawShouldReturn400WhenRequestIsNotPending() {
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(named("candidate-1", "Eu", null)));
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        req.setStatus(MatchStatus.ACCEPTED);
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> matchService.withdrawMatchRequest("candidate-1", "req-1"));
        assertEquals("request is not pending", ex.getMessage());
        verify(matchRequestRepository, never()).transitionStatusFromPending(any(), any());
    }

    @Test
    void withdrawShouldMarkPendingRequestAsWithdrawnWithoutTouchingVacancies() {
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(named("candidate-1", "Eu", null)));
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(matchRequestRepository.transitionStatusFromPending("req-1", MatchStatus.WITHDRAWN)).thenReturn(1);

        matchService.withdrawMatchRequest("candidate-1", "req-1");

        verify(matchRequestRepository).transitionStatusFromPending("req-1", MatchStatus.WITHDRAWN);
        verify(houseRepository, never()).decrementVacancyIfAvailable(any());
        verify(houseRepository, never()).save(any());
    }

    @Test
    void withdrawShouldReturn400WhenRequestStoppedBeingPendingConcurrently() {
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(named("candidate-1", "Eu", null)));
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(matchRequestRepository.transitionStatusFromPending("req-1", MatchStatus.WITHDRAWN)).thenReturn(0);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> matchService.withdrawMatchRequest("candidate-1", "req-1"));
        assertEquals("request is not pending", ex.getMessage());
    }

    @Test
    void listHouseMatchRequestsShouldExposeCandidateCardVotersWithoutDirectionAndMyVote() {
        User caller = named("member-1", "Ana Moradora", "house-1");
        User other = named("member-2", "Bruno Morador", "house-1");
        House house = openHouse("house-1", 2, VotingRule.UNANIMITY);
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");
        User candidate = named("candidate-1", "Carla", null);
        candidate.setEmail("carla@test.com");

        when(userRepository.findById("member-1")).thenReturn(Optional.of(caller));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(matchRequestRepository.findByHouseIdAndStatusInOrderByCreatedAtAsc("house-1", OPEN))
                .thenReturn(List.of(req));
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(userProfileRepository.findById("candidate-1")).thenReturn(Optional.empty());
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(caller, other));
        when(matchVoteRepository.findByRequestId("req-1")).thenReturn(List.of(
                matchVote("req-1", "member-2", VoteType.REJECT),
                matchVote("req-1", "member-1", VoteType.APPROVE),
                matchVote("req-1", "former-1", VoteType.APPROVE)));
        when(pointService.getUserPointsSummary("candidate-1")).thenReturn(new PointsSummaryDto(42, 7));
        when(residenceHistoryService.calculateTotalResidenceMonths("candidate-1")).thenReturn(5);

        List<MatchRequestResponse> list = matchService.listHouseMatchRequests("member-1", "house-1");

        assertEquals(1, list.size());
        MatchRequestResponse item = list.get(0);
        assertEquals("Carla", item.getUserName());
        assertEquals("carla@test.com", item.getUserEmail());
        assertNull(item.getCompatibilityScore());

        CandidateCardDto card = item.getCandidate();
        assertEquals("candidate-1", card.id());
        assertEquals("Carla", card.name());
        assertNull(card.compatibilityScore(), "incomplete profile -> null compatibility");
        assertEquals(42, card.points());
        assertEquals(5, card.residenceMonths());

        // Only current residents, in resident order, and no vote direction.
        assertEquals(List.of(new VoterSummaryDto("member-1", "Ana Moradora"),
                new VoterSummaryDto("member-2", "Bruno Morador")), item.getVoters());
        assertEquals("APPROVE", item.getMyVote());
    }

    @Test
    void listHouseMatchRequestsShouldReturnNullMyVoteWhenCallerHasNotVoted() {
        User caller = named("member-1", "Ana", "house-1");
        House house = openHouse("house-1", 1, VotingRule.UNANIMITY);
        MatchRequest req = pendingRequest("req-1", "candidate-1", "house-1");

        when(userRepository.findById("member-1")).thenReturn(Optional.of(caller));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(matchRequestRepository.findByHouseIdAndStatusInOrderByCreatedAtAsc("house-1", OPEN))
                .thenReturn(List.of(req));
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(named("candidate-1", "C", null)));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(caller));
        when(matchVoteRepository.findByRequestId("req-1")).thenReturn(List.of());
        when(pointService.getUserPointsSummary("candidate-1")).thenReturn(new PointsSummaryDto(0, 0));
        when(residenceHistoryService.calculateTotalResidenceMonths("candidate-1")).thenReturn(null);

        MatchRequestResponse item = matchService.listHouseMatchRequests("member-1", "house-1").get(0);

        assertNull(item.getMyVote());
        assertTrue(item.getVoters().isEmpty());
        assertEquals(0, item.getCandidate().points());
        assertNull(item.getCandidate().residenceMonths());
    }

    // ---------- B6: expiração, confirmar, recusar e retirar ----------

    private MatchRequest awaitingMove(String id, String userId, String houseId) {
        MatchRequest req = pendingRequest(id, userId, houseId);
        req.setStatus(MatchStatus.AWAITING_MOVE);
        req.setMoveDeadline(FIXED_NOW_UTC.plusHours(10));
        return req;
    }

    @Test
    void expireOverdueMovesShouldGiveTheVacancyBackOnlyWhenTheRowWasExpiredHere() {
        MatchRequest mine = awaitingMove("req-1", "c-1", "house-1");
        MatchRequest racedAway = awaitingMove("req-2", "c-2", "house-2");
        when(matchRequestRepository.findByStatusAndMoveDeadlineLessThanEqual(MatchStatus.AWAITING_MOVE, FIXED_NOW_UTC))
                .thenReturn(List.of(mine, racedAway));
        when(matchRequestRepository.expireAwaitingMove("req-1", FIXED_NOW_UTC)).thenReturn(1);
        // Another transaction (job vs read) expired it first: no second vacancy.
        when(matchRequestRepository.expireAwaitingMove("req-2", FIXED_NOW_UTC)).thenReturn(0);

        matchService.expireOverdueMoves();

        verify(houseRepository).incrementVacancy("house-1");
        verify(houseRepository, never()).incrementVacancy("house-2");
    }

    @Test
    void readsShouldExpireOverdueMovesFirst() {
        when(userRepository.findById("c-1")).thenReturn(Optional.of(named("c-1", "Eu", null)));
        when(matchRequestRepository.findByUserIdOrderByCreatedAtDesc("c-1")).thenReturn(List.of());

        matchService.listMyMatchRequests("c-1");

        InOrder order = inOrder(matchRequestRepository);
        order.verify(matchRequestRepository)
                .findByStatusAndMoveDeadlineLessThanEqual(MatchStatus.AWAITING_MOVE, FIXED_NOW_UTC);
        order.verify(matchRequestRepository).findByUserIdOrderByCreatedAtDesc("c-1");
    }

    @Test
    void confirmMoveShouldLeaveOldHouseJoinNewOneAndCancelOtherReservations() {
        MatchRequest req = awaitingMove("req-1", "mover", "house-new");
        MatchRequest other = awaitingMove("req-2", "mover", "house-other");
        User moverBefore = named("mover", "Mover", "house-old");
        moverBefore.setIsHouseAdmin(true);
        User moverAfterLeave = named("mover", "Mover", null);
        UserMeResponse me = mock(UserMeResponse.class);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("mover")).thenReturn(Optional.of(moverBefore), Optional.of(moverAfterLeave));
        when(userRepository.findByHouseIdOrderByIdAsc("house-new")).thenReturn(List.of(member("t-admin")));
        when(matchRequestRepository.transitionAwaitingMoveTo("req-1", MatchStatus.ACCEPTED)).thenReturn(1);
        when(matchRequestRepository.findByUserIdAndStatus("mover", MatchStatus.AWAITING_MOVE))
                .thenReturn(List.of(req, other));
        when(matchRequestRepository.transitionAwaitingMoveTo("req-2", MatchStatus.CANCELLED)).thenReturn(1);
        when(userService.getCurrentUser("mover")).thenReturn(me);

        UserMeResponse result = matchService.confirmMove("mover", "req-1", new ConfirmMoveRequest("heir"));

        assertSame(me, result);
        assertEquals("house-new", moverAfterLeave.getHouseId());
        assertFalse(moverAfterLeave.getIsHouseAdmin());
        assertFalse(moverAfterLeave.getPendingWelcome());

        InOrder order = inOrder(houseMembershipService, matchRequestRepository, userRepository,
                residenceHistoryService, houseRepository);
        order.verify(houseMembershipService).leaveHouse("mover", "house-old", "heir");
        order.verify(matchRequestRepository).transitionAwaitingMoveTo("req-1", MatchStatus.ACCEPTED);
        order.verify(userRepository).save(moverAfterLeave);
        order.verify(residenceHistoryService).recordEntry("mover", "house-new");
        order.verify(matchRequestRepository).transitionAwaitingMoveTo("req-2", MatchStatus.CANCELLED);
        order.verify(houseRepository).incrementVacancy("house-other");
        verify(houseRepository, never()).incrementVacancy("house-new");
        verify(matchRequestRepository, never()).transitionAwaitingMoveTo("req-1", MatchStatus.CANCELLED);
    }

    @Test
    void confirmMoveShouldPropagateMissingHeirWithoutWritingAnything() {
        MatchRequest req = awaitingMove("req-1", "mover", "house-new");
        User mover = named("mover", "Mover", "house-old");
        mover.setIsHouseAdmin(true);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("mover")).thenReturn(Optional.of(mover));
        when(userRepository.findByHouseIdOrderByIdAsc("house-new")).thenReturn(List.of(member("t-admin")));
        doThrow(new BusinessRuleException("new admin required"))
                .when(houseMembershipService).leaveHouse("mover", "house-old", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> matchService.confirmMove("mover", "req-1", null));
        assertEquals("new admin required", ex.getMessage());
        verify(matchRequestRepository, never()).transitionAwaitingMoveTo(any(), any());
        verify(userRepository, never()).save(any());
        verify(houseRepository, never()).incrementVacancy(any());
        verifyNoInteractions(residenceHistoryService);
    }

    @Test
    void confirmMoveShouldRefuseNonOwnerAndRequestsNotAwaitingMove() {
        MatchRequest req = awaitingMove("req-1", "mover", "house-new");
        MatchRequest pending = pendingRequest("req-2", "mover", "house-new");
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(matchRequestRepository.findById("req-2")).thenReturn(Optional.of(pending));
        when(matchRequestRepository.findById("missing")).thenReturn(Optional.empty());

        ForbiddenOperationException forbidden = assertThrows(ForbiddenOperationException.class,
                () -> matchService.confirmMove("intruder", "req-1", null));
        assertEquals("only the owner can perform this action", forbidden.getMessage());
        ConflictException conflict = assertThrows(ConflictException.class,
                () -> matchService.confirmMove("mover", "req-2", null));
        assertEquals("request is not awaiting move", conflict.getMessage());
        assertThrows(ResourceNotFoundException.class, () -> matchService.confirmMove("mover", "missing", null));
        verifyNoInteractions(houseMembershipService);
    }

    @Test
    void confirmMoveShouldRefuseWhenTargetHouseHasNoResidentsLeft() {
        MatchRequest req = awaitingMove("req-1", "mover", "house-new");
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("mover")).thenReturn(Optional.of(named("mover", "Mover", "house-old")));
        when(userRepository.findByHouseIdOrderByIdAsc("house-new")).thenReturn(List.of());

        ConflictException ex = assertThrows(ConflictException.class,
                () -> matchService.confirmMove("mover", "req-1", null));
        assertEquals("house is no longer active", ex.getMessage());
        verifyNoInteractions(houseMembershipService);
    }

    @Test
    void confirmMoveShouldThrowConflictWhenReservationWasClosedConcurrently() {
        MatchRequest req = awaitingMove("req-1", "mover", "house-new");
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("mover")).thenReturn(Optional.of(named("mover", "Mover", null)));
        when(userRepository.findByHouseIdOrderByIdAsc("house-new")).thenReturn(List.of(member("t-admin")));
        when(matchRequestRepository.transitionAwaitingMoveTo("req-1", MatchStatus.ACCEPTED)).thenReturn(0);

        assertThrows(ConflictException.class, () -> matchService.confirmMove("mover", "req-1", null));
        verifyNoInteractions(houseMembershipService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void declineMoveShouldWithdrawAndGiveTheVacancyBack() {
        MatchRequest req = awaitingMove("req-1", "mover", "house-new");
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(matchRequestRepository.transitionAwaitingMoveTo("req-1", MatchStatus.WITHDRAWN)).thenReturn(1);

        matchService.declineMove("mover", "req-1");

        verify(houseRepository).incrementVacancy("house-new");
    }

    @Test
    void declineMoveShouldRefuseNonOwnerAndNotGiveVacancyBackOnLostRace() {
        MatchRequest req = awaitingMove("req-1", "mover", "house-new");
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));

        assertThrows(ForbiddenOperationException.class, () -> matchService.declineMove("intruder", "req-1"));

        when(matchRequestRepository.transitionAwaitingMoveTo("req-1", MatchStatus.WITHDRAWN)).thenReturn(0);
        assertThrows(ConflictException.class, () -> matchService.declineMove("mover", "req-1"));
        verify(houseRepository, never()).incrementVacancy(any());
    }

    @Test
    void retractShouldCancelAndGiveTheVacancyBackForTargetAdmin() {
        MatchRequest req = awaitingMove("req-1", "mover", "house-1");
        User admin = member("admin-1");
        admin.setIsHouseAdmin(true);
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));
        when(matchRequestRepository.transitionAwaitingMoveTo("req-1", MatchStatus.CANCELLED)).thenReturn(1);

        matchService.retractMove("admin-1", "req-1");

        verify(houseRepository).incrementVacancy("house-1");
    }

    @Test
    void retractShouldRefuseNonAdminAndRequestsNotAwaitingMove() {
        MatchRequest req = awaitingMove("req-1", "mover", "house-1");
        MatchRequest pending = pendingRequest("req-2", "mover", "house-1");
        User resident = member("resident-1");
        User admin = member("admin-1");
        admin.setIsHouseAdmin(true);
        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(matchRequestRepository.findById("req-2")).thenReturn(Optional.of(pending));
        when(userRepository.findById("resident-1")).thenReturn(Optional.of(resident));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(admin));

        assertThrows(ForbiddenOperationException.class, () -> matchService.retractMove("resident-1", "req-1"));
        ConflictException ex = assertThrows(ConflictException.class,
                () -> matchService.retractMove("admin-1", "req-2"));
        assertEquals("request is not awaiting move", ex.getMessage());
        verify(matchRequestRepository, never()).transitionAwaitingMoveTo(any(), any());
        verify(houseRepository, never()).incrementVacancy(any());
    }
}
