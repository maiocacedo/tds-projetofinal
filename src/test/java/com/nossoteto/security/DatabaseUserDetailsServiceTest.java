package com.nossoteto.security;

import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {

    private final UserRepository repo = mock(UserRepository.class);
    private final DatabaseUserDetailsService service = new DatabaseUserDetailsService(repo);

    @Test
    void usernameIsTheUserIdAndAuthoritiesIncludePermissionsAndBothScopes() {
        User u = new User();
        u.setId("user-42");
        u.setEmail("a@b.com");
        u.setPasswordHash("$2a$hash");
        when(repo.findByEmail("a@b.com")).thenReturn(Optional.of(u));
        when(repo.findPermissionNamesByUserId("user-42")).thenReturn(List.of("ROLE_MANAGE_PROFILE"));

        UserDetails details = service.loadUserByUsername("a@b.com");

        assertEquals("user-42", details.getUsername());
        assertEquals("$2a$hash", details.getPassword());
        Set<String> auths = details.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_MANAGE_PROFILE", "SCOPE_read", "SCOPE_write"), auths);
    }

    @Test
    void unknownEmailThrows() {
        when(repo.findByEmail("x@y.com")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("x@y.com"));
    }
}
