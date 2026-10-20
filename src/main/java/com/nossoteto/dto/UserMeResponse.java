package com.nossoteto.dto;

public class UserMeResponse {

    private String id;
    private String name;
    private String email;
    private String houseId;
    private Boolean isHouseAdmin;
    private String houseName;
    private UserProfileDto profile;

    public UserMeResponse() {}

    public UserMeResponse(String id, String name, String email, String houseId,
                          Boolean isHouseAdmin, String houseName, UserProfileDto profile) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.houseId = houseId;
        this.isHouseAdmin = isHouseAdmin;
        this.houseName = houseName;
        this.profile = profile;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }

    public Boolean getIsHouseAdmin() { return isHouseAdmin; }
    public void setIsHouseAdmin(Boolean isHouseAdmin) { this.isHouseAdmin = isHouseAdmin; }

    public String getHouseName() { return houseName; }
    public void setHouseName(String houseName) { this.houseName = houseName; }

    public UserProfileDto getProfile() { return profile; }
    public void setProfile(UserProfileDto profile) { this.profile = profile; }
}
