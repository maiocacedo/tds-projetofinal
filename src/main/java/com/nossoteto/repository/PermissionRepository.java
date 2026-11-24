package com.nossoteto.repository;

import com.nossoteto.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {
    List<Permission> findByNameIn(Collection<String> names);
    List<Permission> findByNameNot(String name);
}
