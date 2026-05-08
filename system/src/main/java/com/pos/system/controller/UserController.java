package com.pos.system.controller;

import com.pos.system.dto.user.AssignAuthRequest;
import com.pos.system.dto.user.AssignRoleRequest;
import com.pos.system.dto.user.BranchUserPermissionResponse;
import com.pos.system.dto.user.CreateUserRequest;
import com.pos.system.dto.user.UpdateUserRequest;
import com.pos.system.dto.user.UserPermissionResponse;
import com.pos.system.dto.user.*;
import com.pos.system.model.auth.Role;
import com.pos.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin
public class UserController {

    private final UserService userService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_CREATE')")
    @PostMapping
    public ResponseEntity<String> create(@RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.create(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_VIEW')")
    @GetMapping
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_VIEW')")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_UPDATE')")
    @PutMapping("/{userId}")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable Long userId,
            @RequestBody UpdateUserRequest request
    ) {
        userService.updateUser(userId, request);
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_DELETE')")
    @DeleteMapping("/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok("User deleted successfully");
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_UPDATE')")
    @PostMapping("/{userId}/assign-auth")
    public ResponseEntity<String> assignAuthToUser(
            @PathVariable Long userId,
            @RequestBody AssignAuthRequest request
    ) {
        return ResponseEntity.ok(userService.assignAuthToUser(userId, request.getAuthId()));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_UPDATE')")
    @DeleteMapping("/{userId}/deassign-auth")
    public ResponseEntity<String> deassignAuthFromUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.deassignAuthFromUser(userId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_ROLE_ASSIGN')")
    @PostMapping("/{userId}/roles")
    public ResponseEntity<String> assignRole(
            @PathVariable Long userId,
            @RequestBody AssignRoleRequest request
    ) {
        return ResponseEntity.ok(userService.assignRole(userId, request.getRoleId()));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_ROLE_REMOVE')")
    @DeleteMapping("/{userId}/roles/{roleId}")
    public ResponseEntity<String> removeRole(
            @PathVariable Long userId,
            @PathVariable Long roleId
    ) {
        return ResponseEntity.ok(userService.removeRole(userId, roleId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_VIEW')")
    @GetMapping("/{userId}/roles")
    public ResponseEntity<List<Role>> getUserRoles(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserRoles(userId));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_VIEW')")
    @GetMapping("/{userId}/permissions")
    public ResponseEntity<List<UserPermissionResponse>> getUserPermissions(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserPermissions(userId));
    }
    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_VIEW')")
@GetMapping("/branch/{branchId}")
public ResponseEntity<List<UserResponseDto>> getUsersByBranch(@PathVariable Long branchId) {
    return ResponseEntity.ok(userService.getUsersByBranch(branchId));
}

@PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('USER_VIEW')")
@GetMapping("/branch/{branchId}/permissions")
public ResponseEntity<List<BranchUserPermissionResponse>> getBranchUserPermissions(@PathVariable Long branchId) {
    return ResponseEntity.ok(userService.getBranchUserPermissions(branchId));
}
}