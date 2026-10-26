package com.nossoteto.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET = "NossoTetoTestSecretKeyForJwtGenerationNeedsToBeVeryLong256BitsAtLeast";

    @Test
    void tokenLifetimeShouldMatchConfiguredExpiration() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);

        String token = provider.generateToken("user-1");
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();

        long lifetimeSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertEquals(60, lifetimeSeconds);
        assertEquals(60, provider.getExpirationSeconds());
        assertEquals("user-1", provider.getUserIdFromToken(token));
    }

    @Test
    void tokenSignedWithAnotherSecretShouldBeRejected() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);
        JwtTokenProvider other = new JwtTokenProvider(SECRET + "-different", 60_000);

        assertTrue(provider.validateToken(provider.generateToken("user-1")));
        assertFalse(provider.validateToken(other.generateToken("user-1")));
    }

    @Test
    void secretShorterThan256BitsShouldFailFast() {
        assertThrows(WeakKeyException.class, () -> new JwtTokenProvider("short-secret", 60_000));
    }
}
