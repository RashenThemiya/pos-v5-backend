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
@Table(name = "promotion_buyx_gety_rules")
public class PromotionBuyXGetYRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ruleId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long promotionId;

    @Column(nullable = false)
    private Long buyItemId;

    // unit for the buy side — e.g. "buy 2 Boxes"
    @Column(nullable = false)
    private Long buyUnitId;

    @Column(nullable = false)
    private BigDecimal buyQty;

    @Column(nullable = false)
    private Long getItemId;

    // unit for the get side — e.g. "get 1 Bottle free"
    @Column(nullable = false)
    private Long getUnitId;

    @Column(nullable = false)
    private BigDecimal getQty;

    private BigDecimal getDiscountPercent = BigDecimal.valueOf(100);
}
