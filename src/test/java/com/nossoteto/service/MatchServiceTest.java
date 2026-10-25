package com.nossoteto.service;

import com.nossoteto.dto.CreateMatchRequestDto;
import com.nossoteto.dto.MatchRequestResponse;
import com.nossoteto.dto.VoteMatchResponse;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.exception.BusinessRuleException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
                votingEvaluator
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
        when(matchRequestRepository.existsByUserIdAndHouseIdAndStatus("candidate-1", "house-1", MatchStatus.PENDING))
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
        when(matchRequestRepository.existsByUserIdAndHouseIdAndStatus("candidate-1", "house-1", MatchStatus.PENDING))
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
        when(matchRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc("house-1", MatchStatus.PENDING))
                .thenReturn(List.of(req));
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));
        when(userProfileRepository.findById("candidate-1")).thenReturn(Optional.of(profile));

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
        verify(userRepository).save(candidate);
        verify(matchRequestRepository).save(req);
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

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("REJEITAR");

        VoteMatchResponse response = matchService.voteOnMatchRequest("voter-1", "req-1", voteDto);

        assertEquals(MatchStatus.REJECTED, response.getStatus());
        assertEquals(0, response.getApproveVotes());
        assertEquals(1, response.getRejectVotes());
        assertEquals(3, response.getTotalHouseMembers());
        verify(matchRequestRepository).save(req);
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

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APPROVE");

        VoteMatchResponse response = matchService.voteOnMatchRequest("voter-2", "req-1", voteDto);

        assertEquals(MatchStatus.ACCEPTED, response.getStatus());
        assertEquals("house-1", candidate.getHouseId());
        assertFalse(response.isHasOtherPendingRequests());
        assertTrue(response.getOtherPendingRequestIds().isEmpty());
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
    void createMatchRequestShouldRejectUserWhoAlreadyLivesInAnotherHouse() {
        User resident = new User();
        resident.setId("resident-1");
        resident.setHouseId("house-A");

        House house = new House();
        house.setId("house-1");
        house.setIsAcceptingMatches(true);

        when(userRepository.findById("resident-1")).thenReturn(Optional.of(resident));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));

        CreateMatchRequestDto dto = new CreateMatchRequestDto();
        dto.setHouseId("house-1");

        assertThrows(BusinessRuleException.class, () -> matchService.createMatchRequest("resident-1", dto));
        verify(matchRequestRepository, never()).save(any());
    }

    @Test
    void approvalShouldCancelRequestInsteadOfMovingCandidateWhoJoinedAnotherHouse() {
        MatchRequest req = new MatchRequest();
        req.setId("req-1");
        req.setUserId("candidate-1");
        req.setHouseId("house-1");
        req.setStatus(MatchStatus.PENDING);

        User voter = member("voter-1");

        User candidate = new User();
        candidate.setId("candidate-1");
        candidate.setHouseId("house-A");
        candidate.setIsHouseAdmin(true);

        House house = new House();
        house.setId("house-1");
        house.setVotingRule(VotingRule.UNANIMITY);

        MatchVote vote = new MatchVote();
        vote.setVoterId("voter-1");
        vote.setVote(VoteType.APPROVE);

        when(matchRequestRepository.findById("req-1")).thenReturn(Optional.of(req));
        when(userRepository.findById("voter-1")).thenReturn(Optional.of(voter));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(voter));
        when(matchVoteRepository.findByRequestIdAndVoterId("req-1", "voter-1")).thenReturn(Optional.empty());
        when(matchVoteRepository.findByRequestId("req-1")).thenReturn(List.of(vote));
        when(userRepository.findById("candidate-1")).thenReturn(Optional.of(candidate));

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APROVAR");

        VoteMatchResponse response = matchService.voteOnMatchRequest("voter-1", "req-1", voteDto);

        assertEquals(MatchStatus.CANCELLED, response.getStatus());
        assertEquals(MatchStatus.CANCELLED, req.getStatus());
        assertEquals("house-A", candidate.getHouseId());
        assertTrue(candidate.getIsHouseAdmin());
        verify(userRepository, never()).save(any());
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
}
