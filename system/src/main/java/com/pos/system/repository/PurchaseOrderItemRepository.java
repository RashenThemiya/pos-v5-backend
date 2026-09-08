package com.pos.system.repository;

import com.pos.system.model.supplier.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    List<PurchaseOrderItem> findByPoId(Long poId);
    @Query("select item from PurchaseOrderItem item where item.poId in :poIds")
    List<PurchaseOrderItem> findByPoIdIn(@Param("poIds") Collection<Long> poIds);
    Optional<PurchaseOrderItem> findByPoIdAndItemIdAndUnitId(Long poId, Long itemId, Long unitId);
    Optional<PurchaseOrderItem> findByPoIdAndItemIdAndVariantIdAndUnitId(Long poId, Long itemId, Long variantId, Long unitId);
    boolean existsByVariantId(Long variantId);
}
