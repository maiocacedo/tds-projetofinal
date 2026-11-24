package com.nossoteto.security;

import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
@Profile("!basic-security")
public class OAuthTokenService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final String dummyHash;

    public OAuthTokenService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                             AccessTokenService accessTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.dummyHash = passwordEncoder.encode("timing-equalizer");
    }

    @Transactional
    public TokenResult passwordGrant(SecurityProperties.Client client, String username, String password,
                                     String requestedScope) {
        if (username == null || username.isBlank() || password == null) {
            throw OAuthException.invalidRequest("username and password are required");
        }
        Set<String> scopes = resolveScopes(client, requestedScope);
        Optional<User> user = userRepository.findByEmail(username);
        // Always run BCrypt so unknown e-mails and wrong passwords take the same time.
        boolean matches = passwordEncoder.matches(password, user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            throw OAuthException.invalidGrant("Invalid credentials");
        }
        String userId = user.get().getId();
        return new TokenResult(accessTokenService.issue(userId, client.clientId(), scopes), userId, null);
    }

    static Set<String> resolveScopes(SecurityProperties.Client client, String requested) {
        if (requested == null || requested.isBlank()) {
            return new TreeSet<>(client.scopes());
        }
        Set<String> scopes = new TreeSet<>(Arrays.asList(requested.trim().split("\\s+")));
        if (!client.scopes().containsAll(scopes)) {
            throw OAuthException.invalidScope();
        }
        return scopes;
    }

    public record TokenResult(AccessTokenService.IssuedAccessToken accessToken, String userId, String refreshToken) {}
}
