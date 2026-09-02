package com.pos.system.dto.stock;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StockCountItemResponseDto {
    private Long stockCountItemId;
    private Long stockCountId;
    private Long itemId;
    private Long variantId;
    private String variantSku;
    private String variantLabel;
    private BigDecimal systemQty;
    private BigDecimal countedQty;
    private BigDecimal differenceQty;
    private String note;
}
