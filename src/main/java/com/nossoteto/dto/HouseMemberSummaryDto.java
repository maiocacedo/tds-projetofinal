package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nossoteto.model.User;

public class HouseMemberSummaryDto {

    private String id;
    private String name;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String email;
    private Boolean isHouseAdmin;

    public HouseMemberSummaryDto() {
    }

    public HouseMemberSummaryDto(String id, String name, String email, Boolean isHouseAdmin) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.isHouseAdmin = isHouseAdmin;
    }

    public static HouseMemberSummaryDto fromEntity(User user, boolean includeEmail) {
        if (user == null) {
            return null;
        }
        return new HouseMemberSummaryDto(
                user.getId(),
                user.getName(),
                includeEmail ? user.getEmail() : null,
                user.getIsHouseAdmin()
        );
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Boolean getIsHouseAdmin() { return isHouseAdmin; }
    public void setIsHouseAdmin(Boolean isHouseAdmin) { this.isHouseAdmin = isHouseAdmin; }
}
