package com.nossoteto.controller;

import com.nossoteto.dto.UserMeResponse;
import com.nossoteto.dto.UserPointsResponse;
import com.nossoteto.dto.UserProfileDto;
import com.nossoteto.service.PointService;
import com.nossoteto.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PointService pointService;

    public UserController(UserService userService, PointService pointService) {
        this.userService = userService;
        this.pointService = pointService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_PROFILE') and hasAuthority('SCOPE_read')")
    public ResponseEntity<UserMeResponse> getCurrentUser(Authentication authentication) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.getCurrentUser(userId));
    }

    @GetMapping("/me/points")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_PROFILE') and hasAuthority('SCOPE_read')")
    public ResponseEntity<UserPointsResponse> getMyPoints(Authentication authentication) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(pointService.getUserPoints(userId));
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_PROFILE') and hasAuthority('SCOPE_write')")
    public ResponseEntity<UserProfileDto> updateProfile(
            Authentication authentication,
            @RequestBody UserProfileDto dto) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.updateProfile(userId, dto));
    }

    @PostMapping("/me/welcome-ack")
    // Only SCOPE_write (same pattern as leave): a user without ROLE_MANAGE_PROFILE must still be
    // able to dismiss pendingWelcome.
    @PreAuthorize("hasAuthority('SCOPE_write')")
    public ResponseEntity<Void> acknowledgeWelcome(Authentication authentication) {
        userService.acknowledgeWelcome(extractUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
