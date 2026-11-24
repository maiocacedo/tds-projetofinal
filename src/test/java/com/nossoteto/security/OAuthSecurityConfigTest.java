package com.nossoteto.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OAuthSecurityConfigTest {

    @Test
    void rejectsSecretShorterThan32BytesAndAccepts32Bytes() {
        assertThrows(IllegalStateException.class, () -> new OAuthSecurityConfig("short"));
        assertThrows(IllegalStateException.class, () -> new OAuthSecurityConfig(""));
        assertThrows(IllegalStateException.class, () -> new OAuthSecurityConfig("x".repeat(31)));
        assertDoesNotThrow(() -> new OAuthSecurityConfig("x".repeat(32)));
    }

    @Test
    void countsSecretLengthInUtf8BytesForMultibyteCharacters() {
        assertDoesNotThrow(() -> new OAuthSecurityConfig("é".repeat(16)));
        assertThrows(IllegalStateException.class, () -> new OAuthSecurityConfig("é".repeat(15) + "x"));
    }

    @Test
    void jwtAuthenticationConverterCombinesScopesAndAuthoritiesAndUsesSubjectAsName() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject("user-1")
                .claim("scope", "read write")
                .claim("authorities", List.of("ROLE_MANAGE_PROFILE"))
                .build();

        AbstractAuthenticationToken auth = new OAuthSecurityConfig("x".repeat(32))
                .jwtAuthenticationConverter()
                .convert(jwt);

        assertNotNull(auth);
        assertEquals("user-1", auth.getName());
        Set<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        assertEquals(3, auth.getAuthorities().size());
        assertEquals(Set.of("SCOPE_read", "SCOPE_write", "ROLE_MANAGE_PROFILE"), authorities);
    }
}
