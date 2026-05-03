package com.pos.system.repository;

import com.pos.system.model.supplier.PurchaseReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {

    List<PurchaseReturn> findByBranchIdOrderByReturnDateDesc(Long branchId);

    List<PurchaseReturn> findByBranchIdAndSupplierIdOrderByReturnDateDesc(
            Long branchId,
            Long supplierId
    );

    List<PurchaseReturn> findBySupplyIdOrderByReturnDateDesc(Long supplyId);
}