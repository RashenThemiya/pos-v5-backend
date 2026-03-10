package com.pos.system.repository;

import com.pos.system.model.auth.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);
    List<RolePermission> findByRoleId(Long roleId);
    Optional<RolePermission> findByRoleIdAndPermissionId(Long roleId, Long permissionId);
}