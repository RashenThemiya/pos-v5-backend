package com.pos.system.dto.supplier;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseOrderItemRequestDto {
    private Long itemId;
    private Long unitId;
    private BigDecimal orderedQty;
    private BigDecimal unitCostEst;
}