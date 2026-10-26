package com.nossoteto.controller;

import com.nossoteto.dto.ChoreResponse;
import com.nossoteto.dto.CreateChoreRequest;
import com.nossoteto.service.ChoreService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/houses/{houseId}/chores")
public class ChoreController {

    private final ChoreService choreService;

    public ChoreController(ChoreService choreService) {
        this.choreService = choreService;
    }

    @PostMapping
    public ResponseEntity<ChoreResponse> createChore(
            Authentication authentication,
            @PathVariable String houseId,
            @RequestBody CreateChoreRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(choreService.createChore(userId, houseId, request));
    }

    @GetMapping
    public ResponseEntity<List<ChoreResponse>> listChores(
            Authentication authentication,
            @PathVariable String houseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.listChores(userId, houseId));
    }

    @PutMapping("/{choreId}/complete")
    public ResponseEntity<ChoreResponse> completeChore(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String choreId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.completeChore(userId, houseId, choreId));
    }

    @PostMapping("/rotate")
    public ResponseEntity<List<ChoreResponse>> rotateHouseChores(
            Authentication authentication,
            @PathVariable String houseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.rotateHouseChores(userId, houseId));
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? (String) authentication.getPrincipal() : null;
    }
}
