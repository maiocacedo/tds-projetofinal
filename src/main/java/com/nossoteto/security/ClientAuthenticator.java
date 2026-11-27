package com.nossoteto.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Profile("!basic-security")
public class ClientAuthenticator {

    private final Map<String, SecurityProperties.Client> clients;

    public ClientAuthenticator(SecurityProperties properties) {
        List<SecurityProperties.Client> configured = properties != null ? properties.clients() : null;
        if (configured == null || configured.isEmpty()) {
            throw new IllegalStateException("app.security.clients must contain at least one OAuth client");
        }
        Set<String> seenIds = new HashSet<>();
        for (SecurityProperties.Client client : configured) {
            if (client == null) {
                throw new IllegalStateException("OAuth client configuration entry must not be null");
            }
            if (client.clientId() == null || client.clientId().isBlank()) {
                throw new IllegalStateException("OAuth client '" + client.clientId() + "' must define a non-blank clientId");
            }
            if (!seenIds.add(client.clientId())) {
                throw new IllegalStateException("Duplicate OAuth client id: " + client.clientId());
            }
            if (client.publicClient()) {
                if (client.clientSecret() != null && !client.clientSecret().isBlank()) {
                    throw new IllegalStateException("Public OAuth client '" + client.clientId() + "' must not define a clientSecret");
                }
            } else {
                if (client.clientSecret() == null || client.clientSecret().isBlank()) {
                    throw new IllegalStateException("OAuth client '" + client.clientId() + "' must define a non-blank clientSecret");
                }
            }
            if (client.scopes() == null || client.scopes().isEmpty()
                    || client.scopes().stream().anyMatch(s -> s == null || s.isBlank())) {
                throw new IllegalStateException("OAuth client '" + client.clientId() + "' must define at least one non-blank scope");
            }
        }
        this.clients = configured.stream()
                .collect(Collectors.toUnmodifiableMap(SecurityProperties.Client::clientId, Function.identity()));
    }

    public SecurityProperties.Client authenticate(String authorizationHeader, String bodyClientId) {
        boolean hasAuthHeader = authorizationHeader != null && !authorizationHeader.isBlank();
        boolean hasBodyClientId = bodyClientId != null && !bodyClientId.isBlank();

        if (hasAuthHeader && hasBodyClientId) {
            throw OAuthException.invalidRequest("Client must not use more than one authentication method");
        }
        if (hasBodyClientId) {
            SecurityProperties.Client client = clients.get(bodyClientId);
            if (client == null || !client.publicClient()) {
                throw OAuthException.invalidClient();
            }
            return client;
        }
        if (hasAuthHeader) {
            return authenticateBasic(authorizationHeader);
        }
        throw OAuthException.invalidClient();
    }

    private SecurityProperties.Client authenticateBasic(String authorizationHeader) {
        if (!authorizationHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            throw OAuthException.invalidClient();
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorizationHeader.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw OAuthException.invalidClient();
        }
        int separator = decoded.indexOf(':');
        if (separator <= 0) {
            throw OAuthException.invalidClient();
        }
        SecurityProperties.Client client = clients.get(decoded.substring(0, separator));
        String presented = decoded.substring(separator + 1);
        String expected = (client != null && !client.publicClient() && client.clientSecret() != null)
                ? client.clientSecret()
                : "";
        boolean secretMatches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
        if (client == null || client.publicClient() || !secretMatches) {
            throw OAuthException.invalidClient();
        }
        return client;
    }
}
