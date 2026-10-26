package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class CreateChoreRequest {

    @JsonAlias("descricao")
    private String description;

    @JsonAlias("tipo")
    private String choreType;

    @JsonAlias({"responsavelId", "currentAssigneeId"})
    private String assigneeId;

    public CreateChoreRequest() {}

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getChoreType() { return choreType; }
    public void setChoreType(String choreType) { this.choreType = choreType; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }
}
