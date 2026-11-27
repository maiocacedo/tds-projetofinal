package com.nossoteto.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAuthenticatorTest {

    private final ClientAuthenticator authenticator = new ClientAuthenticator(props(List.of(
            new SecurityProperties.Client("mobile", "s3cret", Set.of("read"), false),
            new SecurityProperties.Client("web", null, Set.of("read", "write"), true))));

    private static SecurityProperties props(List<SecurityProperties.Client> clients) {
        return new SecurityProperties(
                Duration.ofMinutes(15), Duration.ofDays(7), new SecurityProperties.Cookie(false), clients);
    }

    private static String basic(String raw) {
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validConfidentialCredentialsReturnTheClient() {
        assertEquals("mobile", authenticator.authenticate(basic("mobile:s3cret"), null).clientId());
    }

    @Test
    void validPublicClientInBodyReturnsTheClient() {
        assertEquals("web", authenticator.authenticate(null, "web").clientId());
        assertEquals("web", authenticator.authenticate("   ", "web").clientId());
    }

    @Test
    void wrongSecretIsInvalidClient() {
        OAuthException ex = assertThrows(OAuthException.class,
                () -> authenticator.authenticate(basic("mobile:nope"), null));
        assertEquals("invalid_client", ex.getError());
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    void publicClientWithBasicAuthIsInvalidClient() {
        OAuthException withSecret = assertThrows(OAuthException.class,
                () -> authenticator.authenticate(basic("web:anything"), null));
        assertEquals("invalid_client", withSecret.getError());

        OAuthException withEmptySecret = assertThrows(OAuthException.class,
                () -> authenticator.authenticate(basic("web:"), null));
        assertEquals("invalid_client", withEmptySecret.getError());
    }

    @Test
    void confidentialClientWithoutBasicAuthIsInvalidClient() {
        OAuthException ex = assertThrows(OAuthException.class,
                () -> authenticator.authenticate(null, "mobile"));
        assertEquals("invalid_client", ex.getError());
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    void bothAuthorizationHeaderAndBodyClientIdReturn400InvalidRequest() {
        OAuthException ex1 = assertThrows(OAuthException.class,
                () -> authenticator.authenticate(basic("mobile:s3cret"), "web"));
        assertEquals("invalid_request", ex1.getError());
        assertEquals(400, ex1.getStatus().value());

        OAuthException ex2 = assertThrows(OAuthException.class,
                () -> authenticator.authenticate(basic("mobile:s3cret"), "mobile"));
        assertEquals("invalid_request", ex2.getError());
        assertEquals(400, ex2.getStatus().value());
    }

    @Test
    void unknownClientMissingAuthMalformedBase64AndNoColonAreInvalidClient() {
        assertThrows(OAuthException.class, () -> authenticator.authenticate(basic("ghost:s3cret"), null));
        assertThrows(OAuthException.class, () -> authenticator.authenticate(null, "ghost"));
        assertThrows(OAuthException.class, () -> authenticator.authenticate(null, null));
        assertThrows(OAuthException.class, () -> authenticator.authenticate("  ", "  "));
        assertThrows(OAuthException.class, () -> authenticator.authenticate("Basic %%%", null));
        assertThrows(OAuthException.class, () -> authenticator.authenticate(basic("mobilesecret"), null));
        assertThrows(OAuthException.class, () -> authenticator.authenticate("Bearer abc", null));
    }

    @Test
    void rejectsEmptyOrBlankClientSecretForConfidentialClient() {
        IllegalStateException emptyEx = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("mobile", "", Set.of("read"), false)))));
        assertTrue(emptyEx.getMessage().contains("mobile"));

        IllegalStateException blankEx = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("mobile", "   ", Set.of("read"), false)))));
        assertTrue(blankEx.getMessage().contains("mobile"));
    }

    @Test
    void rejectsNullClientSecretForConfidentialClient() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("mobile", null, Set.of("read"), false)))));
        assertTrue(ex.getMessage().contains("mobile"));
    }

    @Test
    void rejectsNonBlankClientSecretForPublicClientAndAllowsNullOrBlank() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("web", "leaked-secret", Set.of("read"), true)))));
        assertTrue(ex.getMessage().contains("web"));
        assertFalse(ex.getMessage().contains("leaked-secret"));

        ClientAuthenticator withNullSecret = new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("web", null, Set.of("read"), true))));
        assertEquals("web", withNullSecret.authenticate(null, "web").clientId());

        ClientAuthenticator withBlankSecret = new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("web", "   ", Set.of("read"), true))));
        assertEquals("web", withBlankSecret.authenticate(null, "web").clientId());
    }

    @Test
    void rejectsEmptyOrNullScopes() {
        IllegalStateException emptyEx = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("mobile", "s3cret", Set.of(), false)))));
        assertTrue(emptyEx.getMessage().contains("mobile"));

        IllegalStateException nullEx = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("mobile", "s3cret", null, false)))));
        assertTrue(nullEx.getMessage().contains("mobile"));
    }

    @Test
    void rejectsBlankOrNullClientId() {
        assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("  ", "s3cret", Set.of("read"), false)))));
        assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client(null, "s3cret", Set.of("read"), false)))));
    }

    @Test
    void rejectsEmptyOrNullClientList() {
        assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(props(List.of())));
        assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(props(null)));
    }

    @Test
    void rejectsDuplicateClientIdWithoutLeakingSecrets() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(
                        new SecurityProperties.Client("web", "s3cret-A", Set.of("read"), false),
                        new SecurityProperties.Client("web", "s3cret-B", Set.of("write"), false)))));
        assertTrue(ex.getMessage().contains("web"));
        assertFalse(ex.getMessage().contains("s3cret-A"));
        assertFalse(ex.getMessage().contains("s3cret-B"));
        assertEquals("Duplicate OAuth client id: web", ex.getMessage());
    }

    @Test
    void clientToStringMasksClientSecret() {
        String rendered = new SecurityProperties.Client("mobile", "s3cret-A", Set.of("read"), false).toString();
        assertFalse(rendered.contains("s3cret-A"));
        assertTrue(rendered.contains("mobile"));
        assertEquals("Client[clientId=mobile, clientSecret=******, scopes=[read], publicClient=false]", rendered);

        String publicRendered = new SecurityProperties.Client("web", null, Set.of("read"), true).toString();
        assertEquals("Client[clientId=web, clientSecret=******, scopes=[read], publicClient=true]", publicRendered);
    }
}
