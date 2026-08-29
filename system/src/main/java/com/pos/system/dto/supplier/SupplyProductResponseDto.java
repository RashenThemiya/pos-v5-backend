package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SupplyProductResponseDto {
    private Long supplyProductId;
    private Long supplyId;
    private Long itemId;
    private Long unitId;
    private Long masterUnitId;
    private String unitName;
    private String batchNo;
    private String supplierBatchBarcode;
    private String productBarcode;
    private String internalBatchBarcode;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private BigDecimal quantityReceived;
    private BigDecimal quantityReceivedBase;
    private BigDecimal qtyRemaining;
    private BigDecimal qtyDamaged;
    private BigDecimal qtyExpired;
    private LocalDate expiryDate;
    private BigDecimal lineTotal;
    private LocalDateTime createdAt;
}
