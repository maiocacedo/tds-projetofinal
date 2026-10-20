package com.nossoteto.controller;

import com.nossoteto.dto.CreateGroceryRequest;
import com.nossoteto.dto.GroceryResponse;
import com.nossoteto.service.GroceryService;
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
@RequestMapping("/api/houses/{houseId}/groceries")
public class GroceryController {

    private final GroceryService groceryService;

    public GroceryController(GroceryService groceryService) {
        this.groceryService = groceryService;
    }

    @PostMapping
    public ResponseEntity<GroceryResponse> createGrocery(
            Authentication authentication,
            @PathVariable String houseId,
            @RequestBody CreateGroceryRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groceryService.createGrocery(userId, houseId, request));
    }

    @GetMapping
    public ResponseEntity<List<GroceryResponse>> listGroceries(
            Authentication authentication,
            @PathVariable String houseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(groceryService.listGroceries(userId, houseId));
    }

    @PutMapping("/{itemId}/assign")
    public ResponseEntity<GroceryResponse> assignGrocery(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String itemId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(groceryService.assignGrocery(userId, houseId, itemId));
    }

    @PutMapping("/{itemId}/purchase")
    public ResponseEntity<GroceryResponse> markAsPurchased(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String itemId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(groceryService.markAsPurchased(userId, houseId, itemId));
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? (String) authentication.getPrincipal() : null;
    }
}
