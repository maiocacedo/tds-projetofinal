package com.nossoteto.security;

import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Profile("basic-security")
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        List<GrantedAuthority> authorities = new ArrayList<>();
        userRepository.findPermissionNamesByUserId(user.getId())
                .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        // Basic auth has no OAuth client, so it carries full scope.
        authorities.add(new SimpleGrantedAuthority("SCOPE_read"));
        authorities.add(new SimpleGrantedAuthority("SCOPE_write"));
        // username = user id, because every service keys on the user id (INV-11/INV-12).
        return org.springframework.security.core.userdetails.User.withUsername(user.getId())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .build();
    }
}
