package com.pos.system.dto.stock;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StockTransferItemResponseDto {
    private Long transferItemId;
    private Long transferId;
    private Long itemId;
    private String itemName;
    private String itemSku;
    private Long variantId;
    private String variantSku;
    private String variantLabel;
    private Long destinationItemId;
    private Long destinationVariantId;
    private Long unitId;
    private String unitName;
    private BigDecimal unitQuantity;
    private String internalBatchBarcode;
    private BigDecimal quantity;
}
