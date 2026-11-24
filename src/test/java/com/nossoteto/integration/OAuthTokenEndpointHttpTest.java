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
        String query = "grant_type=password&username=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=senha123";
        HttpResponse<String> response = sendToken("/oauth/token?" + query, "");

        assertEquals(400, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("invalid_request", body.get("error").asText());
        assertEquals("Parameters must be sent in the request body", body.get("error_description").asText());
    }

    @Test
    void repeatedGrantTypeInBodyIs400InvalidRequest() throws Exception {
        String form = "grant_type=password&grant_type=password&username="
                + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=senha123";
        HttpResponse<String> response = sendToken("/oauth/token", form);

        assertEquals(400, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("invalid_request", body.get("error").asText());
        assertEquals("Parameter grant_type must not be repeated", body.get("error_description").asText());
    }

    @Test
    void validCredentialsInBodyReturn200() throws Exception {
        String form = "grant_type=password&username=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=senha123";
        HttpResponse<String> response = sendToken("/oauth/token", form);

        assertEquals(200, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("Bearer", body.get("token_type").asText());
        assertTrue(body.hasNonNull("access_token"));
    }

    private HttpResponse<String> sendToken(String path, String formBody) throws Exception {
        String basic = Base64.getEncoder().encodeToString(
                "nosso-teto-web:web-test-secret".getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
