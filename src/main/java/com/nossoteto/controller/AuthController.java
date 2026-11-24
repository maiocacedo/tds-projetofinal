package com.nossoteto.controller;

import com.nossoteto.dto.RegisterRequest;
import com.nossoteto.dto.RegisterResponse;
import com.nossoteto.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        RegisterResponse user = authService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
}
