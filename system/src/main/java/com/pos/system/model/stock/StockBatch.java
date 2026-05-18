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
@Table(name = "stock_batches", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"branch_id", "internal_batch_barcode"})
})
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

    @Column(nullable = false)
    private Long unitId;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal receivedQty;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal receivedBaseQty;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal qtyRemaining;

    @Column(precision = 14, scale = 4)
    private BigDecimal availableQty = BigDecimal.ZERO;

    @Column(precision = 14, scale = 4)
    private BigDecimal damagedQty = BigDecimal.ZERO;

    @Column(precision = 14, scale = 4)
    private BigDecimal expiredQty = BigDecimal.ZERO;

    @Column(nullable = false, length = 120)
    private String internalBatchBarcode;

    private String batchNo;
    private String supplierBatchBarcode;

    private LocalDate expiryDate;

    @Column(precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(precision = 14, scale = 2)
    private BigDecimal sellingPrice;

    private LocalDateTime createdAt;
}
