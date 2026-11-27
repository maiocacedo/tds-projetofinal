package com.nossoteto.service;

import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.model.PasswordResetToken;
import com.nossoteto.model.RefreshToken;
import com.nossoteto.model.User;
import com.nossoteto.repository.PasswordResetTokenRepository;
import com.nossoteto.repository.RefreshTokenRepository;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PasswordResetServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private MutableClock clock;
    private PasswordResetService demoEnabledService;
    private PasswordResetService demoDisabledService;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-26T20:00:00Z"));
        demoEnabledService = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                clock,
                true);
        demoDisabledService = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                refreshTokenRepository,
                passwordEncoder,
                clock,
                false);
    }

    private User persistUser(String email, String rawPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setName("Usuario Teste");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.saveAndFlush(user);
    }

    private RefreshToken persistRefreshToken(String userId, LocalDateTime revokedAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID().toString());
        token.setTokenHash(RefreshTokenService.sha256Hex(UUID.randomUUID().toString()));
        token.setFamilyId(UUID.randomUUID().toString());
        token.setUserId(userId);
        token.setClientId("nosso-teto-web");
        token.setScope("read write");
        token.setCreatedAt(now.minusMinutes(5));
        token.setExpiresAt(now.plusDays(7));
        token.setRevokedAt(revokedAt);
        return refreshTokenRepository.saveAndFlush(token);
    }

    @Test
    void requestResetWithNullBlankOrUnknownEmailReturnsEmptyWithoutPersistingToken() {
        long before = passwordResetTokenRepository.count();

        assertTrue(demoEnabledService.requestReset(null).isEmpty());
        assertTrue(demoEnabledService.requestReset("   ").isEmpty());
        assertTrue(demoEnabledService.requestReset("desconhecido@nossoteto.com").isEmpty());
        assertEquals(before, passwordResetTokenRepository.count());
    }

    @Test
    void requestResetWithDemoFlagDisabledPersistsHashedTokenAndReturnsEmpty() {
        String email = "disabled-" + UUID.randomUUID() + "@nossoteto.com";
        User user = persistUser(email, "senhaAntiga1");
        long before = passwordResetTokenRepository.count();

        Optional<String> result = demoDisabledService.requestReset("  " + email + "  ");

        assertTrue(result.isEmpty());
        assertEquals(before + 1, passwordResetTokenRepository.count());
        PasswordResetToken saved = passwordResetTokenRepository.findAll().stream()
                .filter(t -> user.getId().equals(t.getUserId()))
                .findFirst()
                .orElseThrow();
        assertEquals(64, saved.getTokenHash().length());
        assertEquals(LocalDateTime.now(clock), saved.getCreatedAt());
        assertEquals(LocalDateTime.now(clock).plusMinutes(15), saved.getExpiresAt());
        assertNull(saved.getUsedAt());
    }

    @Test
    void requestResetWithDemoFlagEnabledReturnsRawTokenAndStoresOnlySha256Hash() {
        String email = "enabled-" + UUID.randomUUID() + "@nossoteto.com";
        User user = persistUser(email, "senhaAntiga1");

        String rawToken = demoEnabledService.requestReset(" " + email + " ").orElseThrow();

        assertEquals(43, rawToken.length());
        assertFalse(rawToken.contains("="));
        PasswordResetToken stored = passwordResetTokenRepository
                .findByTokenHash(RefreshTokenService.sha256Hex(rawToken))
                .orElseThrow();
        assertEquals(user.getId(), stored.getUserId());
        assertNotEquals(rawToken, stored.getTokenHash());
        assertEquals(64, stored.getTokenHash().length());
        assertEquals(LocalDateTime.now(clock), stored.getCreatedAt());
        assertEquals(LocalDateTime.now(clock).plusMinutes(15), stored.getExpiresAt());
        assertNull(stored.getUsedAt());
    }

    @Test
    void confirmResetSucceedsAt14Minutes59SecondsAndUpdatesPasswordAndRevokesRefreshTokens() {
        String email = "ttl-ok-" + UUID.randomUUID() + "@nossoteto.com";
        User user = persistUser(email, "senhaAntiga1");
        User otherUser = persistUser("other-" + UUID.randomUUID() + "@nossoteto.com", "outraSenha1");
        RefreshToken activeRefresh = persistRefreshToken(user.getId(), null);
        LocalDateTime priorRevocation = LocalDateTime.now(clock).minusMinutes(2);
        RefreshToken alreadyRevoked = persistRefreshToken(user.getId(), priorRevocation);
        RefreshToken otherUserRefresh = persistRefreshToken(otherUser.getId(), null);

        String rawToken = demoEnabledService.requestReset(email).orElseThrow();

        clock.advanceSeconds(14 * 60 + 59);
        LocalDateTime confirmTime = LocalDateTime.now(clock);

        demoEnabledService.confirmReset(rawToken, "novaSenha123");

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("novaSenha123", updated.getPasswordHash()));
        assertFalse(passwordEncoder.matches("senhaAntiga1", updated.getPasswordHash()));
        assertEquals(confirmTime, updated.getUpdatedAt());

        PasswordResetToken used = passwordResetTokenRepository
                .findByTokenHash(RefreshTokenService.sha256Hex(rawToken))
                .orElseThrow();
        assertEquals(confirmTime, used.getUsedAt());

        assertEquals(confirmTime, refreshTokenRepository.findById(activeRefresh.getId()).orElseThrow().getRevokedAt());
        assertEquals(priorRevocation, refreshTokenRepository.findById(alreadyRevoked.getId()).orElseThrow().getRevokedAt());
        assertNull(refreshTokenRepository.findById(otherUserRefresh.getId()).orElseThrow().getRevokedAt());
    }

    @Test
    void confirmResetFailsAtExact15MinuteBoundary() {
        String email = "ttl-exp-" + UUID.randomUUID() + "@nossoteto.com";
        persistUser(email, "senhaAntiga1");
        String rawToken = demoEnabledService.requestReset(email).orElseThrow();

        clock.advanceSeconds(15 * 60);

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> demoEnabledService.confirmReset(rawToken, "novaSenha123"));
        assertEquals("invalid or expired token", ex.getMessage());
    }

    @Test
    void confirmResetWithShortOrNullPasswordDoesNotConsumeToken() {
        String email = "short-pass-" + UUID.randomUUID() + "@nossoteto.com";
        User user = persistUser(email, "senhaAntiga1");
        String rawToken = demoEnabledService.requestReset(email).orElseThrow();

        BusinessRuleException nullEx = assertThrows(
                BusinessRuleException.class,
                () -> demoEnabledService.confirmReset(rawToken, null));
        assertEquals("password must have at least 8 characters", nullEx.getMessage());

        BusinessRuleException shortEx = assertThrows(
                BusinessRuleException.class,
                () -> demoEnabledService.confirmReset(rawToken, "1234567"));
        assertEquals("password must have at least 8 characters", shortEx.getMessage());

        PasswordResetToken stillUnused = passwordResetTokenRepository
                .findByTokenHash(RefreshTokenService.sha256Hex(rawToken))
                .orElseThrow();
        assertNull(stillUnused.getUsedAt());

        demoEnabledService.confirmReset(rawToken, "12345678");
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("12345678", updated.getPasswordHash()));
    }

    @Test
    void confirmResetRejectsNullBlankOrUnknownTokenAndBlocksReuse() {
        BusinessRuleException nullTokenEx = assertThrows(
                BusinessRuleException.class,
                () -> demoEnabledService.confirmReset(null, "novaSenha123"));
        assertEquals("invalid or expired token", nullTokenEx.getMessage());

        BusinessRuleException blankTokenEx = assertThrows(
                BusinessRuleException.class,
                () -> demoEnabledService.confirmReset("   ", "novaSenha123"));
        assertEquals("invalid or expired token", blankTokenEx.getMessage());

        BusinessRuleException unknownTokenEx = assertThrows(
                BusinessRuleException.class,
                () -> demoEnabledService.confirmReset("token-inexistente", "novaSenha123"));
        assertEquals("invalid or expired token", unknownTokenEx.getMessage());

        String email = "reuse-" + UUID.randomUUID() + "@nossoteto.com";
        persistUser(email, "senhaAntiga1");
        String rawToken = demoEnabledService.requestReset(email).orElseThrow();

        demoEnabledService.confirmReset(rawToken, "novaSenha123");

        BusinessRuleException reuseEx = assertThrows(
                BusinessRuleException.class,
                () -> demoEnabledService.confirmReset(rawToken, "outraSenha123"));
        assertEquals("invalid or expired token", reuseEx.getMessage());
    }

    @Test
    void confirmResetWithTokenBInvalidatesPreviousTokenAForSameUser() {
        String email = "multi-" + UUID.randomUUID() + "@nossoteto.com";
        persistUser(email, "senhaAntiga1");
        String tokenA = demoEnabledService.requestReset(email).orElseThrow();
        clock.advanceSeconds(10);
        String tokenB = demoEnabledService.requestReset(email).orElseThrow();

        demoEnabledService.confirmReset(tokenB, "novaSenha123");

        PasswordResetToken storedA = passwordResetTokenRepository
                .findByTokenHash(RefreshTokenService.sha256Hex(tokenA))
                .orElseThrow();
        assertNotNull(storedA.getUsedAt());

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> demoEnabledService.confirmReset(tokenA, "outraSenha123"));
        assertEquals("invalid or expired token", ex.getMessage());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        void advanceSeconds(long seconds) {
            this.current = this.current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
