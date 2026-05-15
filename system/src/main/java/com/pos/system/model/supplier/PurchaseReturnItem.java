package com.pos.system.model.supplier;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "purchase_return_items")
public class PurchaseReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long purchaseReturnItemId;

    @Column(nullable = false)
    private Long purchaseReturnId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long unitId;

    private String internalBatchBarcode;

    // AVAILABLE / DAMAGED / EXPIRED
    @Column(nullable = false, length = 30)
    private String returnStockType = "AVAILABLE";

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;
}