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
    private Long variantId;
    private String variantSku;
    private String variantLabel;
    private Long unitId;
    private Long masterUnitId;
    private String unitName;
    private String internalBatchBarcode;
    private String returnStockType;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal lineTotal;
}
