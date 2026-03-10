package com.pos.system.service;

import com.pos.system.dto.role.RoleRequest;
import com.pos.system.model.auth.Permission;
import com.pos.system.model.auth.Role;
import com.pos.system.model.auth.RolePermission;
import com.pos.system.repository.PermissionRepository;
import com.pos.system.repository.RolePermissionRepository;
import com.pos.system.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

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

    public String assignPermission(Long roleId, Long permissionId) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        permissionRepository.findById(permissionId)
                .orElseThrow(() -> new RuntimeException("Permission not found"));

        if (rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            throw new RuntimeException("Permission already assigned to role");
        }

        RolePermission rolePermission = new RolePermission();
        rolePermission.setRoleId(roleId);
        rolePermission.setPermissionId(permissionId);
        rolePermissionRepository.save(rolePermission);

        return "Permission assigned to role successfully";
    }

    public String removePermission(Long roleId, Long permissionId) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        permissionRepository.findById(permissionId)
                .orElseThrow(() -> new RuntimeException("Permission not found"));

        RolePermission mapping = rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)
                .orElseThrow(() -> new RuntimeException("Permission is not assigned to this role"));

        rolePermissionRepository.delete(mapping);

        return "Permission removed from role successfully";
    }

    public List<Permission> getPermissionsByRole(Long roleId) {
        roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        List<RolePermission> mappings = rolePermissionRepository.findByRoleId(roleId);
        List<Permission> permissions = new ArrayList<>();

        for (RolePermission rp : mappings) {
            permissionRepository.findById(rp.getPermissionId()).ifPresent(permissions::add);
        }

        return permissions;
    }
}