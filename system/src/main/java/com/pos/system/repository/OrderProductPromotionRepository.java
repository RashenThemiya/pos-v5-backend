package com.pos.system.repository;

import com.pos.system.model.promotion.OrderProductPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderProductPromotionRepository extends JpaRepository<OrderProductPromotion, Long> {
    List<OrderProductPromotion> findByOrderProductId(Long orderProductId);
}
