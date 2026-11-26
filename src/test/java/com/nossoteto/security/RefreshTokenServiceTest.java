package com.nossoteto.security;

import com.nossoteto.model.RefreshToken;
import com.nossoteto.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest
class RefreshTokenServiceTest {

    @Autowired private RefreshTokenService service;
    @SpyBean private RefreshTokenRepository repository;
    @Autowired private com.nossoteto.service.AuthService authService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String newUser() {
        com.nossoteto.dto.RegisterRequest r = new com.nossoteto.dto.RegisterRequest();
        r.setName("Usuario Teste");
        r.setEmail("rt-" + UUID.randomUUID() + "@nossoteto.com");
        r.setPassword("senha123");
        return authService.registerUser(r).getId();
    }

    @Test
    void onlyTheHashIsPersisted() {
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        RefreshToken stored = repository.findByTokenHash(RefreshTokenService.sha256Hex(raw)).orElseThrow();
        assertNotEquals(raw, stored.getTokenHash());
        assertEquals(64, stored.getTokenHash().length());
        assertTrue(raw.length() >= 43);
    }

    @Test
    void rotationIssuesNewTokenInSameFamilyAndKeepsScope() {
        String raw = service.issue(newUser(), "nosso-teto-web", "read", null);
        RefreshTokenService.Rotation rotation = service.rotate(raw, "nosso-teto-web");
        assertNotEquals(raw, rotation.refreshToken());
        assertEquals("read", rotation.scope());
        RefreshToken oldT = repository.findByTokenHash(RefreshTokenService.sha256Hex(raw)).orElseThrow();
        RefreshToken newT = repository.findByTokenHash(RefreshTokenService.sha256Hex(rotation.refreshToken())).orElseThrow();
        assertEquals(oldT.getFamilyId(), newT.getFamilyId());
        assertNotNull(oldT.getUsedAt());
    }

    @Test
    void reuseRevokesTheWholeFamilyAndThatRevocationIsCommitted() {
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        String second = service.rotate(raw, "nosso-teto-web").refreshToken();

        OAuthException ex = assertThrows(OAuthException.class, () -> service.rotate(raw, "nosso-teto-web"));
        assertEquals("invalid_grant", ex.getError());

        assertNotNull(repository.findByTokenHash(RefreshTokenService.sha256Hex(second)).orElseThrow().getRevokedAt());
        assertThrows(OAuthException.class, () -> service.rotate(second, "nosso-teto-web"));
    }

