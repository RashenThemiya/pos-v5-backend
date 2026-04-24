package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SupplierItemResponseDto {

    private Long supplierItemId;

    private Long branchId;
    private Long supplierId;
    private String supplierName;

    private Long itemId;
    private String itemName;
    private String sku;

    private Long unitId;

    private BigDecimal lastPurchaseCost;
    private BigDecimal defaultCostPrice;

    private Boolean isPreferred;
    private Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}