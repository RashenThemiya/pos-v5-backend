package com.pos.system.repository;

import com.pos.system.model.supplier.Supply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplyRepository extends JpaRepository<Supply, Long> {

    Optional<Supply> findByGrnNo(String grnNo);

    List<Supply> findByBranchId(Long branchId);

    List<Supply> findByPoId(Long poId);

    @Query("SELECT DISTINCT s FROM Supply s JOIN SupplyProduct sp ON sp.supplyId = s.supplyId " +
            "WHERE s.branchId = :branchId AND sp.itemId = :itemId ORDER BY s.supplyDate DESC")
    List<Supply> findByBranchIdAndItemId(@Param("branchId") Long branchId,
                                         @Param("itemId") Long itemId);

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
