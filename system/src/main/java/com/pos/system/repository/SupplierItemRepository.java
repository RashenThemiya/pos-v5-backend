package com.pos.system.repository;

import com.pos.system.model.supplier.SupplierItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierItemRepository extends JpaRepository<SupplierItem, Long> {

    Optional<SupplierItem> findByBranchIdAndSupplierIdAndItemIdAndUnitId(
            Long branchId,
            Long supplierId,
            Long itemId,
            Long unitId
    );

    List<SupplierItem> findByBranchIdAndSupplierIdAndIsActiveTrue(Long branchId, Long supplierId);

    List<SupplierItem> findByBranchIdAndItemIdAndIsActiveTrue(Long branchId, Long itemId);
}