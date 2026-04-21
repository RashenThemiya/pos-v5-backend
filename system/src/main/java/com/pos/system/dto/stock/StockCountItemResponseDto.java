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
    private BigDecimal systemQty;
    private BigDecimal countedQty;
    private BigDecimal differenceQty;
    private String note;
}