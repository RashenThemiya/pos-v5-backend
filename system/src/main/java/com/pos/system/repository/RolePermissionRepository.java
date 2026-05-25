package com.pos.system.repository;

import com.pos.system.model.auth.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    boolean existsByRoleIdAndAuthorityCode(Long roleId, String authorityCode);
    List<RolePermission> findByRoleId(Long roleId);
    Optional<RolePermission> findByRoleIdAndAuthorityCode(Long roleId, String authorityCode);
    void deleteByRoleId(Long roleId);
}
