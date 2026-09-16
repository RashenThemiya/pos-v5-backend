package com.pos.system.repository;

import com.pos.system.model.promotion.PromotionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionItemRepository extends JpaRepository<PromotionItem, Long> {
    List<PromotionItem> findByPromotionId(Long promotionId);
    List<PromotionItem> findByPromotionIdAndIsActiveTrue(Long promotionId);
    Optional<PromotionItem> findByPromotionIdAndItemId(Long promotionId, Long itemId);
    List<PromotionItem> findByBranchIdAndItemIdAndIsActiveTrue(Long branchId, Long itemId);
    List<PromotionItem> findByBranchIdAndItemIdAndVariantIdAndIsActiveTrue(Long branchId, Long itemId, Long variantId);
}
