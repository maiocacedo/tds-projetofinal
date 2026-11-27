package com.nossoteto.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/oauth/token")
@Profile("!basic-security")
public class TokenController {

    private final ClientAuthenticator clientAuthenticator;
    private final OAuthTokenService tokenService;
    private final RefreshTokenCookieFactory cookieFactory;

    public TokenController(ClientAuthenticator clientAuthenticator, OAuthTokenService tokenService,
                           RefreshTokenCookieFactory cookieFactory) {
        this.clientAuthenticator = clientAuthenticator;
        this.tokenService = tokenService;
        this.cookieFactory = cookieFactory;
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam MultiValueMap<String, String> form,
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshCookie,
            HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            throw OAuthException.invalidRequest("Parameters must be sent in the request body");
        }
        String bodyClientId = singleParam(form, "client_id");
        SecurityProperties.Client client = clientAuthenticator.authenticate(authorization, bodyClientId);
        String grantType = singleParam(form, "grant_type");
        String username = singleParam(form, "username");
        String password = singleParam(form, "password");
        String scope = singleParam(form, "scope");
        OAuthTokenService.TokenResult result = switch (grantType == null ? "" : grantType) {
            case "password" -> tokenService.passwordGrant(client, username, password, scope);
            case "refresh_token" -> tokenService.refreshGrant(client, refreshCookie);
            case "" -> throw OAuthException.invalidRequest("grant_type is required");
            default -> throw OAuthException.unsupportedGrantType();
        };
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(result.refreshToken()).toString())
                .body(body(result));
    }

    @DeleteMapping("/revoke")
    public ResponseEntity<Void> revoke(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) MultiValueMap<String, String> form,
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshCookie,
            HttpServletRequest request) {
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            throw OAuthException.invalidRequest("Query parameters are not allowed");
        }
        String bodyClientId = singleParam(form, "client_id");
        SecurityProperties.Client client = clientAuthenticator.authenticate(authorization, bodyClientId);
        tokenService.revoke(client, refreshCookie);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    private static String singleParam(MultiValueMap<String, String> form, String name) {
        if (form == null) {
            return null;
        }
        List<String> values = form.get(name);
        if (values != null && values.size() > 1) {
            throw OAuthException.invalidRequest("Parameter " + name + " must not be repeated");
        }
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    static Map<String, Object> body(OAuthTokenService.TokenResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", result.accessToken().value());
        body.put("token_type", "Bearer");
        body.put("expires_in", result.accessToken().expiresInSeconds());
        body.put("scope", result.accessToken().scope());
        body.put("user_id", result.userId());
        return body;
    }

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<Map<String, String>> handle(OAuthException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus()).cacheControl(CacheControl.noStore());
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (ex.getStatus().value() == 401 && authorization != null && !authorization.isBlank()) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"nosso-teto\"");
        }
        return builder.body(Map.of("error", ex.getError(), "error_description", ex.getMessage()));
    }
}
