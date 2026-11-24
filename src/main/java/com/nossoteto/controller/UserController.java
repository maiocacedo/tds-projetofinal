package com.nossoteto.controller;

import com.nossoteto.dto.UserMeResponse;
import com.nossoteto.dto.UserProfileDto;
import com.nossoteto.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAuthority('ROLE_MANAGE_PROFILE') and hasAuthority('SCOPE_read')")
    public ResponseEntity<UserMeResponse> getCurrentUser(Authentication authentication) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.getCurrentUser(userId));
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_PROFILE') and hasAuthority('SCOPE_write')")
    public ResponseEntity<UserProfileDto> updateProfile(
            Authentication authentication,
            @RequestBody UserProfileDto dto) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.updateProfile(userId, dto));
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
