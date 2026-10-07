package com.pos.system.repository;

import com.pos.system.model.supplier.PurchaseReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PurchaseReturnItemRepository extends JpaRepository<PurchaseReturnItem, Long> {

    List<PurchaseReturnItem> findByPurchaseReturnId(Long purchaseReturnId);

    @Query("select item from PurchaseReturnItem item where item.purchaseReturnId in :purchaseReturnIds")
    List<PurchaseReturnItem> findByPurchaseReturnIdIn(@Param("purchaseReturnIds") Collection<Long> purchaseReturnIds);

    boolean existsByVariantId(Long variantId);
}
