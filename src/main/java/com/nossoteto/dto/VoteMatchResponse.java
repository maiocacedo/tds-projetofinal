package com.nossoteto.dto;

import com.nossoteto.model.enums.MatchStatus;

import java.util.ArrayList;
import java.util.List;

public class VoteMatchResponse {

    private String requestId;
    private MatchStatus status;
    private int approveVotes;
    private int rejectVotes;
    private int totalHouseMembers;
    private boolean hasOtherPendingRequests;
    private List<String> otherPendingRequestIds = new ArrayList<>();

    public VoteMatchResponse() {}

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public int getApproveVotes() { return approveVotes; }
    public void setApproveVotes(int approveVotes) { this.approveVotes = approveVotes; }

    public int getRejectVotes() { return rejectVotes; }
    public void setRejectVotes(int rejectVotes) { this.rejectVotes = rejectVotes; }

    public int getTotalHouseMembers() { return totalHouseMembers; }
    public void setTotalHouseMembers(int totalHouseMembers) { this.totalHouseMembers = totalHouseMembers; }

    public boolean isHasOtherPendingRequests() { return hasOtherPendingRequests; }
    public void setHasOtherPendingRequests(boolean hasOtherPendingRequests) { this.hasOtherPendingRequests = hasOtherPendingRequests; }

    public List<String> getOtherPendingRequestIds() { return otherPendingRequestIds; }
    public void setOtherPendingRequestIds(List<String> otherPendingRequestIds) { this.otherPendingRequestIds = otherPendingRequestIds; }
}
