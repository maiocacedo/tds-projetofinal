package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OAuthTokenEndpointHttpTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();
    private String email;

    @BeforeEach
    void registerUser() throws Exception {
        email = "token-http-" + UUID.randomUUID() + "@nossoteto.com";
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"nome\":\"TokenHttp\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}"))
                .build();
        HttpResponse<String> register = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, register.statusCode());
    }

    @Test
    void credentialsOnlyInQueryStringWithEmptyBodyAre400InvalidRequest() throws Exception {
        String query = "client_id=nosso-teto-web&grant_type=password&username="
                + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=senha123";
        HttpResponse<String> response = sendPublicToken("/oauth/token?" + query, "", null);

        assertEquals(400, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("invalid_request", body.get("error").asText());
        assertEquals("Parameters must be sent in the request body", body.get("error_description").asText());
    }

    @Test
    void revokeWithQueryStringIs400InvalidRequestBeforeClientAuth() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/oauth/token/revoke?client_id=nosso-teto-web"))
                .DELETE()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("invalid_request", body.get("error").asText());
        assertEquals("Query parameters are not allowed", body.get("error_description").asText());
    }

    @Test
    void repeatedGrantTypeInBodyIs400InvalidRequest() throws Exception {
        String form = "client_id=nosso-teto-web&grant_type=password&grant_type=password&username="
                + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=senha123";
        HttpResponse<String> response = sendPublicToken("/oauth/token", form, null);

        assertEquals(400, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("invalid_request", body.get("error").asText());
        assertEquals("Parameter grant_type must not be repeated", body.get("error_description").asText());
    }

    @Test
    void repeatedClientIdInBodyIs400InvalidRequest() throws Exception {
        String form = "client_id=nosso-teto-web&client_id=nosso-teto-web&grant_type=password&username="
                + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=senha123";
        HttpResponse<String> response = sendPublicToken("/oauth/token", form, null);

        assertEquals(400, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("invalid_request", body.get("error").asText());
        assertEquals("Parameter client_id must not be repeated", body.get("error_description").asText());
    }

    @Test
    void publicClientPasswordRefreshAndRevokeFlowOverRealHttp() throws Exception {
        String passwordForm = "client_id=nosso-teto-web&grant_type=password&username="
                + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=senha123";
        HttpResponse<String> loginResponse = sendPublicToken("/oauth/token", passwordForm, null);

        assertEquals(200, loginResponse.statusCode());
        JsonNode loginBody = objectMapper.readTree(loginResponse.body());
        assertEquals("Bearer", loginBody.get("token_type").asText());
        assertEquals("read write", loginBody.get("scope").asText());
        assertTrue(loginBody.hasNonNull("access_token"));
        String firstCookie = extractRefreshCookiePair(loginResponse);
        assertNotNull(firstCookie);

        HttpResponse<String> refreshResponse = sendPublicToken(
                "/oauth/token", "client_id=nosso-teto-web&grant_type=refresh_token", firstCookie);
        assertEquals(200, refreshResponse.statusCode());
        JsonNode refreshBody = objectMapper.readTree(refreshResponse.body());
        assertTrue(refreshBody.hasNonNull("access_token"));
        String secondCookie = extractRefreshCookiePair(refreshResponse);
        assertNotNull(secondCookie);
        assertNotEquals(firstCookie, secondCookie);

        HttpResponse<String> revokeResponse = sendPublicRevoke("client_id=nosso-teto-web", secondCookie);
        assertEquals(204, revokeResponse.statusCode());
        String clearSetCookie = revokeResponse.headers().firstValue("Set-Cookie").orElse("");
        assertTrue(clearSetCookie.contains("Max-Age=0"), clearSetCookie);

        HttpResponse<String> afterRevoke = sendPublicToken(
                "/oauth/token", "client_id=nosso-teto-web&grant_type=refresh_token", secondCookie);
        assertEquals(400, afterRevoke.statusCode());
        assertEquals("invalid_grant", objectMapper.readTree(afterRevoke.body()).get("error").asText());
    }

    @Test
    void basicHeaderAndBodyClientIdTogetherReturn400InvalidRequest() throws Exception {
        String tokenForm = "client_id=nosso-teto-web&grant_type=password&username="
                + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=senha123";
        HttpResponse<String> tokenResponse = sendBasicToken(
                "nosso-teto-mobile", "mobile-test-secret", "/oauth/token", tokenForm);
        assertEquals(400, tokenResponse.statusCode());
        assertEquals("invalid_request", objectMapper.readTree(tokenResponse.body()).get("error").asText());

        String basic = basicAuth("nosso-teto-mobile", "mobile-test-secret");
        HttpRequest revokeRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth/token/revoke"))
                .header("Authorization", basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .method("DELETE", HttpRequest.BodyPublishers.ofString("client_id=nosso-teto-web"))
                .build();
        HttpResponse<String> revokeResponse = http.send(revokeRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, revokeResponse.statusCode());
        assertEquals("invalid_request", objectMapper.readTree(revokeResponse.body()).get("error").asText());
    }

    @Test
    void unauthorizedWithoutAuthorizationHeaderOmitsWwwAuthenticate() throws Exception {
        String unknownForm = "client_id=ghost-client&grant_type=password&username="
                + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=senha123";
        HttpResponse<String> unknownResponse = sendPublicToken("/oauth/token", unknownForm, null);
        assertEquals(401, unknownResponse.statusCode());
        assertEquals("invalid_client", objectMapper.readTree(unknownResponse.body()).get("error").asText());
        assertTrue(unknownResponse.headers().firstValue("WWW-Authenticate").isEmpty());

        String confidentialInBody = "client_id=nosso-teto-mobile&grant_type=password&username="
                + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=senha123";
        HttpResponse<String> confidentialResponse = sendPublicToken("/oauth/token", confidentialInBody, null);
        assertEquals(401, confidentialResponse.statusCode());
        assertEquals("invalid_client", objectMapper.readTree(confidentialResponse.body()).get("error").asText());
        assertTrue(confidentialResponse.headers().firstValue("WWW-Authenticate").isEmpty());

        HttpResponse<String> revokeNoAuth = sendPublicRevoke("", null);
        assertEquals(401, revokeNoAuth.statusCode());
        assertEquals("invalid_client", objectMapper.readTree(revokeNoAuth.body()).get("error").asText());
        assertTrue(revokeNoAuth.headers().firstValue("WWW-Authenticate").isEmpty());
    }

    @Test
    void confidentialClientWithBasicWorksAndInvalidBasicEmitsWwwAuthenticate() throws Exception {
        String form = "grant_type=password&username=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=senha123";
        HttpResponse<String> ok = sendBasicToken("nosso-teto-mobile", "mobile-test-secret", "/oauth/token", form);
        assertEquals(200, ok.statusCode());
        assertEquals("read", objectMapper.readTree(ok.body()).get("scope").asText());

        HttpResponse<String> wrongSecret = sendBasicToken("nosso-teto-mobile", "wrong", "/oauth/token", form);
        assertEquals(401, wrongSecret.statusCode());
        assertEquals("invalid_client", objectMapper.readTree(wrongSecret.body()).get("error").asText());
        assertEquals("Basic realm=\"nosso-teto\"", wrongSecret.headers().firstValue("WWW-Authenticate").orElse(null));

        HttpResponse<String> publicWithBasic = sendBasicToken("nosso-teto-web", "any", "/oauth/token", form);
        assertEquals(401, publicWithBasic.statusCode());
        assertEquals("invalid_client", objectMapper.readTree(publicWithBasic.body()).get("error").asText());
        assertEquals("Basic realm=\"nosso-teto\"", publicWithBasic.headers().firstValue("WWW-Authenticate").orElse(null));
    }

    private static String basicAuth(String clientId, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    private static String extractRefreshCookiePair(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(v -> v.startsWith("refresh_token="))
                .map(v -> v.split(";", 2)[0])
                .findFirst()
                .orElse(null);
    }

    private HttpResponse<String> sendPublicToken(String path, String formBody, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendPublicRevoke(String formBody, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth/token/revoke"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(formBody));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendBasicToken(String clientId, String secret, String path, String formBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", basicAuth(clientId, secret))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
