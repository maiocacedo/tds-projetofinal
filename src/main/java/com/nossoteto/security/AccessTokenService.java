package com.nossoteto.security;

import com.nossoteto.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
@Profile("!basic-security")
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository;
    private final Duration ttl;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, UserRepository userRepository,
                              SecurityProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.userRepository = userRepository;
        this.ttl = properties.accessTokenTtl();
        this.clock = clock;
    }

    public IssuedAccessToken issue(String userId, String clientId, Set<String> scopes) {
        // Permissions are re-read from the database on every issuance (INV-05).
        List<String> authorities = userRepository.findPermissionNamesByUserId(userId);
        String scope = String.join(" ", new TreeSet<>(scopes));
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("scope", scope)
                .claim("authorities", authorities)
                .claim("client_id", clientId)
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedAccessToken(value, ttl.toSeconds(), scope);
    }

    public record IssuedAccessToken(String value, long expiresInSeconds, String scope) {}
}
