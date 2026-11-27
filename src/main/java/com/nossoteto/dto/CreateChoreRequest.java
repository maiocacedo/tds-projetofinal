package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public class CreateChoreRequest {

    @JsonAlias("descricao")
    private String description;

    @JsonAlias("tipo")
    private String choreType;

    @JsonAlias({"responsavelId", "currentAssigneeId"})
    private String assigneeId;

    @JsonAlias("frequencia")
    private String frequency;

    @JsonAlias("ordemRoda")
    private List<String> rotationOrder;

    @JsonAlias("atalhoMercado")
    private Boolean linkedToGroceries;

    public CreateChoreRequest() {}

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getChoreType() { return choreType; }
    public void setChoreType(String choreType) { this.choreType = choreType; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public List<String> getRotationOrder() { return rotationOrder; }
    public void setRotationOrder(List<String> rotationOrder) { this.rotationOrder = rotationOrder; }

    public Boolean getLinkedToGroceries() { return linkedToGroceries; }
    public void setLinkedToGroceries(Boolean linkedToGroceries) { this.linkedToGroceries = linkedToGroceries; }
}
