package com.nossoteto.controller;

import com.nossoteto.dto.CreateMatchRequestDto;
import com.nossoteto.dto.MatchRequestResponse;
import com.nossoteto.dto.MyMatchRequestDto;
import com.nossoteto.dto.VoteMatchResponse;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.service.MatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping("/api/matches/requests")
    @PreAuthorize("hasAuthority('ROLE_MATCH') and hasAuthority('SCOPE_write')")
    public ResponseEntity<MatchRequestResponse> createMatchRequest(
            Authentication authentication,
            @RequestBody CreateMatchRequestDto dto) {
        String userId = extractUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(matchService.createMatchRequest(userId, dto));
    }

    @GetMapping("/api/matches/requests/mine")
    @PreAuthorize("hasAuthority('ROLE_MATCH') and hasAuthority('SCOPE_read')")
    public ResponseEntity<List<MyMatchRequestDto>> listMyMatchRequests(Authentication authentication) {
        return ResponseEntity.ok(matchService.listMyMatchRequests(extractUserId(authentication)));
    }

    @DeleteMapping("/api/matches/requests/{id}")
    @PreAuthorize("hasAuthority('ROLE_MATCH') and hasAuthority('SCOPE_write')")
    public ResponseEntity<Void> withdrawMatchRequest(Authentication authentication, @PathVariable String id) {
        matchService.withdrawMatchRequest(extractUserId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/houses/{id}/matches")
    @PreAuthorize("hasAuthority('ROLE_MATCH') and hasAuthority('SCOPE_read')")
    public ResponseEntity<List<MatchRequestResponse>> listHouseMatchRequests(
            Authentication authentication,
            @PathVariable String id) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(matchService.listHouseMatchRequests(userId, id));
    }

    @PostMapping("/api/matches/requests/{requestId}/vote")
    @PreAuthorize("hasAuthority('ROLE_MATCH') and hasAuthority('SCOPE_write')")
    public ResponseEntity<VoteMatchResponse> voteOnMatchRequest(
            Authentication authentication,
            @PathVariable String requestId,
            @RequestBody VoteRequestDto dto) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(matchService.voteOnMatchRequest(userId, requestId, dto));
    }

    @PostMapping("/api/matches/requests/cancel-others")
    @PreAuthorize("hasAuthority('ROLE_MATCH') and hasAuthority('SCOPE_write')")
    public ResponseEntity<Map<String, Object>> cancelOtherPendingRequests(
            Authentication authentication) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(matchService.cancelOtherPendingRequests(userId));
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
