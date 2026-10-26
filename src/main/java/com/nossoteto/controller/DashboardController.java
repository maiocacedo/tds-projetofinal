package com.nossoteto.controller;

import com.nossoteto.dto.DashboardResponse;
import com.nossoteto.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/houses/{houseId}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getHouseDashboard(
            Authentication authentication,
            @PathVariable String houseId) {
        String userId = extractUserId(authentication);
        return ResponseEntity.ok(dashboardService.getHouseDashboard(userId, houseId));
    }

    private String extractUserId(Authentication authentication) {
        return authentication != null ? (String) authentication.getPrincipal() : null;
    }
}
