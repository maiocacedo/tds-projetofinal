package com.nossoteto.dto;

import com.nossoteto.model.enums.MatchStatus;

import java.time.LocalDateTime;
import java.util.List;

public class MatchRequestResponse {

    private String id;
    private String userId;
    private String userName;
    private String userEmail;
    private String houseId;
    private MatchStatus status;
    private Integer compatibilityScore;
    private UserProfileDto userProfile;
    private LocalDateTime createdAt;
    // UTC end of the 72 h reservation; null unless the request is (or was) AWAITING_MOVE.
    private LocalDateTime moveDeadline;
    // Filled by GET /api/houses/{id}/matches only.
    private CandidateCardDto candidate;
    private List<VoterSummaryDto> voters;
    private String myVote;

    public MatchRequestResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public Integer getCompatibilityScore() { return compatibilityScore; }
    public void setCompatibilityScore(Integer compatibilityScore) { this.compatibilityScore = compatibilityScore; }

    public UserProfileDto getUserProfile() { return userProfile; }
    public void setUserProfile(UserProfileDto userProfile) { this.userProfile = userProfile; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getMoveDeadline() { return moveDeadline; }
    public void setMoveDeadline(LocalDateTime moveDeadline) { this.moveDeadline = moveDeadline; }

    public CandidateCardDto getCandidate() { return candidate; }
    public void setCandidate(CandidateCardDto candidate) { this.candidate = candidate; }

    public List<VoterSummaryDto> getVoters() { return voters; }
    public void setVoters(List<VoterSummaryDto> voters) { this.voters = voters; }

    public String getMyVote() { return myVote; }
    public void setMyVote(String myVote) { this.myVote = myVote; }
}
