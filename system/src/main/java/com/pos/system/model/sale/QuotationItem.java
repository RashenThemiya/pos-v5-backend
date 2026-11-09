package com.pos.system.model.sale;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "quotation_items", indexes = @Index(name = "idx_quotation_item_quotation", columnList = "quotationId"))
public class QuotationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quotationItemId;
    @Column(nullable = false)
    private Long quotationId;
    @Column(nullable = false)
    private Long itemId;
    private Long variantId;
    private Long unitId;
    private String batchBarcode;
    private Long promotionId;
    private String promotionName;
    private String promotionType;
    @Column(precision = 19, scale = 2)
    private BigDecimal promotionValue;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discount;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;
}
