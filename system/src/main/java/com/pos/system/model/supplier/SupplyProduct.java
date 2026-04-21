package com.pos.system.model.supplier;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "supply_products")
public class SupplyProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long supplyProductId;

    @Column(nullable = false)
    private Long supplyId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long unitId;

    private String batchNo;

    private String supplierBatchBarcode;

    private String productBarcode;

    @Column(nullable = false, unique = true, length = 120)
    private String internalBatchBarcode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal sellingPrice;

    // quantity entered in receiving unit
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantityReceived;

    // quantity converted to base unit
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantityReceivedBase;

    // remaining qty always in base unit
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal qtyRemaining;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal qtyDamaged = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal qtyExpired = BigDecimal.ZERO;

    private LocalDate expiryDate;

    @Column(precision = 14, scale = 2)
    private BigDecimal lineTotal;

    private LocalDateTime createdAt;
}