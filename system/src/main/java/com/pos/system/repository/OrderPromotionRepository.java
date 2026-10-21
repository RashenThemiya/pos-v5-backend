package com.pos.system.repository;

import com.pos.system.model.promotion.OrderPromotion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPromotionRepository extends JpaRepository<OrderPromotion, Long> {
}
