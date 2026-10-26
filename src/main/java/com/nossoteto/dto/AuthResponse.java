package com.nossoteto.dto;

public class AuthResponse {
    private String token;
    private String userId;
    private Integer expiresIn = 3600;

    public AuthResponse() {}

    public AuthResponse(String token, String userId) {
        this.token = token;
        this.userId = userId;
        this.expiresIn = 3600;
    }

    public AuthResponse(String token, String userId, Integer expiresIn) {
        this.token = token;
        this.userId = userId;
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }
}
