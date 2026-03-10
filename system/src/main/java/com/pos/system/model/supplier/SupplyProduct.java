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

    private String batchNo;
    private String productBarcode;

    @Column(nullable = false, unique = true, length = 120)
    private String internalBatchBarcode;

    @Column(nullable = false)
    private BigDecimal costPrice;

    @Column(nullable = false)
    private BigDecimal sellingPrice;

    @Column(nullable = false)
    private BigDecimal quantityReceived;

    @Column(nullable = false)
    private BigDecimal qtyRemaining;

    private BigDecimal qtyDamaged = BigDecimal.ZERO;
    private BigDecimal qtyExpired = BigDecimal.ZERO;
    private LocalDate expiryDate;
    private BigDecimal lineTotal;
    private LocalDateTime createdAt;
}
