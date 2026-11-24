package com.nossoteto.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "description")
    private String description;

    public Permission() {}

    public Permission(Integer id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission other)) return false;
        return Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() { return Objects.hashCode(name); }
}
