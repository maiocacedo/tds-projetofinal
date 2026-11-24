package com.nossoteto.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @DefaultValue("PT15M") Duration accessTokenTtl,
        @DefaultValue("P7D") Duration refreshTokenTtl,
        @DefaultValue Cookie cookie,
        List<Client> clients) {

    public record Cookie(@DefaultValue("false") boolean secure) {}

    public record Client(String clientId, String clientSecret, Set<String> scopes) {
        @Override
        public String toString() {
            return "Client[clientId=" + clientId + ", clientSecret=******, scopes=" + scopes + "]";
        }
    }
}
