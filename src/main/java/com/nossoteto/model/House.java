package com.nossoteto.model;

import com.nossoteto.model.enums.VotingRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "houses")
public class House {

    @Id
    private String id;
    private String name;
    private String address;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_accepting_matches")
    private Boolean isAcceptingMatches = true;

    @Column(name = "rent_amount")
    private BigDecimal rentAmount;

    @Column(name = "allows_pets")
    private Boolean allowsPets = true;

    @Column(name = "allows_smokers")
    private Boolean allowsSmokers = false;

    @Column(name = "expected_organization_level")
    private Integer expectedOrganizationLevel = 3;

    @Column(name = "visitor_tolerance")
    private Integer visitorTolerance = 3;

    @Column(name = "has_furniture")
    private Boolean hasFurniture = false;

    @Column(name = "basic_rules", columnDefinition = "TEXT")
    private String basicRules;

    @Enumerated(EnumType.STRING)
    @Column(name = "voting_rule", nullable = false)
    private VotingRule votingRule = VotingRule.UNANIMITY;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsAcceptingMatches() { return isAcceptingMatches; }
    public void setIsAcceptingMatches(Boolean isAcceptingMatches) { this.isAcceptingMatches = isAcceptingMatches; }

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

    public String getBasicRules() { return basicRules; }
    public void setBasicRules(String basicRules) { this.basicRules = basicRules; }

    public VotingRule getVotingRule() { return votingRule; }
    public void setVotingRule(VotingRule votingRule) { this.votingRule = votingRule; }
}
