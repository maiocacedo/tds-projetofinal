package com.nossoteto.controller;

import com.nossoteto.dto.CreateExpenseRequest;
import com.nossoteto.dto.ExpenseResponse;
import com.nossoteto.dto.ExpenseSummaryResponse;
import com.nossoteto.dto.ProposeSplitRequest;
import com.nossoteto.dto.UpdateExpenseRequest;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.service.ExpenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_write')")
    public ResponseEntity<ExpenseResponse> createExpense(
            Authentication authentication,
            @PathVariable String houseId,
            @RequestBody CreateExpenseRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.createExpense(userId, houseId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_read')")
    public ResponseEntity<List<ExpenseResponse>> listExpenses(
            Authentication authentication,
            @PathVariable String houseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.listExpenses(userId, houseId));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_read')")
    public ResponseEntity<ExpenseSummaryResponse> getSummary(
            Authentication authentication,
            @PathVariable String houseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.getSummary(userId, houseId));
    }

    @PutMapping("/{expenseId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_write')")
    public ResponseEntity<ExpenseResponse> updateExpense(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId,
            @RequestBody UpdateExpenseRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.updateExpense(userId, houseId, expenseId, request));
    }

    @DeleteMapping("/{expenseId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_write')")
    public ResponseEntity<Void> deleteExpense(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId) {
        String userId = extractUserId(authentication);
        expenseService.deleteExpense(userId, houseId, expenseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{expenseId}/attachment")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_read')")
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
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_write')")
    public ResponseEntity<ExpenseResponse> proposeCustomSplit(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId,
            @RequestBody ProposeSplitRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.proposeCustomSplit(userId, houseId, expenseId, request));
    }

    @PostMapping("/{expenseId}/vote-split")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_write')")
    public ResponseEntity<ExpenseResponse> voteOnCustomSplit(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId,
            @RequestBody VoteRequestDto voteDto) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.voteOnCustomSplit(userId, houseId, expenseId, voteDto));
    }

    @PutMapping("/{expenseId}/pay")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_write')")
    public ResponseEntity<ExpenseResponse> markSplitAsPaid(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.markSplitAsPaid(userId, houseId, expenseId));
    }

    @PutMapping("/{expenseId}/splits/{splitId}/confirm-received")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_write')")
    public ResponseEntity<ExpenseResponse> confirmReceived(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId,
            @PathVariable String splitId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.confirmReceived(userId, houseId, expenseId, splitId));
    }

    @PutMapping("/{expenseId}/splits/{splitId}/not-received")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_EXPENSES') and hasAuthority('SCOPE_write')")
    public ResponseEntity<ExpenseResponse> markNotReceived(
            Authentication authentication,
            @PathVariable String houseId,
            @PathVariable String expenseId,
            @PathVariable String splitId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(expenseService.markNotReceived(userId, houseId, expenseId, splitId));
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
