package com.nossoteto.security;

import com.nossoteto.model.RefreshToken;
import com.nossoteto.repository.RefreshTokenRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Profile("!basic-security")
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties, Clock clock) {
        Duration configuredTtl = properties.refreshTokenTtl();
        if (configuredTtl == null || configuredTtl.isZero() || configuredTtl.isNegative()) {
            throw new IllegalStateException("app.security.refresh-token-ttl must be positive");
        }
        this.repository = repository;
        this.ttl = configuredTtl;
        this.clock = clock;
    }

    @Transactional
    public String issue(String userId, String clientId, String scope, String familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now(clock);

        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID().toString());
        token.setTokenHash(sha256Hex(raw));
        token.setFamilyId(familyId != null ? familyId : UUID.randomUUID().toString());
        token.setUserId(userId);
        token.setClientId(clientId);
        token.setScope(scope);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(ttl));
        repository.save(token);
        return raw;
    }

    // noRollbackFor: a detected reuse must keep the family revocation even though we throw (INV-07).
    @Transactional(noRollbackFor = OAuthException.class)
    public Rotation rotate(String raw, String clientId) {
        if (raw == null || raw.isBlank()) {
            throw OAuthException.invalidGrant("Refresh token missing");
        }
        RefreshToken current = repository.findByTokenHash(sha256Hex(raw))
                .orElseThrow(() -> OAuthException.invalidGrant("Invalid refresh token"));
        LocalDateTime now = LocalDateTime.now(clock);

        if (current.getRevokedAt() != null) {
            throw OAuthException.invalidGrant("Refresh token revoked");
        }
        if (current.getUsedAt() != null) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw OAuthException.invalidGrant("Refresh token reuse detected");
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw OAuthException.invalidGrant("Refresh token expired");
        }
        if (!current.getClientId().equals(clientId)) {
            throw OAuthException.invalidGrant("Refresh token was issued to another client");
        }
        // Atomic compare-and-set: only one concurrent caller can flip used_at (INV-06).
        if (repository.markUsed(current.getId(), now) != 1) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw OAuthException.invalidGrant("Refresh token reuse detected");
        }
        String next = issue(current.getUserId(), current.getClientId(), current.getScope(), current.getFamilyId());
        return new Rotation(current.getUserId(), current.getScope(), next);
    }

    @Transactional
    public void revoke(String raw) {
        revoke(raw, null);
    }

    @Transactional
    public void revoke(String raw, String clientId) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        repository.findByTokenHash(sha256Hex(raw))
                .filter(t -> clientId == null || t.getClientId().equals(clientId))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), LocalDateTime.now(clock)));
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record Rotation(String userId, String scope, String refreshToken) {
        @Override
        public String toString() {
            return "Rotation[userId=" + userId + ", scope=" + scope + ", refreshToken=******]";
        }
    }
}
