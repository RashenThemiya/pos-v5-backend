package com.pos.system.repository;

import com.pos.system.model.supplier.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByBranchId(Long branchId);
    Optional<PurchaseOrder> findByPoNo(String poNo);

    @Query("SELECT DISTINCT po FROM PurchaseOrder po JOIN PurchaseOrderItem poi ON poi.poId = po.poId " +
            "WHERE po.branchId = :branchId AND poi.itemId = :itemId ORDER BY po.createdAt DESC")
    List<PurchaseOrder> findByBranchIdAndItemId(@Param("branchId") Long branchId,
                                                @Param("itemId") Long itemId);
}
