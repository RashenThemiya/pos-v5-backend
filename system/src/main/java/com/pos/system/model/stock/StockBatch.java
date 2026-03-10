package com.pos.system.model.stock;

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
@Table(name = "stock_batches")
public class StockBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockBatchId;

    @Column(nullable = false)
    private Long branchId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long supplyProductId;

    @Column(nullable = false, unique = true, length = 120)
    private String internalBatchBarcode;

    private LocalDate expiryDate;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private BigDecimal qtyRemaining;
    private LocalDateTime createdAt;
}
