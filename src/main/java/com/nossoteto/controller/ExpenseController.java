package com.nossoteto.controller;

import com.nossoteto.dto.CreateExpenseRequest;
import com.nossoteto.dto.ExpenseResponse;
import com.nossoteto.dto.ProposeSplitRequest;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.service.ExpenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/houses/{houseId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            Authentication authentication,
            @PathVariable String houseId,
            @RequestBody CreateExpenseRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.createExpense(userId, houseId, request));
    }

    @GetMapping
    public ResponseEntity<List<ExpenseResponse>> listExpenses(
            Authentication authentication,
            @PathVariable String houseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.listExpenses(userId, houseId));
    }

    @GetMapping("/{expenseId}/attachment")
    public ResponseEntity<byte[]> getAttachment(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId) {
        String userId = extractUserId(authentication);
        byte[] bytes = expenseService.getAttachment(userId, houseId, expenseId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @PostMapping("/{expenseId}/propose-split")
    public ResponseEntity<ExpenseResponse> proposeCustomSplit(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId,
            @RequestBody ProposeSplitRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.proposeCustomSplit(userId, houseId, expenseId, request));
    }

    @PostMapping("/{expenseId}/vote-split")
    public ResponseEntity<ExpenseResponse> voteOnCustomSplit(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId,
            @RequestBody VoteRequestDto voteDto) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.voteOnCustomSplit(userId, houseId, expenseId, voteDto));
    }

    @PutMapping("/{expenseId}/pay")
    public ResponseEntity<ExpenseResponse> markSplitAsPaid(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.markSplitAsPaid(userId, houseId, expenseId));
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? (String) authentication.getPrincipal() : null;
    }
}
