package com.pos.system.repository;

import com.pos.system.model.promotion.PromotionBuyXGetYRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PromotionBuyXGetYRuleRepository extends JpaRepository<PromotionBuyXGetYRule, Long> {
    List<PromotionBuyXGetYRule> findByPromotionId(Long promotionId);
    List<PromotionBuyXGetYRule> findByBranchIdAndBuyItemId(Long branchId, Long buyItemId);
    List<PromotionBuyXGetYRule> findByPromotionIdAndIsActiveTrue(Long promotionId);
}
