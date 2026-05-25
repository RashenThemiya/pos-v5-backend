package com.pos.system.service;

import com.pos.system.dto.role.RoleRequest;
import com.pos.system.dto.authority.AuthorityResponse;
import com.pos.system.model.auth.Role;
import com.pos.system.model.auth.RolePermission;
import com.pos.system.repository.RolePermissionRepository;
import com.pos.system.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuthorityService authorityService;

    public Role create(RoleRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Role name is required");
        }

        String roleName = request.getName().trim().toUpperCase();

        if (roleRepository.existsByName(roleName)) {
            throw new RuntimeException("Role already exists");
        }

        Role role = new Role();
        role.setName(roleName);
        role.setDescription(request.getDescription());
        role.setIsActive(request.getIsActive() == null ? true : request.getIsActive());

        return roleRepository.save(role);
    }

    public List<Role> getAll() {
        return roleRepository.findAll();
    }

    public Role update(Long roleId, RoleRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("Role name is required");
        }

        String newName = request.getName().trim().toUpperCase();

        if (!role.getName().equalsIgnoreCase(newName) && roleRepository.existsByName(newName)) {
            throw new RuntimeException("Role already exists");
        }

        role.setName(newName);
        role.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            role.setIsActive(request.getIsActive());
        }

        return roleRepository.save(role);
    }

    public void delete(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        roleRepository.delete(role);
    }

    public String assignPermission(Long roleId, String authorityCode) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        String normalizedCode = normalizeAuthorityCode(authorityCode);
        validateAuthorityCode(normalizedCode);

        if (rolePermissionRepository.existsByRoleIdAndAuthorityCode(roleId, normalizedCode)) {
            throw new RuntimeException("Permission already assigned to role");
        }

        RolePermission rolePermission = new RolePermission();
        rolePermission.setRoleId(roleId);
        rolePermission.setAuthorityCode(normalizedCode);
        rolePermission.setCreatedAt(LocalDateTime.now());
        rolePermissionRepository.save(rolePermission);

        return "Permission assigned to role successfully";
    }

    public String assignPermissions(Long roleId, List<String> authorityCodes) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if (authorityCodes == null || authorityCodes.isEmpty()) {
            throw new RuntimeException("authorityCodes is required");
        }

        Set<String> availableCodes = authorityService.getAllAuthorityCodes();
        Set<String> uniqueCodes = new LinkedHashSet<>();

        for (String authorityCode : authorityCodes) {
            String normalizedCode = normalizeAuthorityCode(authorityCode);
            if (!availableCodes.contains(normalizedCode)) {
                throw new RuntimeException("Invalid authority code: " + normalizedCode);
            }
            uniqueCodes.add(normalizedCode);
        }

        List<RolePermission> mappings = new ArrayList<>();

        for (String authorityCode : uniqueCodes) {
            if (rolePermissionRepository.existsByRoleIdAndAuthorityCode(roleId, authorityCode)) {
                continue;
            }

            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setAuthorityCode(authorityCode);
            rolePermission.setCreatedAt(LocalDateTime.now());
            mappings.add(rolePermission);
        }

        if (!mappings.isEmpty()) {
            rolePermissionRepository.saveAll(mappings);
        }

        return "Permissions assigned to role successfully";
    }

    @Transactional
    public String replacePermissions(Long roleId, List<String> authorityCodes) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        if (authorityCodes == null) {
            throw new RuntimeException("authorityCodes is required");
        }

        Set<String> availableCodes = authorityService.getAllAuthorityCodes();
        Set<String> uniqueCodes = new LinkedHashSet<>();

        for (String authorityCode : authorityCodes) {
            String normalizedCode = normalizeAuthorityCode(authorityCode);
            if (!availableCodes.contains(normalizedCode)) {
                throw new RuntimeException("Invalid authority code: " + normalizedCode);
            }
            uniqueCodes.add(normalizedCode);
        }

        rolePermissionRepository.deleteByRoleId(roleId);

        List<RolePermission> mappings = new ArrayList<>();
        for (String authorityCode : uniqueCodes) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setAuthorityCode(authorityCode);
            rolePermission.setCreatedAt(LocalDateTime.now());
            mappings.add(rolePermission);
        }

        if (!mappings.isEmpty()) {
            rolePermissionRepository.saveAll(mappings);
        }

        return "Role permissions updated successfully";
    }

    public String removePermission(Long roleId, String authorityCode) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        String normalizedCode = normalizeAuthorityCode(authorityCode);
        validateAuthorityCode(normalizedCode);

        RolePermission mapping = rolePermissionRepository.findByRoleIdAndAuthorityCode(roleId, normalizedCode)
                .orElseThrow(() -> new RuntimeException("Permission is not assigned to this role"));

        rolePermissionRepository.delete(mapping);

        return "Permission removed from role successfully";
    }

    public List<AuthorityResponse> getPermissionsByRole(Long roleId) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        List<RolePermission> mappings = rolePermissionRepository.findByRoleId(roleId);
        List<AuthorityResponse> permissions = new ArrayList<>();

        for (RolePermission rp : mappings) {
            String code = rp.getAuthorityCode();
            permissions.add(new AuthorityResponse(code, toLabel(code)));
        }

        return permissions;
    }

    private void validateAuthorityCode(String authorityCode) {
        if (!authorityService.getAllAuthorityCodes().contains(authorityCode)) {
            throw new RuntimeException("Invalid authority code: " + authorityCode);
        }
    }

    private String normalizeAuthorityCode(String authorityCode) {
        if (authorityCode == null || authorityCode.isBlank()) {
            throw new RuntimeException("authorityCode is required");
        }

        return authorityCode.trim().toUpperCase();
    }

    private String toLabel(String code) {
        String[] parts = code.toLowerCase().split("_");
        List<String> labelParts = new ArrayList<>();

        for (String part : parts) {
            if (!part.isBlank()) {
                labelParts.add(part.substring(0, 1).toUpperCase() + part.substring(1));
            }
        }

        return String.join(" ", labelParts);
    }
}
