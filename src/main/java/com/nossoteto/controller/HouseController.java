package com.nossoteto.controller;

import com.nossoteto.dto.CreateHouseRequest;
import com.nossoteto.dto.HouseResponse;
import com.nossoteto.service.HouseMembershipService;
import com.nossoteto.service.HouseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public HouseController(HouseService houseService, HouseMembershipService houseMembershipService) {
        this.houseService = houseService;
        this.houseMembershipService = houseMembershipService;
    }

    @PostMapping
    public ResponseEntity<HouseResponse> createHouse(
            Authentication authentication,
            @RequestBody CreateHouseRequest request) {
        String userId = extractUserId(authentication);
        HouseResponse created = houseService.createHouse(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
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
    public ResponseEntity<HouseResponse> getHouseDetails(
            Authentication authentication,
            @PathVariable String id) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(houseService.getHouseDetails(userId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HouseResponse> updateHouse(
            Authentication authentication,
            @PathVariable String id,
            @RequestBody CreateHouseRequest request) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(houseService.updateHouse(userId, id, request));
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveHouse(
            Authentication authentication,
            @PathVariable String id) {
        String userId = extractUserId(authentication);
        houseMembershipService.leaveHouse(userId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            Authentication authentication,
            @PathVariable String id,
            @PathVariable String memberId) {
        String userId = extractUserId(authentication);
        houseMembershipService.removeMember(userId, id, memberId);
        return ResponseEntity.noContent().build();
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? (String) authentication.getPrincipal() : null;
    }
}
