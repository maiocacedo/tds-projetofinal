package com.nossoteto.service;

import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.model.PasswordResetToken;
import com.nossoteto.model.User;
import com.nossoteto.repository.PasswordResetTokenRepository;
import com.nossoteto.repository.RefreshTokenRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String INVALID_OR_EXPIRED_TOKEN = "invalid or expired token";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final boolean demoPasswordResetEnabled;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.auth.demo-password-reset:false}") boolean demoPasswordResetEnabled) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.demoPasswordResetEnabled = demoPasswordResetEnabled;
    }

    @Transactional
    public Optional<String> requestReset(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String normalizedEmail = email.trim();
        if (normalizedEmail.isEmpty()) {
            return Optional.empty();
        }

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now(clock);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setId(UUID.randomUUID().toString());
        resetToken.setUserId(userOpt.get().getId());
        resetToken.setTokenHash(RefreshTokenService.sha256Hex(rawToken));
        resetToken.setCreatedAt(now);
        resetToken.setExpiresAt(now.plus(TOKEN_TTL));
        passwordResetTokenRepository.save(resetToken);

        return demoPasswordResetEnabled ? Optional.of(rawToken) : Optional.empty();
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException("password must have at least 8 characters");
        }
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessRuleException(INVALID_OR_EXPIRED_TOKEN);
        }

        String tokenHash = RefreshTokenService.sha256Hex(rawToken);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessRuleException(INVALID_OR_EXPIRED_TOKEN));

        String tokenId = token.getId();
        String userId = token.getUserId();
        LocalDateTime now = LocalDateTime.now(clock);

        if (passwordResetTokenRepository.markUsed(tokenId, now) != 1) {
            throw new BusinessRuleException(INVALID_OR_EXPIRED_TOKEN);
        }
        passwordResetTokenRepository.markAllUnusedByUserId(userId, now);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException(INVALID_OR_EXPIRED_TOKEN));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(now);
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(userId, now);
    }
}
