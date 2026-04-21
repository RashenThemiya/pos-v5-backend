package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PurchaseOrderItemResponseDto {
    private Long poItemId;
    private Long poId;
    private Long itemId;
    private Long unitId;
    private BigDecimal orderedQty;
    private BigDecimal receivedQty;
    private BigDecimal unitCostEst;
}