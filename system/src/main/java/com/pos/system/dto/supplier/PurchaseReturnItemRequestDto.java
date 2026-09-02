package com.pos.system.dto.supplier;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseReturnItemRequestDto {

    private Long itemId;
    private Long variantId;
    private Long unitId;
    private String internalBatchBarcode;

    // AVAILABLE / DAMAGED / EXPIRED
    private String returnStockType;

    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal lineTotal;
}
