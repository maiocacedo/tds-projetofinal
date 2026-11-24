package com.nossoteto.security;

import org.springframework.http.HttpStatus;

public class OAuthException extends RuntimeException {

    private final String error;
    private final HttpStatus status;

    public OAuthException(String error, String description, HttpStatus status) {
        super(description);
        this.error = error;
        this.status = status;
    }

    public static OAuthException invalidClient() {
        return new OAuthException("invalid_client", "Client authentication failed", HttpStatus.UNAUTHORIZED);
    }

    public static OAuthException invalidGrant(String description) {
        return new OAuthException("invalid_grant", description, HttpStatus.BAD_REQUEST);
    }

    public static OAuthException invalidScope() {
        return new OAuthException("invalid_scope", "Requested scope is not allowed for this client", HttpStatus.BAD_REQUEST);
    }

    public static OAuthException unsupportedGrantType() {
        return new OAuthException("unsupported_grant_type", "Grant type not supported", HttpStatus.BAD_REQUEST);
    }

    public static OAuthException invalidRequest(String description) {
        return new OAuthException("invalid_request", description, HttpStatus.BAD_REQUEST);
    }

    public String getError() { return error; }
    public HttpStatus getStatus() { return status; }
}
