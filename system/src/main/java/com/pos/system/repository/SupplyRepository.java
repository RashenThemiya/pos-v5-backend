package com.pos.system.repository;

import com.pos.system.model.supplier.Supply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplyRepository extends JpaRepository<Supply, Long> {

    Optional<Supply> findByGrnNo(String grnNo);

    List<Supply> findByBranchId(Long branchId);

    List<Supply> findByPoId(Long poId);

    List<Supply> findByBranchIdAndPaymentStatusNotOrderBySupplyDateDesc(
            Long branchId,
            String paymentStatus
    );

    List<Supply> findByBranchIdAndSupplierIdAndPaymentStatusNotOrderBySupplyDateDesc(
            Long branchId,
            Long supplierId,
            String paymentStatus
    );
}
