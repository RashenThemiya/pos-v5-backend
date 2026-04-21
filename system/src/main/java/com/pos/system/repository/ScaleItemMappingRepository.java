package com.pos.system.repository;

import com.pos.system.model.catalog.ScaleItemMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScaleItemMappingRepository extends JpaRepository<ScaleItemMapping, Long> {
    Optional<ScaleItemMapping> findByBranchIdAndScaleItemCodeAndIsActiveTrue(Long branchId, String scaleItemCode);
    Optional<ScaleItemMapping> findByItemIdAndIsActiveTrue(Long itemId);
    boolean existsByBranchIdAndScaleItemCode(Long branchId, String scaleItemCode);
}