    @Test
    void reuseByAnotherClientRevokesTheWholeFamilyAndPersistsRevocation() {
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        String second = service.rotate(raw, "nosso-teto-web").refreshToken();
        RefreshToken storedOriginal = repository.findByTokenHash(RefreshTokenService.sha256Hex(raw)).orElseThrow();
        String familyId = storedOriginal.getFamilyId();

        OAuthException ex = assertThrows(OAuthException.class, () -> service.rotate(raw, "nosso-teto-mobile"));
        assertEquals("invalid_grant", ex.getError());

        Integer totalInFamily = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE family_id = ?",
                Integer.class, familyId);
        assertEquals(2, totalInFamily);
        Integer unrevoked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE family_id = ? AND revoked_at IS NULL",
                Integer.class, familyId);
        assertEquals(0, unrevoked, "revoked_at must be persisted as non-null for the whole family");

        assertNotNull(repository.findByTokenHash(RefreshTokenService.sha256Hex(raw)).orElseThrow().getRevokedAt());
        assertNotNull(repository.findByTokenHash(RefreshTokenService.sha256Hex(second)).orElseThrow().getRevokedAt());
        assertThrows(OAuthException.class, () -> service.rotate(second, "nosso-teto-web"));
    }

    @Test
    void unknownBlankOtherClientAndRevokedAreInvalidGrant() {
        assertThrows(OAuthException.class, () -> service.rotate("nope", "nosso-teto-web"));
        assertThrows(OAuthException.class, () -> service.rotate(null, "nosso-teto-web"));
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        OAuthException otherClientEx = assertThrows(OAuthException.class, () -> service.rotate(raw, "nosso-teto-mobile"));
        assertEquals("invalid_grant", otherClientEx.getError());
        assertNull(repository.findByTokenHash(RefreshTokenService.sha256Hex(raw)).orElseThrow().getRevokedAt());
        assertNotNull(service.rotate(raw, "nosso-teto-web").refreshToken());
        String other = service.issue(newUser(), "nosso-teto-web", "read write", null);
        service.revoke(other);
        assertThrows(OAuthException.class, () -> service.rotate(other, "nosso-teto-web"));
    }

    @Test
    void expiredRefreshTokenIsInvalidGrant() {
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        RefreshToken stored = repository.findByTokenHash(RefreshTokenService.sha256Hex(raw)).orElseThrow();
        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        repository.save(stored);

        OAuthException ex = assertThrows(OAuthException.class, () -> service.rotate(raw, "nosso-teto-web"));
        assertEquals("invalid_grant", ex.getError());
    }

    @Test
    void concurrentRevocationBetweenFindAndMarkUsedFailsAndLeavesNoActiveTokenInFamily() {
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        RefreshToken stored = repository.findByTokenHash(RefreshTokenService.sha256Hex(raw)).orElseThrow();
        String familyId = stored.getFamilyId();

        doAnswer(inv -> {
            LocalDateTime now = inv.getArgument(1);
            repository.revokeFamily(familyId, now);
            return mockingDetails(repository).getMockCreationSettings().getDefaultAnswer().answer(inv);
        }).when(repository).markUsed(eq(stored.getId()), any(LocalDateTime.class));

        OAuthException ex = assertThrows(OAuthException.class, () -> service.rotate(raw, "nosso-teto-web"));
        assertEquals("invalid_grant", ex.getError());

        Integer unrevoked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE family_id = ? AND revoked_at IS NULL",
                Integer.class, familyId);
        assertEquals(0, unrevoked, "no refresh token in the family may remain with revoked_at null");
        Integer totalInFamily = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE family_id = ?",
                Integer.class, familyId);
        assertEquals(1, totalInFamily, "no new refresh token may be issued into a revoked family");
    }

    @Test
    void markUsedReturningZeroThrowsInvalidGrantAndPersistsFamilyRevocation() {
        String first = service.issue(newUser(), "nosso-teto-web", "read write", null);
        String second = service.rotate(first, "nosso-teto-web").refreshToken();
        RefreshToken storedSecond = repository.findByTokenHash(RefreshTokenService.sha256Hex(second)).orElseThrow();
        String familyId = storedSecond.getFamilyId();

        doReturn(0).when(repository).markUsed(eq(storedSecond.getId()), any(LocalDateTime.class));

        OAuthException ex = assertThrows(OAuthException.class, () -> service.rotate(second, "nosso-teto-web"));
        assertEquals("invalid_grant", ex.getError());

        Integer totalInFamily = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE family_id = ?",
                Integer.class, familyId);
        assertEquals(2, totalInFamily);
        Integer unrevoked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE family_id = ? AND revoked_at IS NULL",
                Integer.class, familyId);
        assertEquals(0, unrevoked, "revoked_at must be persisted as non-null for the whole family");
    }

    @Test
    void concurrentRotationOfTheSameTokenSucceedsAtMostOnce() throws Exception {
        String raw = service.issue(newUser(), "nosso-teto-web", "read write", null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger invalidGrant = new AtomicInteger();
        Callable<Void> task = () -> {
            start.await();
            try {
                service.rotate(raw, "nosso-teto-web");
                ok.incrementAndGet();
            } catch (OAuthException e) {
                assertEquals("invalid_grant", e.getError());
                invalidGrant.incrementAndGet();
            }
            return null;
        };
        try {
            Future<Void> a = pool.submit(task);
            Future<Void> b = pool.submit(task);
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        // The first writer never waits on a lock, so exactly one success is deterministic (INV-06).
        assertEquals(1, ok.get(), "exactly one rotation must succeed");
        assertEquals(1, invalidGrant.get(), "losing thread must fail with invalid_grant");
    }

    @Test
    void cookieFactoryHonorsSecureFlagAndHardenedAttributes() {
        SecurityProperties secureProps = new SecurityProperties(
                Duration.ofMinutes(15), Duration.ofDays(7), new SecurityProperties.Cookie(true), List.of());
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(secureProps);

        ResponseCookie created = factory.create("tok-val");
        assertEquals("refresh_token", created.getName());
        assertEquals("tok-val", created.getValue());
        assertTrue(created.isHttpOnly());
        assertTrue(created.isSecure());
        assertEquals("Strict", created.getSameSite());
        assertEquals("/oauth/token", created.getPath());
        assertEquals(Duration.ofDays(7), created.getMaxAge());

        ResponseCookie cleared = factory.clear();
        assertEquals("", cleared.getValue());
        assertTrue(cleared.isHttpOnly());
        assertTrue(cleared.isSecure());
        assertEquals("Strict", cleared.getSameSite());
        assertEquals("/oauth/token", cleared.getPath());
        assertEquals(Duration.ZERO, cleared.getMaxAge());
    }
}
