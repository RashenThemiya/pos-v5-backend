package com.pos.system.service;

import com.pos.system.dto.permission.PermissionRequest;
import com.pos.system.model.auth.Permission;
import com.pos.system.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public Permission create(PermissionRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new RuntimeException("Permission code is required");
        }

        if (permissionRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Permission code already exists");
        }

        Permission permission = new Permission();
        permission.setCode(request.getCode().trim().toUpperCase());
        permission.setDescription(request.getDescription());
        permission.setModule(request.getModule());

        return permissionRepository.save(permission);
    }

    public List<Permission> getAll() {
        return permissionRepository.findAll();
    }

    public Permission update(Long permissionId, PermissionRequest request) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new RuntimeException("Permission not found"));

        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new RuntimeException("Permission code is required");
        }

        String newCode = request.getCode().trim().toUpperCase();

        if (!permission.getCode().equalsIgnoreCase(newCode)
                && permissionRepository.existsByCode(newCode)) {
            throw new RuntimeException("Permission code already exists");
        }

        permission.setCode(newCode);
        permission.setDescription(request.getDescription());
        permission.setModule(request.getModule());

        return permissionRepository.save(permission);
    }

    public void delete(Long permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new RuntimeException("Permission not found"));

        permissionRepository.delete(permission);
    }
}