package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class CreateChoreSwapRequest {

    @JsonAlias("tarefaAlvoId")
    private String targetChoreId;

    public CreateChoreSwapRequest() {}

    public String getTargetChoreId() { return targetChoreId; }
    public void setTargetChoreId(String targetChoreId) { this.targetChoreId = targetChoreId; }
}
