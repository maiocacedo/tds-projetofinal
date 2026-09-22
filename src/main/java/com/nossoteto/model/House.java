package com.nossoteto.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "houses")
public class House {
    
    @Id
    private String id;
    private String name;
    private String address;
    private String description;
    
    @Column(name = "is_accepting_matches")
    private Boolean isAcceptingMatches = true;

    // Getters and setters
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
}
