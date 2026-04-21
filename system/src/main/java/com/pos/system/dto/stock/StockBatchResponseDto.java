package com.pos.system.dto.stock;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class StockBatchResponseDto {
    private Long stockBatchId;
    private Long branchId;
    private Long itemId;
    private Long supplyProductId;
    private Long unitId;
    private BigDecimal receivedQty;
    private BigDecimal receivedBaseQty;
    private BigDecimal qtyRemaining;
    private String internalBatchBarcode;
    private String batchNo;
    private String supplierBatchBarcode;
    private LocalDate expiryDate;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private LocalDateTime createdAt;
}