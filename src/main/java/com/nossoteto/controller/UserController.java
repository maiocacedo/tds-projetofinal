package com.nossoteto.controller;

import com.nossoteto.dto.UserMeResponse;
import com.nossoteto.dto.UserProfileDto;
import com.nossoteto.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> getCurrentUser(Authentication authentication) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.getCurrentUser(userId));
    }

    @PutMapping("/me/profile")
    public ResponseEntity<UserProfileDto> updateProfile(
            Authentication authentication,
            @RequestBody UserProfileDto dto) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.updateProfile(userId, dto));
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? (String) authentication.getPrincipal() : null;
    }
}
