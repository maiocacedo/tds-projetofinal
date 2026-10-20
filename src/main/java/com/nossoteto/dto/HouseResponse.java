package com.nossoteto.dto;

import com.nossoteto.model.House;
import com.nossoteto.model.enums.VotingRule;

import java.math.BigDecimal;
import java.util.List;

public class HouseResponse {

    private String id;
    private String name;
    private String address;
    private String description;
    private String basicRules;
    private BigDecimal rentAmount;
    private Boolean allowsPets;
    private Boolean allowsSmokers;
    private Integer expectedOrganizationLevel;
    private Integer visitorTolerance;
    private Boolean hasFurniture;
    private VotingRule votingRule;
    private Boolean isAcceptingMatches;
    private Integer compatibilityScore;
    private List<HouseMemberSummaryDto> members;

    public HouseResponse() {
    }

    public static HouseResponse fromEntity(House house, Integer compatibilityScore, List<HouseMemberSummaryDto> members) {
        HouseResponse response = new HouseResponse();
        response.setId(house.getId());
        response.setName(house.getName());
        response.setAddress(house.getAddress());
        response.setDescription(house.getDescription());
        response.setBasicRules(house.getBasicRules());
        response.setRentAmount(house.getRentAmount());
        response.setAllowsPets(house.getAllowsPets());
        response.setAllowsSmokers(house.getAllowsSmokers());
        response.setExpectedOrganizationLevel(house.getExpectedOrganizationLevel());
        response.setVisitorTolerance(house.getVisitorTolerance());
        response.setHasFurniture(house.getHasFurniture());
        response.setVotingRule(house.getVotingRule());
        response.setIsAcceptingMatches(house.getIsAcceptingMatches());
        response.setCompatibilityScore(compatibilityScore);
        response.setMembers(members);
        return response;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBasicRules() { return basicRules; }
    public void setBasicRules(String basicRules) { this.basicRules = basicRules; }

    public BigDecimal getRentAmount() { return rentAmount; }
    public void setRentAmount(BigDecimal rentAmount) { this.rentAmount = rentAmount; }

    public Boolean getAllowsPets() { return allowsPets; }
    public void setAllowsPets(Boolean allowsPets) { this.allowsPets = allowsPets; }

    public Boolean getAllowsSmokers() { return allowsSmokers; }
    public void setAllowsSmokers(Boolean allowsSmokers) { this.allowsSmokers = allowsSmokers; }

    public Integer getExpectedOrganizationLevel() { return expectedOrganizationLevel; }
    public void setExpectedOrganizationLevel(Integer expectedOrganizationLevel) { this.expectedOrganizationLevel = expectedOrganizationLevel; }

    public Integer getVisitorTolerance() { return visitorTolerance; }
    public void setVisitorTolerance(Integer visitorTolerance) { this.visitorTolerance = visitorTolerance; }

    public Boolean getHasFurniture() { return hasFurniture; }
    public void setHasFurniture(Boolean hasFurniture) { this.hasFurniture = hasFurniture; }

    public VotingRule getVotingRule() { return votingRule; }
    public void setVotingRule(VotingRule votingRule) { this.votingRule = votingRule; }

    public Boolean getIsAcceptingMatches() { return isAcceptingMatches; }
    public void setIsAcceptingMatches(Boolean isAcceptingMatches) { this.isAcceptingMatches = isAcceptingMatches; }

    public Integer getCompatibilityScore() { return compatibilityScore; }
    public void setCompatibilityScore(Integer compatibilityScore) { this.compatibilityScore = compatibilityScore; }

    public List<HouseMemberSummaryDto> getMembers() { return members; }
    public void setMembers(List<HouseMemberSummaryDto> members) { this.members = members; }
}
