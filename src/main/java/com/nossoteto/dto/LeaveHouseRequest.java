package com.nossoteto.dto;

public class LeaveHouseRequest {

    private String newAdminId;

    public LeaveHouseRequest() {}

    public LeaveHouseRequest(String newAdminId) {
        this.newAdminId = newAdminId;
    }

    public String getNewAdminId() {
        return newAdminId;
    }

    public void setNewAdminId(String newAdminId) {
        this.newAdminId = newAdminId;
    }
}
