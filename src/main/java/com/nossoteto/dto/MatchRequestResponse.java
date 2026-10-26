package com.nossoteto.dto;

import com.nossoteto.model.enums.MatchStatus;

import java.time.LocalDateTime;

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
}
