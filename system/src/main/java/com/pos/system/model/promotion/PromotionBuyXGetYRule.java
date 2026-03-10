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

    @Column(nullable = false)
    private BigDecimal buyQty;

    @Column(nullable = false)
    private Long getItemId;

    @Column(nullable = false)
    private BigDecimal getQty;

    private BigDecimal getDiscountPercent = BigDecimal.valueOf(100);
}
