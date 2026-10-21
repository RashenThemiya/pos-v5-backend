package com.pos.system.service;

import com.pos.system.dto.sale.*;
import com.pos.system.model.promotion.*;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.OrderProduct;
import com.pos.system.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/** Saves the actual checkout discounts, rather than recalculating historical sales for analytics. */
@Service
@RequiredArgsConstructor
@Transactional
public class SalePromotionService {
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository redemptionRepository;
    private final OrderPromotionRepository orderPromotionRepository;
    private final OrderProductPromotionRepository linePromotionRepository;

    public void recordLine(CustomerOrder order, OrderProduct line, Long promotionId) {
        if (promotionId == null || line.getDiscount().signum() == 0) return;
        Promotion promotion = requirePromotion(order, promotionId);
        if (Set.of("PERCENTAGE", "FIXED").contains(promotion.getType())) {
            throw new IllegalArgumentException("Bill promotions must be applied at bill level");
        }
        OrderProductPromotion applied = new OrderProductPromotion();
        applied.setBranchId(order.getBranchId());
        applied.setOrderProductId(line.getOrderProductId());
        applied.setPromotionId(promotionId);
        applied.setPromotionName(promotion.getName());
        applied.setPromoType(promotion.getType());
        applied.setPromoValue(promotion.getValue());
        applied.setDiscountAmount(line.getDiscount());
        applied.setCreatedAt(order.getOrderDate());
        linePromotionRepository.save(applied);
        redeem(order, promotion, line.getDiscount());
    }

    public void recordBill(CustomerOrder order, List<Long> promotionIds) {
        if (promotionIds == null || promotionIds.isEmpty()) return;
        BigDecimal runningTotal = order.getSubtotal();
        BigDecimal attributed = BigDecimal.ZERO;
        List<Promotion> selected = new LinkedHashSet<>(promotionIds).stream()
                .map(id -> requirePromotion(order, id)).toList();
        if (selected.size() > 1 && selected.stream().anyMatch(p -> !Boolean.TRUE.equals(p.getIsStackable()))) {
            throw new IllegalArgumentException("Selected bill promotions cannot be combined");
        }
        for (Promotion promotion : selected) {
            if (!Set.of("PERCENTAGE", "FIXED").contains(promotion.getType())) {
                throw new IllegalArgumentException("Only bill promotions may be applied at bill level");
            }
            if (promotion.getMinBillTotal() != null && runningTotal.compareTo(promotion.getMinBillTotal()) < 0) {
                throw new IllegalArgumentException("Bill does not meet the promotion minimum");
            }
            BigDecimal amount = "PERCENTAGE".equals(promotion.getType())
                    ? runningTotal.multiply(promotion.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : promotion.getValue();
            amount = amount.max(BigDecimal.ZERO).min(runningTotal).setScale(2, RoundingMode.HALF_UP);
            // Never attribute more discount than was actually deducted on the invoice.
            amount = amount.min(order.getDiscount().subtract(attributed).max(BigDecimal.ZERO));
            if (amount.signum() == 0) continue;
            OrderPromotion applied = new OrderPromotion();
            applied.setBranchId(order.getBranchId());
            applied.setOrderId(order.getOrderId());
            applied.setPromotionId(promotion.getPromotionId());
            applied.setDiscountAmount(amount);
            applied.setCreatedAt(order.getOrderDate());
            orderPromotionRepository.save(applied);
            redeem(order, promotion, amount);
            attributed = attributed.add(amount);
            runningTotal = runningTotal.subtract(amount);
        }
    }

    private Promotion requirePromotion(CustomerOrder order, Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promotion not found: " + id));
        LocalDateTime now = order.getOrderDate();
        if (!Objects.equals(order.getBranchId(), promotion.getBranchId())
                || !Boolean.TRUE.equals(promotion.getIsActive())
                || (promotion.getStartAt() != null && now.isBefore(promotion.getStartAt()))
                || (promotion.getEndAt() != null && now.isAfter(promotion.getEndAt()))) {
            throw new IllegalArgumentException("Promotion is not available for this sale: " + id);
        }
        // Multiple lines using the same promotion count as one redemption per sale.
        if (!redemptionRepository.existsByPromotionIdAndOrderId(id, order.getOrderId())) {
            if (promotion.getMaxUsesTotal() != null
                    && redemptionRepository.countByPromotionId(id) >= promotion.getMaxUsesTotal()) {
                throw new IllegalArgumentException("Promotion usage limit reached");
            }
            if (order.getCustomerId() != null && promotion.getMaxUsesPerCustomer() != null
                    && redemptionRepository.countByPromotionIdAndCustomerId(id, order.getCustomerId()) >= promotion.getMaxUsesPerCustomer()) {
                throw new IllegalArgumentException("Customer promotion usage limit reached");
            }
        }
        return promotion;
    }

    private void redeem(CustomerOrder order, Promotion promotion, BigDecimal amount) {
        PromotionRedemption redemption = redemptionRepository.findByOrderId(order.getOrderId()).stream()
                .filter(row -> row.getPromotionId().equals(promotion.getPromotionId())).findFirst()
                .orElseGet(() -> {
                    PromotionRedemption row = new PromotionRedemption();
                    row.setBranchId(order.getBranchId());
                    row.setOrderId(order.getOrderId());
                    row.setCustomerId(order.getCustomerId());
                    row.setPromotionId(promotion.getPromotionId());
                    row.setPromotionName(promotion.getName());
                    row.setPromoCode(promotion.getPromoCode());
                    row.setPromotionType(promotion.getType());
                    row.setUsedAt(order.getOrderDate());
                    row.setDiscountAmount(BigDecimal.ZERO);
                    return row;
                });
        redemption.setDiscountAmount(redemption.getDiscountAmount().add(amount));
        redemptionRepository.save(redemption);
    }

    public List<AppliedPromotionResponse> getAppliedPromotions(Long orderId) {
        return redemptionRepository.findByOrderId(orderId).stream().map(row -> {
            Promotion fallback = row.getPromotionName() == null
                    ? promotionRepository.findById(row.getPromotionId()).orElse(null) : null;
            return AppliedPromotionResponse.builder()
                    .promotionId(row.getPromotionId())
                    .promotionName(row.getPromotionName() != null ? row.getPromotionName() : fallback != null ? fallback.getName() : null)
                    .promoCode(row.getPromoCode())
                    .promotionType(row.getPromotionType() != null ? row.getPromotionType() : fallback != null ? fallback.getType() : null)
                    .discountAmount(row.getDiscountAmount()).build();
        }).toList();
    }

    public OrderProductPromotion getLinePromotion(Long orderProductId) {
        return linePromotionRepository.findByOrderProductId(orderProductId).stream().findFirst().orElse(null);
    }
}
