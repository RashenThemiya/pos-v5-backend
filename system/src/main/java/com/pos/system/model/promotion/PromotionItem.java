package com.pos.system.model.promotion;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "promotion_items")
public class PromotionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long promotionId;

    @Column(nullable = false)
    private Long itemId;

    private Long variantId;

    // which unit the maxQty and discount applies to
    // e.g. unitId for "Box" means discount applies when sold by the box
    @Column(nullable = false)
    private Long unitId;

    private BigDecimal maxQty;
    private BigDecimal usedQty = BigDecimal.ZERO;
    private Boolean isActive = true;
}
