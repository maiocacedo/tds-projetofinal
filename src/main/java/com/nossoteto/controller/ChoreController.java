package com.nossoteto.controller;

import com.nossoteto.dto.ChoreResponse;
import com.nossoteto.dto.ChoreSwapDto;
import com.nossoteto.dto.CreateChoreRequest;
import com.nossoteto.dto.CreateChoreSwapRequest;
import com.nossoteto.dto.UpdateRotationRequest;
import com.nossoteto.service.ChoreService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private static final String READ = "hasAuthority('ROLE_MANAGE_CHORES') and hasAuthority('SCOPE_read')";
    private static final String WRITE = "hasAuthority('ROLE_MANAGE_CHORES') and hasAuthority('SCOPE_write')";

    private final ChoreService choreService;

    public ChoreController(ChoreService choreService) {
        this.choreService = choreService;
    }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<ChoreResponse> createChore(
            Authentication authentication,
            @PathVariable String houseId,
            @RequestBody CreateChoreRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(choreService.createChore(userId, houseId, request));
    }

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<List<ChoreResponse>> listChores(
            Authentication authentication,
            @PathVariable String houseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.listChores(userId, houseId));
    }

    @PutMapping("/{choreId}/complete")
    @PreAuthorize(WRITE)
    public ResponseEntity<ChoreResponse> completeChore(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String choreId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.completeChore(userId, houseId, choreId));
    }

    @PutMapping("/{choreId}/uncomplete")
    @PreAuthorize(WRITE)
    public ResponseEntity<ChoreResponse> uncompleteChore(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String choreId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.uncompleteChore(userId, houseId, choreId));
    }

    @PutMapping("/{choreId}/reopen")
    @PreAuthorize(WRITE)
    public ResponseEntity<ChoreResponse> reopenChore(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String choreId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.reopenChore(userId, houseId, choreId));
    }

    @PutMapping("/{choreId}/rotation")
    @PreAuthorize(WRITE)
    public ResponseEntity<ChoreResponse> updateRotation(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String choreId,
            @RequestBody UpdateRotationRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.updateRotation(userId, houseId, choreId, request));
    }

    @GetMapping("/swaps")
    @PreAuthorize(READ)
    public ResponseEntity<List<ChoreSwapDto>> listPendingSwaps(
            Authentication authentication,
            @PathVariable String houseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.listPendingSwaps(userId, houseId));
    }

    @PostMapping("/{choreId}/swaps")
    @PreAuthorize(WRITE)
    public ResponseEntity<ChoreSwapDto> createSwap(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String choreId,
            @RequestBody CreateChoreSwapRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(choreService.createSwap(userId, houseId, choreId, request));
    }

    @PutMapping("/swaps/{swapId}/accept")
    @PreAuthorize(WRITE)
    public ResponseEntity<ChoreSwapDto> acceptSwap(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String swapId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.acceptSwap(userId, houseId, swapId));
    }

    @PutMapping("/swaps/{swapId}/reject")
    @PreAuthorize(WRITE)
    public ResponseEntity<ChoreSwapDto> rejectSwap(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String swapId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.rejectSwap(userId, houseId, swapId));
    }

    @PostMapping("/rotate")
    @PreAuthorize(WRITE)
    public ResponseEntity<List<ChoreResponse>> rotateHouseChores(
            Authentication authentication,
            @PathVariable String houseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(choreService.rotateHouseChores(userId, houseId));
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
