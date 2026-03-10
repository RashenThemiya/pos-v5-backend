package com.pos.system.controller;

import com.pos.system.dto.permission.PermissionRequest;
import com.pos.system.model.auth.Permission;
import com.pos.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@CrossOrigin
public class PermissionController {

    private final PermissionService permissionService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PERMISSION_CREATE')")
    @PostMapping
    public ResponseEntity<Permission> create(@RequestBody PermissionRequest request) {
        return ResponseEntity.ok(permissionService.create(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PERMISSION_VIEW')")
    @GetMapping
    public ResponseEntity<List<Permission>> getAll() {
        return ResponseEntity.ok(permissionService.getAll());
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PERMISSION_UPDATE')")
    @PutMapping("/{permissionId}")
    public ResponseEntity<Permission> update(@PathVariable Long permissionId,
                                             @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(permissionService.update(permissionId, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('PERMISSION_DELETE')")
    @DeleteMapping("/{permissionId}")
    public ResponseEntity<String> delete(@PathVariable Long permissionId) {
        permissionService.delete(permissionId);
        return ResponseEntity.ok("Permission deleted successfully");
    }
}