package com.pos.system.model.sale;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "quotation_promotions", indexes = @Index(name = "idx_quotation_promotion_quotation", columnList = "quotationId"))
public class QuotationPromotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quotationPromotionId;
    @Column(nullable = false)
    private Long quotationId;
    @Column(nullable = false)
    private Long promotionId;
    private String promotionName;
    private String promoCode;
    private String promotionType;
    @Column(precision = 19, scale = 2)
    private BigDecimal promotionValue;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;
}
