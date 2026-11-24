package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OAuthPasswordFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private JwtEncoder jwtEncoder;

    private String email;
    private String userId;

    @BeforeEach
    void register() throws Exception {
        email = "oauth-" + UUID.randomUUID() + "@nossoteto.com";
        MvcResult r = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"OAuth\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isCreated()).andReturn();
        userId = objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions token(String client, String secret, String... params) throws Exception {
        var req = post("/oauth/token").with(httpBasic(client, secret)).contentType(MediaType.APPLICATION_FORM_URLENCODED);
        for (int i = 0; i < params.length; i += 2) req.param(params[i], params[i + 1]);
        return mockMvc.perform(req);
    }

    @Test
    void passwordGrantReturnsBearerJwtWithScopesAndPermissions() throws Exception {
        MvcResult r = token("nosso-teto-web", "web-test-secret",
                "grant_type", "password", "username", email, "password", "senha123")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.scope").value("read write"))
                .andExpect(jsonPath("$.user_id").value(userId))
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(r.getResponse().getContentAsString());
        Jwt jwt = jwtDecoder.decode(body.get("access_token").asText());
        assertEquals(userId, jwt.getSubject());
        assertEquals("read write", jwt.getClaimAsString("scope"));
        assertEquals("nosso-teto-web", jwt.getClaimAsString("client_id"));
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        assertTrue(authorities.contains("ROLE_MANAGE_PROFILE"));
        assertFalse(authorities.contains("ROLE_ADMIN_USERS"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + body.get("access_token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));
    }

    @Test
    void wrongClientSecretIs401InvalidClientWithChallenge() throws Exception {
        token("nosso-teto-web", "wrong", "grant_type", "password", "username", email, "password", "senha123")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"nosso-teto\""))
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void missingClientAuthenticationIs401() throws Exception {
        mockMvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password").param("username", email).param("password", "senha123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void wrongPasswordAndUnknownUserAre400InvalidGrant() throws Exception {
        token("nosso-teto-web", "web-test-secret", "grant_type", "password", "username", email, "password", "errada")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
        token("nosso-teto-web", "web-test-secret", "grant_type", "password", "username", "ghost@x.com", "password", "senha123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void unsupportedAndMissingGrantType() throws Exception {
        token("nosso-teto-web", "web-test-secret", "grant_type", "client_credentials")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("unsupported_grant_type"));
        token("nosso-teto-web", "web-test-secret")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void mobileClientCannotRequestWriteScopeAndDefaultsToRead() throws Exception {
        token("nosso-teto-mobile", "mobile-test-secret",
                "grant_type", "password", "username", email, "password", "senha123", "scope", "read write")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_scope"));
        token("nosso-teto-mobile", "mobile-test-secret",
                "grant_type", "password", "username", email, "password", "senha123")
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("read"));
    }

    @Test
    void jwtSignedWithAnotherKeyIs401() throws Exception {
        JwtEncoder foreignEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("OutraChaveCompletamenteDiferenteCom32+Bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim("scope", "read write")
                .claim("authorities", List.of("ROLE_MANAGE_PROFILE"))
                .build();
        String forged = foreignEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", Matchers.startsWith("Bearer")))
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void expiredJwtIs401() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .issuedAt(now.minus(Duration.ofHours(2)))
                .expiresAt(now.minus(Duration.ofHours(1)))
                .claim("scope", "read write")
                .claim("authorities", List.of("ROLE_MANAGE_PROFILE"))
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void malformedBearerIs401() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void legacyLoginRouteIsGoneEvenWithValidToken() throws Exception {
        MvcResult tokenResult = token("nosso-teto-web", "web-test-secret",
                "grant_type", "password", "username", email, "password", "senha123")
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("access_token").asText();

        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .andExpect(status().isNotFound());
    }
}
