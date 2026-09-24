package com.pos.system.model.sale;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sales_return_items")
public class SalesReturnItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long returnItemId;

    @Column(nullable = false)
    private Long returnId;

    private Long orderProductId;

    @Column(nullable = false)
    private Long itemId;

    private Long variantId;
    private Long unitId;

    private String internalBatchBarcode;
    @Column(nullable = false)
    private BigDecimal quantity;
    @Column(name = "`condition`")
    private String condition;
    @Column(nullable = false)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private BigDecimal lineRefund;
    private BigDecimal previouslyReturnedQuantity = BigDecimal.ZERO;
    private BigDecimal returnableQuantity = BigDecimal.ZERO;
}
