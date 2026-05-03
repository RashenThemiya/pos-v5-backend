package com.pos.system.repository;

import com.pos.system.model.promotion.PromotionRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, Long> {
    List<PromotionRedemption> findByPromotionId(Long promotionId);
    long countByPromotionId(Long promotionId);
    long countByPromotionIdAndCustomerId(Long promotionId, Long customerId);
    boolean existsByPromotionIdAndOrderId(Long promotionId, Long orderId);
}
