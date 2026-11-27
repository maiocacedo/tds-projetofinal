package com.nossoteto.dto;

public class CreateRemovalRequestDto {

    private String memberId;
    private String reason;

    public CreateRemovalRequestDto() {}

    public CreateRemovalRequestDto(String memberId, String reason) {
        this.memberId = memberId;
        this.reason = reason;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
