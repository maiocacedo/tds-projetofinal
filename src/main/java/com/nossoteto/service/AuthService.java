package com.nossoteto.service;

import com.nossoteto.dto.AuthResponse;
import com.nossoteto.dto.LoginRequest;
import com.nossoteto.dto.RegisterRequest;
import com.nossoteto.dto.RegisterResponse;
import com.nossoteto.exception.BusinessRuleException;
import com.nossoteto.exception.UnauthorizedException;
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

    public RegisterResponse registerUser(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new BusinessRuleException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessRuleException("Password is required");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessRuleException("Email already exists");
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        return RegisterResponse.fromEntity(userRepository.save(user));
    }

    public AuthResponse loginUser(LoginRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        if (userOptional.isEmpty() || request.getPassword() == null) {
            throw new UnauthorizedException("Invalid credentials");
        }

        User user = userOptional.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        String token = jwtTokenProvider.generateToken(user.getId());
        return new AuthResponse(token, user.getId(), (int) jwtTokenProvider.getExpirationSeconds());
    }
}
