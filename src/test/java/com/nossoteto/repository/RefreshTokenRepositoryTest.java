package com.nossoteto.repository;

import com.nossoteto.model.RefreshToken;
import com.nossoteto.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private String persistUser() {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setName("Repo Test User");
        user.setEmail("repo-rt-" + UUID.randomUUID() + "@nossoteto.com");
        user.setPasswordHash("bcrypt-placeholder");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.saveAndFlush(user);
        return user.getId();
    }

    private RefreshToken persistToken(String userId, LocalDateTime now, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID().toString());
        token.setTokenHash(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        token.setFamilyId(UUID.randomUUID().toString());
        token.setUserId(userId);
        token.setClientId("nosso-teto-web");
        token.setScope("read write");
        token.setCreatedAt(now.minusMinutes(5));
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        return refreshTokenRepository.saveAndFlush(token);
    }

    @Test
    void markUsedReturnsOneForValidTokenAndZeroOnSecondAttempt() {
        String userId = persistUser();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        RefreshToken token = persistToken(userId, now, now.plusDays(1), null);

        assertEquals(1, refreshTokenRepository.markUsed(token.getId(), now));
        assertEquals(0, refreshTokenRepository.markUsed(token.getId(), now));
    }

    @Test
    void markUsedReturnsZeroForRevokedToken() {
        String userId = persistUser();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        RefreshToken revoked = persistToken(userId, now, now.plusDays(1), now.minusSeconds(10));

        assertEquals(0, refreshTokenRepository.markUsed(revoked.getId(), now));
    }

    @Test
    void markUsedReturnsZeroForExpiredToken() {
        String userId = persistUser();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        RefreshToken expired = persistToken(userId, now, now.minusSeconds(1), null);
        RefreshToken exactBoundary = persistToken(userId, now, now, null);

        assertEquals(0, refreshTokenRepository.markUsed(expired.getId(), now));
        assertEquals(0, refreshTokenRepository.markUsed(exactBoundary.getId(), now));
    }
}
