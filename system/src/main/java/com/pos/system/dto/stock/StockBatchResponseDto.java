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
    private Long variantId;
    private String variantSku;
    private String variantLabel;
    private String itemSku;
    private String itemName;
    private Long supplyProductId;
    private Long supplyId;
    private Long supplierId;
    private String grnNo;
    private Long unitId;
    private Long masterUnitId;
    private String unitName;
    private String unitBarcode;
    private BigDecimal unitMultiplierToBase;
    private Boolean unitIsBaseUnit;
    private Boolean unitIsActive;
    private BigDecimal receivedQty;
    private BigDecimal qtyRemaining;
    private BigDecimal availableQty;
    private BigDecimal damagedQty;
    private BigDecimal expiredQty;
    private String internalBatchBarcode;
    private String batchNo;
    private String supplierBatchBarcode;
    private LocalDate expiryDate;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private LocalDateTime createdAt;
}
