package com.nossoteto.controller;

import com.nossoteto.dto.PasswordResetConfirmDto;
import com.nossoteto.dto.PasswordResetRequestDto;
import com.nossoteto.dto.RegisterRequest;
import com.nossoteto.dto.RegisterResponse;
import com.nossoteto.service.AuthService;
import com.nossoteto.service.PasswordResetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        RegisterResponse user = authService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/password-reset")
    public ResponseEntity<Map<String, String>> requestPasswordReset(@RequestBody PasswordResetRequestDto request) {
        String email = request != null ? request.email() : null;
        return passwordResetService.requestReset(email)
                .map(demoToken -> ResponseEntity.accepted().body(Map.of("demoToken", demoToken)))
                .orElseGet(() -> ResponseEntity.accepted().body(Map.of()));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@RequestBody PasswordResetConfirmDto request) {
        passwordResetService.confirmReset(
                request != null ? request.token() : null,
                request != null ? request.newPassword() : null);
        return ResponseEntity.noContent().build();
    }
}
