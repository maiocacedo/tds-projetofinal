package com.nossoteto.service;

import com.nossoteto.dto.AdminUserResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.Permission;
import com.nossoteto.model.User;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    public AdminService(UserRepository userRepository, PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminUserResponse replacePermissions(String actorId, String targetId, List<String> names) {
        if (names == null) {
            throw new BusinessRuleException("permissions is required");
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetId));
        if (names.stream().anyMatch(n -> n == null || n.isBlank())) {
            throw new BusinessRuleException("Permission names must not be null or blank");
        }
        Set<String> requested = names.stream().collect(Collectors.toSet());
        if (targetId.equals(actorId) && !requested.contains(Permissions.ADMIN_USERS)) {
            throw new BusinessRuleException("An administrator cannot remove their own ROLE_ADMIN_USERS");
        }
        List<Permission> found = requested.isEmpty() ? List.of() : permissionRepository.findByNameIn(requested);
        if (found.size() != requested.size()) {
            Set<String> known = found.stream().map(Permission::getName).collect(Collectors.toSet());
            String unknown = requested.stream().filter(n -> !known.contains(n)).sorted().collect(Collectors.joining(", "));
            throw new BusinessRuleException("Unknown permission: " + unknown);
        }
        target.setPermissions(new HashSet<>(found));
        userRepository.save(target);
        return toResponse(target);
    }

    private AdminUserResponse toResponse(User user) {
        List<String> permissions = userRepository.findPermissionNamesByUserId(user.getId()).stream().sorted().toList();
        return new AdminUserResponse(user.getId(), user.getName(), user.getEmail(), user.getHouseId(), permissions);
    }
}
