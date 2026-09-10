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
    private String itemName;
    private String itemImage;
    private String sku;
    private Long variantId;
    private String variantSku;
    private String variantLabel;
    private String variantImage;
    private Long unitId;
    private Long masterUnitId;
    private String unitName;
    private BigDecimal orderedQty;
    private BigDecimal receivedQty;
    private BigDecimal remainingQty;
    private BigDecimal unitCostEst;
}
