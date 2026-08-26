package com.pos.system.repository;

import com.pos.system.model.catalog.UnitMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitMasterRepository extends JpaRepository<UnitMaster, Long> {
    List<UnitMaster> findByBranchId(Long branchId);
    List<UnitMaster> findByBranchIdAndIsActive(Long branchId, Boolean isActive);
    Optional<UnitMaster> findByUnitIdAndBranchId(Long unitId, Long branchId);
    boolean existsByBranchIdAndName(Long branchId, String name);
    boolean existsByBranchIdAndNameAndUnitIdNot(Long branchId, String name, Long unitId);
}
