package com.pos.system.dto.supplier;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SupplyProductRequestDto {
    private Long itemId;
    private Long unitId;
    private String batchNo;
    private String supplierBatchBarcode;
    private String productBarcode;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private BigDecimal quantityReceived;
    private BigDecimal qtyDamaged;
    private BigDecimal qtyExpired;
    private LocalDate expiryDate;
    private BigDecimal lineTotal;
}