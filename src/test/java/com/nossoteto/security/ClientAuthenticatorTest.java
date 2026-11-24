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

    private final ClientAuthenticator authenticator = new ClientAuthenticator(props(
            List.of(new SecurityProperties.Client("web", "s3cret", Set.of("read", "write")))));

    private static SecurityProperties props(List<SecurityProperties.Client> clients) {
        return new SecurityProperties(
                Duration.ofMinutes(15), Duration.ofDays(7), new SecurityProperties.Cookie(false), clients);
    }

    private static String basic(String raw) {
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validCredentialsReturnTheClient() {
        assertEquals("web", authenticator.authenticate(basic("web:s3cret")).clientId());
    }

    @Test
    void wrongSecretIsInvalidClient() {
        OAuthException ex = assertThrows(OAuthException.class, () -> authenticator.authenticate(basic("web:nope")));
        assertEquals("invalid_client", ex.getError());
    }

    @Test
    void unknownClientMissingHeaderMalformedBase64AndNoColonAreInvalidClient() {
        assertThrows(OAuthException.class, () -> authenticator.authenticate(basic("ghost:s3cret")));
        assertThrows(OAuthException.class, () -> authenticator.authenticate(null));
        assertThrows(OAuthException.class, () -> authenticator.authenticate("Basic %%%"));
        assertThrows(OAuthException.class, () -> authenticator.authenticate(basic("websecret")));
        assertThrows(OAuthException.class, () -> authenticator.authenticate("Bearer abc"));
    }

    @Test
    void rejectsEmptyOrBlankClientSecret() {
        IllegalStateException emptyEx = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("web", "", Set.of("read"))))));
        assertTrue(emptyEx.getMessage().contains("web"));

        IllegalStateException blankEx = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("web", "   ", Set.of("read"))))));
        assertTrue(blankEx.getMessage().contains("web"));
    }

    @Test
    void rejectsNullClientSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("web", null, Set.of("read"))))));
        assertTrue(ex.getMessage().contains("web"));
    }

    @Test
    void rejectsEmptyOrNullScopes() {
        IllegalStateException emptyEx = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("web", "s3cret", Set.of())))));
        assertTrue(emptyEx.getMessage().contains("web"));

        IllegalStateException nullEx = assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("web", "s3cret", null)))));
        assertTrue(nullEx.getMessage().contains("web"));
    }

    @Test
    void rejectsBlankOrNullClientId() {
        assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client("  ", "s3cret", Set.of("read"))))));
        assertThrows(IllegalStateException.class, () -> new ClientAuthenticator(
                props(List.of(new SecurityProperties.Client(null, "s3cret", Set.of("read"))))));
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
                        new SecurityProperties.Client("web", "s3cret-A", Set.of("read")),
                        new SecurityProperties.Client("web", "s3cret-B", Set.of("write"))))));
        assertTrue(ex.getMessage().contains("web"));
        assertFalse(ex.getMessage().contains("s3cret-A"));
        assertFalse(ex.getMessage().contains("s3cret-B"));
        assertEquals("Duplicate OAuth client id: web", ex.getMessage());
    }

    @Test
    void clientToStringMasksClientSecret() {
        String rendered = new SecurityProperties.Client("web", "s3cret-A", Set.of("read")).toString();
        assertFalse(rendered.contains("s3cret-A"));
        assertTrue(rendered.contains("web"));
        assertEquals("Client[clientId=web, clientSecret=******, scopes=[read]]", rendered);
    }
}
