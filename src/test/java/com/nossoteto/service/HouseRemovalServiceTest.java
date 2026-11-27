package com.nossoteto.service;

import com.nossoteto.dto.CreateRemovalRequestDto;
import com.nossoteto.dto.RemovalRequestResponse;
import com.nossoteto.dto.VoteRequestDto;
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
import com.nossoteto.model.enums.VotingRule;
import com.nossoteto.repository.HouseRemovalRequestRepository;
import com.nossoteto.repository.HouseRemovalVoteRepository;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HouseRemovalServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-27T14:00:00Z");
    private static final LocalDateTime FIXED_NOW_UTC = LocalDateTime.of(2026, 11, 27, 14, 0, 0);

    @Mock
    private HouseRemovalRequestRepository removalRequestRepository;

    @Mock
    private HouseRemovalVoteRepository removalVoteRepository;

    @Mock
    private HouseRepository houseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HouseMembershipService houseMembershipService;

    private HouseRemovalService removalService;

    @BeforeEach
    void setUp() {
        removalService = new HouseRemovalService(
                removalRequestRepository,
                removalVoteRepository,
                houseRepository,
                userRepository,
                new HouseAccessValidator(userRepository),
                new VotingEvaluator(),
                houseMembershipService,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    private User resident(String id, String name, boolean admin) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        u.setHouseId("house-1");
        u.setIsHouseAdmin(admin);
        return u;
    }

    private House house(VotingRule rule) {
        House h = new House();
        h.setId("house-1");
        h.setName("República Teste");
        h.setVotingRule(rule);
        return h;
    }

    private HouseRemovalRequest openRemoval(String id, String targetId, String proposerId) {
        HouseRemovalRequest req = new HouseRemovalRequest();
        req.setId(id);
        req.setHouseId("house-1");
        req.setTargetUserId(targetId);
        req.setProposedById(proposerId);
        req.setReason("Desrespeito às regras");
        req.setStatus(RemovalStatus.OPEN);
        req.setCreatedAt(FIXED_NOW_UTC.minusHours(1));
        return req;
    }

    // ---------- createRemovalRequest ----------

    @Test
    void createRemovalShouldSucceedForAdminAndComputeVotersTotalExcludingTarget() {
        User admin = resident("u1", "Alice", true);
        User target = resident("u2", "Bruno", false);
        User other = resident("u3", "Carla", false);

        when(userRepository.findById("u1")).thenReturn(Optional.of(admin));
        when(userRepository.findById("u2")).thenReturn(Optional.of(target));
        when(removalRequestRepository.existsByHouseIdAndTargetUserIdAndStatus("house-1", "u2", RemovalStatus.OPEN))
                .thenReturn(false);
        when(removalRequestRepository.save(any(HouseRemovalRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin, target, other));

        RemovalRequestResponse resp = removalService.createRemovalRequest(
                "u1", "house-1", new CreateRemovalRequestDto("u2", "  Barulho de madrugada  "));

        assertNotNull(resp.id());
        assertEquals("house-1", resp.houseId());
        assertEquals("u2", resp.targetUserId());
        assertEquals("Bruno", resp.targetUserName());
        assertEquals("u1", resp.proposedById());
        assertEquals("Alice", resp.proposedByName());
        assertEquals("Barulho de madrugada", resp.reason());
        assertEquals(RemovalStatus.OPEN, resp.status());
        assertEquals(FIXED_NOW_UTC, resp.createdAt());
        assertNull(resp.resolvedAt());
        assertTrue(resp.voters().isEmpty());
        assertNull(resp.myVote());
        assertEquals(0, resp.votesCast());
        assertEquals(2, resp.votersTotal());
    }

    @Test
    void createRemovalShouldForbidNonAdmin() {
        when(userRepository.findById("u2")).thenReturn(Optional.of(resident("u2", "Bruno", false)));

        assertThrows(ForbiddenOperationException.class,
                () -> removalService.createRemovalRequest("u2", "house-1", new CreateRemovalRequestDto("u3", null)));
        verify(removalRequestRepository, never()).save(any());
    }

    @Test
    void createRemovalShouldRejectProposingSelfOrBlankMemberId() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(resident("u1", "Alice", true)));

        assertThrows(BusinessRuleException.class,
                () -> removalService.createRemovalRequest("u1", "house-1", new CreateRemovalRequestDto("u1", null)));
        assertThrows(BusinessRuleException.class,
                () -> removalService.createRemovalRequest("u1", "house-1", new CreateRemovalRequestDto(" ", null)));
        assertThrows(BusinessRuleException.class,
                () -> removalService.createRemovalRequest("u1", "house-1", null));
    }

    @Test
    void createRemovalShouldReturnNotFoundWhenTargetIsNotInHouse() {
        User stranger = new User();
        stranger.setId("u9");
        stranger.setHouseId("other-house");
        when(userRepository.findById("u1")).thenReturn(Optional.of(resident("u1", "Alice", true)));
        when(userRepository.findById("u9")).thenReturn(Optional.of(stranger));

        assertThrows(ResourceNotFoundException.class,
                () -> removalService.createRemovalRequest("u1", "house-1", new CreateRemovalRequestDto("u9", null)));
    }

    @Test
    void createRemovalShouldReturn409WhenAlreadyOpenForMember() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(resident("u1", "Alice", true)));
        when(userRepository.findById("u2")).thenReturn(Optional.of(resident("u2", "Bruno", false)));
        when(removalRequestRepository.existsByHouseIdAndTargetUserIdAndStatus("house-1", "u2", RemovalStatus.OPEN))
                .thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> removalService.createRemovalRequest("u1", "house-1", new CreateRemovalRequestDto("u2", null)));
        assertEquals("removal already open for this member", ex.getMessage());
    }

    // ---------- listOpenRemovals ----------

    @Test
    void listOpenRemovalsShouldReturnVotersWithoutDirectionIgnoreExResidentsAndExposeCallerMyVote() {
        User admin = resident("u1", "Alice", true);
        User target = resident("u2", "Bruno", false);
        User resident3 = resident("u3", "Carla", false);
        HouseRemovalRequest req = openRemoval("rem-1", "u2", "u1");

        HouseRemovalVote adminVote = new HouseRemovalVote();
        adminVote.setId("v1");
        adminVote.setRemovalId("rem-1");
        adminVote.setVoterId("u1");
        adminVote.setVote(VoteType.APPROVE);

        HouseRemovalVote exResidentVote = new HouseRemovalVote();
        exResidentVote.setId("v2");
        exResidentVote.setRemovalId("rem-1");
        exResidentVote.setVoterId("ex-resident");
        exResidentVote.setVote(VoteType.REJECT);

        when(userRepository.findById("u3")).thenReturn(Optional.of(resident3));
        when(removalRequestRepository.findByHouseIdAndStatusOrderByCreatedAtAsc("house-1", RemovalStatus.OPEN))
                .thenReturn(List.of(req));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin, target, resident3));
        when(removalVoteRepository.findByRemovalId("rem-1")).thenReturn(List.of(adminVote, exResidentVote));

        List<RemovalRequestResponse> list = removalService.listOpenRemovals("u3", "house-1");

        assertEquals(1, list.size());
        RemovalRequestResponse item = list.get(0);
        assertEquals("rem-1", item.id());
        assertEquals("Bruno", item.targetUserName());
        assertEquals("Alice", item.proposedByName());
        assertEquals(1, item.voters().size());
        assertEquals("u1", item.voters().get(0).id());
        assertEquals("Alice", item.voters().get(0).name());
        assertNull(item.myVote());
        assertEquals(1, item.votesCast());
        assertEquals(2, item.votersTotal());
    }

    // ---------- voteOnRemoval ----------

    @Test
    void voteOnRemovalShouldForbidTargetFromVotingOnOwnRemoval() {
        User target = resident("u2", "Bruno", false);
        HouseRemovalRequest req = openRemoval("rem-1", "u2", "u1");

        when(userRepository.findById("u2")).thenReturn(Optional.of(target));
        when(removalRequestRepository.findByIdAndHouseId("rem-1", "house-1")).thenReturn(Optional.of(req));

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("REJECT");

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> removalService.voteOnRemoval("u2", "house-1", "rem-1", voteDto));

        assertEquals("target member cannot vote on own removal", ex.getMessage());
        verifyNoInteractions(removalVoteRepository, houseMembershipService);
    }

    @Test
    void voteOnRemovalShouldRejectClosedRemoval() {
        User admin = resident("u1", "Alice", true);
        HouseRemovalRequest closed = openRemoval("rem-1", "u2", "u1");
        closed.setStatus(RemovalStatus.CANCELLED);

        when(userRepository.findById("u1")).thenReturn(Optional.of(admin));
        when(removalRequestRepository.findByIdAndHouseId("rem-1", "house-1")).thenReturn(Optional.of(closed));

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APPROVE");

        assertThrows(BusinessRuleException.class,
                () -> removalService.voteOnRemoval("u1", "house-1", "rem-1", voteDto));
        verifyNoInteractions(removalVoteRepository);
    }

    @Test
    void voteOnRemovalShouldAllowVoteSwapAndRejectUnderUnanimity() {
        User admin = resident("u1", "Alice", true);
        User target = resident("u2", "Bruno", false);
        User other = resident("u3", "Carla", false);
        HouseRemovalRequest req = openRemoval("rem-1", "u2", "u1");

        HouseRemovalVote existing = new HouseRemovalVote();
        existing.setId("v1");
        existing.setRemovalId("rem-1");
        existing.setVoterId("u1");
        existing.setVote(VoteType.APPROVE);

        when(userRepository.findById("u1")).thenReturn(Optional.of(admin));
        when(removalRequestRepository.findByIdAndHouseId("rem-1", "house-1")).thenReturn(Optional.of(req));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house(VotingRule.UNANIMITY)));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin, target, other));
        when(removalVoteRepository.findByRemovalIdAndVoterId("rem-1", "u1")).thenReturn(Optional.of(existing));
        when(removalVoteRepository.save(existing)).thenReturn(existing);
        when(removalVoteRepository.findByRemovalId("rem-1")).thenReturn(List.of(existing));
        when(removalRequestRepository.transitionStatusFromOpen("rem-1", RemovalStatus.REJECTED, FIXED_NOW_UTC))
                .thenReturn(1);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("REJECT");

        RemovalRequestResponse resp = removalService.voteOnRemoval("u1", "house-1", "rem-1", voteDto);

        assertEquals(VoteType.REJECT, existing.getVote());
        assertEquals(RemovalStatus.REJECTED, resp.status());
        assertEquals(FIXED_NOW_UTC, resp.resolvedAt());
        assertEquals("REJECT", resp.myVote());
        verifyNoInteractions(houseMembershipService);
    }

    @Test
    void voteOnRemovalShouldApproveAndDetachTargetWhenThresholdReached() {
        User admin = resident("u1", "Alice", true);
        User target = resident("u2", "Bruno", false);
        User other = resident("u3", "Carla", false);
        HouseRemovalRequest req = openRemoval("rem-1", "u2", "u1");

        HouseRemovalVote existingVote = new HouseRemovalVote();
        existingVote.setId("v1");
        existingVote.setRemovalId("rem-1");
        existingVote.setVoterId("u1");
        existingVote.setVote(VoteType.APPROVE);

        when(userRepository.findById("u3")).thenReturn(Optional.of(other));
        when(userRepository.findById("u2")).thenReturn(Optional.of(target));
        when(removalRequestRepository.findByIdAndHouseId("rem-1", "house-1")).thenReturn(Optional.of(req));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house(VotingRule.MAJORITY)));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin, target, other));
        when(removalVoteRepository.findByRemovalIdAndVoterId("rem-1", "u3")).thenReturn(Optional.empty());
        when(removalVoteRepository.save(any(HouseRemovalVote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(removalVoteRepository.findByRemovalId("rem-1")).thenAnswer(inv -> {
            HouseRemovalVote v2 = new HouseRemovalVote();
            v2.setId("v2");
            v2.setRemovalId("rem-1");
            v2.setVoterId("u3");
            v2.setVote(VoteType.APPROVE);
            return List.of(existingVote, v2);
        });
        when(removalRequestRepository.transitionStatusFromOpen("rem-1", RemovalStatus.APPROVED, FIXED_NOW_UTC))
                .thenReturn(1);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APPROVE");

        RemovalRequestResponse resp = removalService.voteOnRemoval("u3", "house-1", "rem-1", voteDto);

        assertEquals(RemovalStatus.APPROVED, resp.status());
        assertEquals(FIXED_NOW_UTC, resp.resolvedAt());
        assertEquals("APPROVE", resp.myVote());
        assertEquals(2, resp.votesCast());
        assertEquals(2, resp.votersTotal());
        verify(houseMembershipService).detachMember(target, "house-1");
    }

    @Test
    void voteOnRemovalShouldThrowConflictWhenConcurrentTransitionHappened() {
        User admin = resident("u1", "Alice", true);
        User target = resident("u2", "Bruno", false);
        HouseRemovalRequest req = openRemoval("rem-1", "u2", "u1");

        when(userRepository.findById("u1")).thenReturn(Optional.of(admin));
        when(removalRequestRepository.findByIdAndHouseId("rem-1", "house-1")).thenReturn(Optional.of(req));
        when(houseRepository.findById("house-1")).thenReturn(Optional.of(house(VotingRule.UNANIMITY)));
        when(userRepository.findByHouseIdOrderByIdAsc("house-1")).thenReturn(List.of(admin, target));
        when(removalVoteRepository.findByRemovalIdAndVoterId("rem-1", "u1")).thenReturn(Optional.empty());
        when(removalVoteRepository.save(any(HouseRemovalVote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(removalVoteRepository.findByRemovalId("rem-1")).thenAnswer(inv -> {
            HouseRemovalVote v1 = new HouseRemovalVote();
            v1.setId("v1");
            v1.setRemovalId("rem-1");
            v1.setVoterId("u1");
            v1.setVote(VoteType.APPROVE);
            return List.of(v1);
        });
        when(removalRequestRepository.transitionStatusFromOpen("rem-1", RemovalStatus.APPROVED, FIXED_NOW_UTC))
                .thenReturn(0);

        VoteRequestDto voteDto = new VoteRequestDto();
        voteDto.setVote("APPROVE");

        assertThrows(ConflictException.class,
                () -> removalService.voteOnRemoval("u1", "house-1", "rem-1", voteDto));
        verifyNoInteractions(houseMembershipService);
    }

    // ---------- cancelRemovalRequest ----------

    @Test
    void cancelRemovalShouldSucceedForProposerWhenOpen() {
        User proposer = resident("u1", "Alice", true);
        HouseRemovalRequest req = openRemoval("rem-1", "u2", "u1");

        when(userRepository.findById("u1")).thenReturn(Optional.of(proposer));
        when(removalRequestRepository.findByIdAndHouseId("rem-1", "house-1")).thenReturn(Optional.of(req));
        when(removalRequestRepository.transitionStatusFromOpen("rem-1", RemovalStatus.CANCELLED, FIXED_NOW_UTC))
                .thenReturn(1);

        removalService.cancelRemovalRequest("u1", "house-1", "rem-1");

        verify(removalRequestRepository).transitionStatusFromOpen("rem-1", RemovalStatus.CANCELLED, FIXED_NOW_UTC);
    }

    @Test
    void cancelRemovalShouldForbidOtherUserIncludingAnotherAdmin() {
        User otherAdmin = resident("u3", "Carla", true);
        HouseRemovalRequest req = openRemoval("rem-1", "u2", "u1");

        when(userRepository.findById("u3")).thenReturn(Optional.of(otherAdmin));
        when(removalRequestRepository.findByIdAndHouseId("rem-1", "house-1")).thenReturn(Optional.of(req));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> removalService.cancelRemovalRequest("u3", "house-1", "rem-1"));

        assertEquals("only the owner can perform this action", ex.getMessage());
        verify(removalRequestRepository, never()).transitionStatusFromOpen(any(), any(), any());
    }

    @Test
    void cancelRemovalShouldRejectAlreadyResolvedRemoval() {
        User proposer = resident("u1", "Alice", true);
        HouseRemovalRequest req = openRemoval("rem-1", "u2", "u1");
        req.setStatus(RemovalStatus.REJECTED);

        when(userRepository.findById("u1")).thenReturn(Optional.of(proposer));
        when(removalRequestRepository.findByIdAndHouseId("rem-1", "house-1")).thenReturn(Optional.of(req));

        assertThrows(BusinessRuleException.class,
                () -> removalService.cancelRemovalRequest("u1", "house-1", "rem-1"));
        verify(removalRequestRepository, never()).transitionStatusFromOpen(any(), any(), any());
    }
}
