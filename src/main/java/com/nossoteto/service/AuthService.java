package com.nossoteto.service;

import com.nossoteto.dto.RegisterRequest;
import com.nossoteto.dto.RegisterResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.model.User;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.Permissions;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]++@[^@.\\s]++(?:\\.[^@.\\s]++)++$");

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PermissionRepository permissionRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse registerUser(RegisterRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessRuleException("name is required");
        }

        String normalizedEmail = request.getEmail() == null ? null : request.getEmail().trim();
        if (normalizedEmail == null
                || normalizedEmail.isEmpty()
                || normalizedEmail.length() > MAX_EMAIL_LENGTH
                || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new BusinessRuleException("invalid email");
        }

        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new BusinessRuleException("password must have at least 8 characters");
        }

        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessRuleException("Email already exists");
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setName(request.getName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPermissions(new HashSet<>(permissionRepository.findByNameNot(Permissions.ADMIN_USERS)));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        return RegisterResponse.fromEntity(userRepository.save(user));
    }
}
