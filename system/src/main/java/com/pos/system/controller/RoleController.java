package com.pos.system.controller;

import com.pos.system.dto.role.AssignPermissionRequest;
import com.pos.system.dto.role.RoleRequest;
import com.pos.system.dto.authority.AuthorityResponse;
import com.pos.system.model.auth.Role;
import com.pos.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@CrossOrigin
public class RoleController {

    private final RoleService roleService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ROLE_CREATE')")
    @PostMapping
    public ResponseEntity<Role> create(@RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<Role>> getAll() {
        return ResponseEntity.ok(roleService.getAll());
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ROLE_UPDATE')")
    @PutMapping("/{roleId}")
    public ResponseEntity<Role> update(@PathVariable Long roleId,
                                       @RequestBody RoleRequest request) {
        return ResponseEntity.ok(roleService.update(roleId, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ROLE_DELETE')")
    @DeleteMapping("/{roleId}")
    public ResponseEntity<String> delete(@PathVariable Long roleId) {
        roleService.delete(roleId);
        return ResponseEntity.ok("Role deleted successfully");
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ROLE_PERMISSION_ASSIGN')")
    @PostMapping("/{roleId}/permissions")
    public ResponseEntity<String> assignPermission(@PathVariable Long roleId,
                                                   @RequestBody AssignPermissionRequest request) {
        if (request.getAuthorityCodes() != null && !request.getAuthorityCodes().isEmpty()) {
            return ResponseEntity.ok(roleService.assignPermissions(roleId, request.getAuthorityCodes()));
        }

        return ResponseEntity.ok(roleService.assignPermission(roleId, request.getAuthorityCode()));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ROLE_PERMISSION_ASSIGN')")
    @PutMapping("/{roleId}/permissions")
    public ResponseEntity<String> replacePermissions(@PathVariable Long roleId,
                                                     @RequestBody AssignPermissionRequest request) {
        return ResponseEntity.ok(roleService.replacePermissions(roleId, request.getAuthorityCodes()));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('ROLE_PERMISSION_REMOVE')")
    @DeleteMapping("/{roleId}/permissions/{authorityCode}")
    public ResponseEntity<String> removePermission(@PathVariable Long roleId,
                                                   @PathVariable String authorityCode) {
        return ResponseEntity.ok(roleService.removePermission(roleId, authorityCode));
    }

    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<List<AuthorityResponse>> getRolePermissions(@PathVariable Long roleId) {
        return ResponseEntity.ok(roleService.getPermissionsByRole(roleId));
    }
}
