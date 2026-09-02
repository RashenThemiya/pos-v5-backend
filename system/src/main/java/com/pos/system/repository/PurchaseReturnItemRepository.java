package com.pos.system.repository;

import com.pos.system.model.supplier.PurchaseReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseReturnItemRepository extends JpaRepository<PurchaseReturnItem, Long> {

    List<PurchaseReturnItem> findByPurchaseReturnId(Long purchaseReturnId);
    boolean existsByVariantId(Long variantId);
}
