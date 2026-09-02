package com.pos.system.dto.supplier;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SupplierItemRequestDto {

    private Long branchId;
    private Long supplierId;
    private Long itemId;
    private Long variantId;
    private Long unitId;

    private BigDecimal lastPurchaseCost;
    private BigDecimal defaultCostPrice;

    private Boolean isPreferred;
    private Boolean isActive;
}
