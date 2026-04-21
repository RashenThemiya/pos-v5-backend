package com.pos.system.repository;

import com.pos.system.model.supplier.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByBranchId(Long branchId);
    Optional<PurchaseOrder> findByPoNo(String poNo);
}