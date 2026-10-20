package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public class VoteRequestDto {

    @JsonAlias("voto")
    private String vote;

    public VoteRequestDto() {}

    public String getVote() { return vote; }
    public void setVote(String vote) { this.vote = vote; }
}
