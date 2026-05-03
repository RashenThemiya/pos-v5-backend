package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PurchaseReturnItemResponseDto {

    private Long purchaseReturnItemId;
    private Long purchaseReturnId;
    private Long itemId;
    private Long unitId;
    private String internalBatchBarcode;
    private String returnStockType;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal lineTotal;
}