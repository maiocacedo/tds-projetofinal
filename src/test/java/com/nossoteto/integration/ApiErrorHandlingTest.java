package com.nossoteto.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs against a real embedded server: MockMvc never forwards to "/error", so it cannot catch
 * Spring Security turning error responses into 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiErrorHandlingTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();
    private String email;
    private String token;

    @BeforeEach
    void registerAndLogin() throws Exception {
        email = "errors-" + UUID.randomUUID() + "@nossoteto.com";
        HttpResponse<String> register = send("POST", "/api/auth/register", null,
                "{\"nome\":\"Erro\",\"email\":\"" + email + "\",\"senha\":\"senha123\"}");
        assertEquals(201, register.statusCode());

        HttpResponse<String> login = send("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"senha\":\"senha123\"}");
        assertEquals(200, login.statusCode());
        token = objectMapper.readTree(login.body()).get("token").asText();
    }

    @Test
    void registerResponseShouldNotExposePasswordHash() throws Exception {
        HttpResponse<String> response = send("POST", "/api/auth/register", null,
                "{\"nome\":\"Hash\",\"email\":\"hash-" + UUID.randomUUID() + "@nossoteto.com\",\"senha\":\"senha123\"}");

        assertEquals(201, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.hasNonNull("id"));
        assertFalse(body.has("passwordHash"));
    }

    @Test
    void protectedRouteWithoutTokenShouldReturn401() throws Exception {
        HttpResponse<String> response = send("GET", "/api/users/me", null, null);

        assertEquals(401, response.statusCode());
        assertEquals("Authentication required", objectMapper.readTree(response.body()).get("error").asText());
    }

    @Test
    void loginWithWrongPasswordShouldReturn401() throws Exception {
        HttpResponse<String> response = send("POST", "/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"senha\":\"errada\"}");

        assertEquals(401, response.statusCode());
    }

    @Test
    void malformedJsonShouldReturn400InsteadOf403() throws Exception {
        HttpResponse<String> response = send("POST", "/api/houses", token, "{\"nome\": ");

        assertEquals(400, response.statusCode());
    }

    @Test
    void invalidEnumValueShouldReturn400WithFieldName() throws Exception {
        HttpResponse<String> response = send("POST", "/api/houses", token,
                "{\"nome\":\"Casa\",\"regraVotacao\":\"FOO\"}");

        assertEquals(400, response.statusCode());
        String error = objectMapper.readTree(response.body()).get("error").asText();
        assertTrue(error.contains("FOO") && error.contains("regraVotacao"), error);
    }

    @Test
    void invalidQueryParamShouldReturn400() throws Exception {
        HttpResponse<String> response = send("GET", "/api/houses?maxRent=abc", token, null);

        assertEquals(400, response.statusCode());
    }

    @Test
    void unsupportedMethodShouldReturn405InsteadOf403() throws Exception {
        HttpResponse<String> response = send("DELETE", "/api/users/me", token, null);

        assertEquals(405, response.statusCode());
    }

    private HttpResponse<String> send(String method, String path, String bearer, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, json == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            builder.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
