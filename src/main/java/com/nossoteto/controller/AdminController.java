package com.nossoteto.controller;

import com.nossoteto.dto.AdminUserResponse;
import com.nossoteto.dto.UpdatePermissionsRequest;
import com.nossoteto.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN_USERS') and hasAuthority('SCOPE_read')")
    public ResponseEntity<List<AdminUserResponse>> listUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN_USERS') and hasAuthority('SCOPE_write')")
    public ResponseEntity<AdminUserResponse> replacePermissions(Authentication authentication,
                                                                @PathVariable String id,
                                                                @RequestBody UpdatePermissionsRequest request) {
        return ResponseEntity.ok(adminService.replacePermissions(authentication.getName(), id, request.permissions()));
    }
}
