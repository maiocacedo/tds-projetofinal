package com.nossoteto.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "budget")
    private BigDecimal budget;

    @Column(name = "has_pets")
    private Boolean hasPets = false;

    @Column(name = "is_smoker")
    private Boolean isSmoker = false;

    @Column(name = "profession")
    private String profession;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "allergies")
    private String allergies;

    @Column(name = "organization_level")
    private Integer organizationLevel;

    @Column(name = "visitor_tolerance")
    private Integer visitorTolerance;

    @Column(name = "has_furniture")
    private Boolean hasFurniture = false;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

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
