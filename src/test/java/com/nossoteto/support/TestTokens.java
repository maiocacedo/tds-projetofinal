package com.nossoteto.support;

import com.nossoteto.model.User;
import com.nossoteto.security.Permissions;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class TestTokens {

    private TestTokens() {}

    public static RequestPostProcessor jwtForUser(String userId) {
        List<String> authorities = new ArrayList<>(Permissions.DEFAULT_USER);
        authorities.add("SCOPE_read");
        authorities.add("SCOPE_write");
        return jwtWith(userId, authorities);
    }

    public static RequestPostProcessor jwtForAdmin(String userId) {
        List<String> authorities = new ArrayList<>(Permissions.DEFAULT_USER);
        authorities.add(Permissions.ADMIN_USERS);
        authorities.add("SCOPE_read");
        authorities.add("SCOPE_write");
        return jwtWith(userId, authorities);
    }

    public static RequestPostProcessor jwtFor(User user) {
        return jwtForUser(user.getId());
    }

    public static RequestPostProcessor jwtFor(String userId, String... authorities) {
        return jwtWith(userId, authorities);
    }

    public static RequestPostProcessor jwtWith(String userId, String... authorities) {
        return jwtWith(userId, Arrays.asList(authorities));
    }

    private static RequestPostProcessor jwtWith(String userId, Collection<String> authorities) {
        List<GrantedAuthority> granted = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        return jwt()
                .jwt(j -> j.subject(userId))
                .authorities(granted);
    }
}
