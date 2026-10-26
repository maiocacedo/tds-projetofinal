package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class CreateMatchRequestDto {

    @JsonAlias("casaId")
    private String houseId;

    public CreateMatchRequestDto() {}

    public String getHouseId() { return houseId; }
    public void setHouseId(String houseId) { this.houseId = houseId; }
}
