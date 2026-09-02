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
    private Long variantId;
    private String variantSku;
    private String variantLabel;
    private String internalBatchBarcode;
    private BigDecimal quantity;
}
