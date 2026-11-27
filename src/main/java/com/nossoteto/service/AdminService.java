package com.nossoteto.service;

import com.nossoteto.dto.AdminUserResponse;
import com.nossoteto.dto.PermissionDto;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.ResourceNotFoundException;
import com.nossoteto.model.House;
import com.nossoteto.model.Permission;
import com.nossoteto.model.User;
import com.nossoteto.repository.HouseRepository;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.RefreshTokenRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final HouseRepository houseRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public AdminService(
            UserRepository userRepository,
            PermissionRepository permissionRepository,
            HouseRepository houseRepository,
            RefreshTokenRepository refreshTokenRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.houseRepository = houseRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        List<User> users = userRepository.findAll();
        Set<String> houseIds = users.stream()
                .map(User::getHouseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> houseNames = new HashMap<>();
        if (!houseIds.isEmpty()) {
            for (House house : houseRepository.findAllById(houseIds)) {
                houseNames.put(house.getId(), house.getName());
            }
        }
        return users.stream()
                .map(u -> toResponse(u, u.getHouseId() != null ? houseNames.get(u.getHouseId()) : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> listPermissions() {
        return permissionRepository.findAll(Sort.by("id")).stream()
                .map(p -> new PermissionDto(
                        p.getName(),
                        (p.getDescription() != null && !p.getDescription().isBlank())
                                ? p.getDescription()
                                : p.getName()))
                .toList();
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
        Set<String> requested = new HashSet<>(names);
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

        String houseName = target.getHouseId() != null
                ? houseRepository.findById(target.getHouseId()).map(House::getName).orElse(null)
                : null;
        AdminUserResponse response = toResponse(target, houseName);

        // revokeAllByUserId uses clearAutomatically = true, so build the response before revoking.
        refreshTokenRepository.revokeAllByUserId(target.getId(), LocalDateTime.now(clock));
        return response;
    }

    private AdminUserResponse toResponse(User user, String houseName) {
        List<String> permissions = userRepository.findPermissionNamesByUserId(user.getId()).stream().sorted().toList();
        return new AdminUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getHouseId(),
                houseName,
                permissions);
    }
}
