package com.nossoteto.service;

import com.nossoteto.dto.LoginRequest;
import com.nossoteto.dto.RegisterRequest;
import com.nossoteto.dto.AuthResponse;
import com.nossoteto.model.User;
import com.nossoteto.repository.UserRepository;
import com.nossoteto.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public User registerUser(RegisterRequest request) throws Exception {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new Exception("Email already exists");
        }
        
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        return userRepository.save(user);
    }

    public AuthResponse loginUser(LoginRequest request) throws Exception {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        if (userOptional.isEmpty()) {
            throw new Exception("Invalid credentials");
        }
        
        User user = userOptional.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new Exception("Invalid credentials");
        }
        
        String token = jwtTokenProvider.generateToken(user.getId());
        return new AuthResponse(token, user.getId());
    }
}
