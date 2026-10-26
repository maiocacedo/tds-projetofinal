package com.nossoteto.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.nossoteto.model.UserProfile;

import java.math.BigDecimal;

public class UserProfileDto {

    @JsonAlias("orcamentoMensal")
    private BigDecimal budget;

    @JsonAlias("possuiPets")
    private Boolean hasPets;

    @JsonAlias("fumante")
    private Boolean isSmoker;

    @JsonAlias("profissao")
    private String profession;

    @JsonAlias("biografia")
    private String bio;

    @JsonAlias("alergias")
    private String allergies;

    @JsonAlias("nivelOrganizacao")
    private Integer organizationLevel;

    @JsonAlias("toleranciaVisitas")
    private Integer visitorTolerance;

    @JsonAlias("mobiliaPropria")
    private Boolean hasFurniture;

    public UserProfileDto() {}

    public static UserProfileDto fromEntity(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        UserProfileDto dto = new UserProfileDto();
        dto.setBudget(profile.getBudget());
        dto.setHasPets(profile.getHasPets());
        dto.setIsSmoker(profile.getIsSmoker());
        dto.setProfession(profile.getProfession());
        dto.setBio(profile.getBio());
        dto.setAllergies(profile.getAllergies());
        dto.setOrganizationLevel(profile.getOrganizationLevel());
        dto.setVisitorTolerance(profile.getVisitorTolerance());
        dto.setHasFurniture(profile.getHasFurniture());
        return dto;
    }

    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }

    public Boolean getHasPets() { return hasPets; }
    public void setHasPets(Boolean hasPets) { this.hasPets = hasPets; }

    public Boolean getIsSmoker() { return isSmoker; }
    public void setIsSmoker(Boolean isSmoker) { this.isSmoker = isSmoker; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }

    public Integer getOrganizationLevel() { return organizationLevel; }
    public void setOrganizationLevel(Integer organizationLevel) { this.organizationLevel = organizationLevel; }

    public Integer getVisitorTolerance() { return visitorTolerance; }
    public void setVisitorTolerance(Integer visitorTolerance) { this.visitorTolerance = visitorTolerance; }

    public Boolean getHasFurniture() { return hasFurniture; }
    public void setHasFurniture(Boolean hasFurniture) { this.hasFurniture = hasFurniture; }
}
