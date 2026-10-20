package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.nossoteto.model.enums.VotingRule;

import java.math.BigDecimal;

public class CreateHouseRequest {

    @JsonAlias("nome")
    private String name;

    @JsonAlias("endereco")
    private String address;

    @JsonAlias("descricao")
    private String description;

    @JsonAlias("regrasBasicas")
    private String basicRules;

    @JsonAlias({"valorAluguel", "orcamentoMensal"})
    private BigDecimal rentAmount;

    @JsonAlias("aceitaPets")
    private Boolean allowsPets;

    @JsonAlias("aceitaFumantes")
    private Boolean allowsSmokers;

    @JsonAlias("nivelOrganizacaoEsperado")
    private Integer expectedOrganizationLevel;

    @JsonAlias("toleranciaVisitas")
    private Integer visitorTolerance;

    @JsonAlias("mobiliaExistente")
    private Boolean hasFurniture;

    @JsonAlias("regraVotacao")
    private VotingRule votingRule;

    @JsonAlias("aceitandoMatches")
    private Boolean isAcceptingMatches;

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
}
