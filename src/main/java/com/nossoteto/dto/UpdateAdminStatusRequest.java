package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpdateAdminStatusRequest {

    @JsonProperty("isAdmin")
    private Boolean isAdmin;

    public UpdateAdminStatusRequest() {}

    public UpdateAdminStatusRequest(Boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    @JsonProperty("isAdmin")
    public Boolean getIsAdmin() {
        return isAdmin;
    }

    @JsonProperty("isAdmin")
    public void setIsAdmin(Boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
}
