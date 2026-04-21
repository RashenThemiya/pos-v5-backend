package com.pos.system.repository;

import com.pos.system.model.supplier.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    List<PurchaseOrderItem> findByPoId(Long poId);
    Optional<PurchaseOrderItem> findByPoIdAndItemIdAndUnitId(Long poId, Long itemId, Long unitId);
}