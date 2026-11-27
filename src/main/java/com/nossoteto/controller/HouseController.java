package com.nossoteto.controller;

import com.nossoteto.dto.CreateHouseRequest;
import com.nossoteto.dto.CreateRemovalRequestDto;
import com.nossoteto.dto.HouseMemberSummaryDto;
import com.nossoteto.dto.HouseResponse;
import com.nossoteto.dto.LeaveHouseRequest;
import com.nossoteto.dto.RemovalRequestResponse;
import com.nossoteto.dto.UpdateAdminStatusRequest;
import com.nossoteto.dto.VoteRequestDto;
import com.nossoteto.service.HouseMembershipService;
import com.nossoteto.service.HouseRemovalService;
import com.nossoteto.service.HouseService;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/houses")
public class HouseController {

    private final HouseService houseService;
    private final HouseMembershipService houseMembershipService;
    private final HouseRemovalService houseRemovalService;

    public HouseController(HouseService houseService,
                           HouseMembershipService houseMembershipService,
                           HouseRemovalService houseRemovalService) {
        this.houseService = houseService;
        this.houseMembershipService = houseMembershipService;
        this.houseRemovalService = houseRemovalService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE_HOUSE') and hasAuthority('SCOPE_write')")
    public ResponseEntity<HouseResponse> createHouse(
            Authentication authentication,
            @RequestBody CreateHouseRequest request) {
        String userId = extractUserId(authentication);
        HouseResponse created = houseService.createHouse(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SEARCH_HOUSES') and hasAuthority('SCOPE_read')")
    public ResponseEntity<List<HouseResponse>> listOpenHouses(
            Authentication authentication,
            @RequestParam(required = false) BigDecimal maxRent,
            @RequestParam(name = "orcamentoMaximo", required = false) BigDecimal orcamentoMaximo,
            @RequestParam(required = false) Boolean allowsPets,
            @RequestParam(name = "aceitaPets", required = false) Boolean aceitaPets,
            @RequestParam(required = false) Boolean allowsSmokers,
            @RequestParam(name = "aceitaFumantes", required = false) Boolean aceitaFumantes) {
        String userId = extractUserId(authentication);
        BigDecimal effectiveMaxRent = maxRent != null ? maxRent : orcamentoMaximo;
        Boolean effectiveAllowsPets = allowsPets != null ? allowsPets : aceitaPets;
        Boolean effectiveAllowsSmokers = allowsSmokers != null ? allowsSmokers : aceitaFumantes;

        return ResponseEntity.ok(houseService.listOpenHouses(
                userId,
                effectiveMaxRent,
                effectiveAllowsPets,
                effectiveAllowsSmokers
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SEARCH_HOUSES') and hasAuthority('SCOPE_read')")
    public ResponseEntity<HouseResponse> getHouseDetails(
            Authentication authentication,
            @PathVariable String id) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(houseService.getHouseDetails(userId, id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_HOUSE') and hasAuthority('SCOPE_write')")
    public ResponseEntity<HouseResponse> updateHouse(
            Authentication authentication,
            @PathVariable String id,
            @RequestBody CreateHouseRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(houseService.updateHouse(userId, id, request));
    }

    @PutMapping("/{id}/members/{memberId}/admin")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_HOUSE') and hasAuthority('SCOPE_write')")
    public ResponseEntity<HouseMemberSummaryDto> updateMemberAdminStatus(
            Authentication authentication,
            @PathVariable String id,
            @PathVariable String memberId,
            @RequestBody(required = false) UpdateAdminStatusRequest request) {
        String userId = extractUserId(authentication);
        Boolean isAdmin = request != null ? request.getIsAdmin() : null;
        return ResponseEntity.ok(houseMembershipService.updateMemberAdminStatus(userId, id, memberId, isAdmin));
    }

    @PostMapping("/{id}/leave")
    @PreAuthorize("hasAuthority('SCOPE_write')")
    public ResponseEntity<Void> leaveHouse(
            Authentication authentication,
            @PathVariable String id,
            @RequestBody(required = false) LeaveHouseRequest request) {
        String userId = extractUserId(authentication);
        String newAdminId = request != null ? request.getNewAdminId() : null;
        houseMembershipService.leaveHouse(userId, id, newAdminId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/removals")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_HOUSE') and hasAuthority('SCOPE_write')")
    public ResponseEntity<RemovalRequestResponse> createRemovalRequest(
            Authentication authentication,
            @PathVariable String id,
            @RequestBody(required = false) CreateRemovalRequestDto request) {
        String userId = extractUserId(authentication);
        RemovalRequestResponse created = houseRemovalService.createRemovalRequest(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}/removals")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_HOUSE') and hasAuthority('SCOPE_read')")
    public ResponseEntity<List<RemovalRequestResponse>> listOpenRemovals(
            Authentication authentication,
            @PathVariable String id) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(houseRemovalService.listOpenRemovals(userId, id));
    }

    @PostMapping("/{id}/removals/{removalId}/vote")
    @PreAuthorize("hasAuthority('SCOPE_write')")
    public ResponseEntity<RemovalRequestResponse> voteOnRemoval(
            Authentication authentication,
            @PathVariable String id,
            @PathVariable String removalId,
            @RequestBody(required = false) VoteRequestDto request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(houseRemovalService.voteOnRemoval(userId, id, removalId, request));
    }

    @DeleteMapping("/{id}/removals/{removalId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_HOUSE') and hasAuthority('SCOPE_write')")
    public ResponseEntity<Void> cancelRemovalRequest(
            Authentication authentication,
            @PathVariable String id,
            @PathVariable String removalId) {
        String userId = extractUserId(authentication);
        houseRemovalService.cancelRemovalRequest(userId, id, removalId);
        return ResponseEntity.noContent().build();
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
