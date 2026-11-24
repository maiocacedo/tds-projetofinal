package com.nossoteto.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String PATH = "/oauth/token";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshTokenCookieFactory(SecurityProperties properties) {
        this.secure = properties.cookie().secure();
        this.maxAge = properties.refreshTokenTtl();
    }

    public ResponseCookie create(String value) {
        return base(value).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
