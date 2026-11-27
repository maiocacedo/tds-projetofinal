package com.nossoteto.dto;

/**
 * Body of {@code POST /api/matches/requests/{id}/confirm-move}. {@code newAdminId} is only
 * required when the mover is the last admin of a house that still has other residents.
 */
public class ConfirmMoveRequest {

    private String newAdminId;

    public ConfirmMoveRequest() {}

    public ConfirmMoveRequest(String newAdminId) {
        this.newAdminId = newAdminId;
    }

    public String getNewAdminId() { return newAdminId; }
    public void setNewAdminId(String newAdminId) { this.newAdminId = newAdminId; }
}
