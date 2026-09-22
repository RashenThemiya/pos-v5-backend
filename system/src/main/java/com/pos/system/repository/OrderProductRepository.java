package com.pos.system.repository;

import com.pos.system.model.sale.OrderProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderProductRepository extends JpaRepository<OrderProduct, Long> {
    List<OrderProduct> findByOrderId(Long orderId);
    List<OrderProduct> findByOrderIdAndItemId(Long orderId, Long itemId);
    List<OrderProduct> findByOrderIdAndItemIdAndVariantId(Long orderId, Long itemId, Long variantId);
    boolean existsByVariantId(Long variantId);
}
