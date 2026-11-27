package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public class UpdateRotationRequest {

    @JsonAlias({"ordem", "rotationOrder"})
    private List<String> order;

    public UpdateRotationRequest() {}

    public List<String> getOrder() { return order; }
    public void setOrder(List<String> order) { this.order = order; }
}
