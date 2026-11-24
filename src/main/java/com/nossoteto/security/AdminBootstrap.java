package com.nossoteto.security;

import com.nossoteto.model.User;
import com.nossoteto.repository.PermissionRepository;
import com.nossoteto.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository, PermissionRepository permissionRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("ADMIN_PASSWORD not set: skipping platform admin bootstrap");
            return;
        }
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User admin = existing.get();
            List<String> currentPermissions = userRepository.findPermissionNamesByUserId(admin.getId());
            if (!currentPermissions.contains(Permissions.ADMIN_USERS)) {
                log.warn("Account {} exists without ROLE_ADMIN_USERS; refusing to promote it to platform admin", email);
                return;
            }
            // Idempotent: never resets an existing password, only guarantees the full permission set.
            admin.setPermissions(new HashSet<>(permissionRepository.findAll()));
            userRepository.save(admin);
            log.info("Platform admin ensured for {}", email);
            return;
        }
        User admin = new User();
        admin.setId(UUID.randomUUID().toString());
        admin.setName("Administrador");
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        admin.setPermissions(new HashSet<>(permissionRepository.findAll()));
        userRepository.save(admin);
        log.info("Platform admin created for {}", email);
    }
}
