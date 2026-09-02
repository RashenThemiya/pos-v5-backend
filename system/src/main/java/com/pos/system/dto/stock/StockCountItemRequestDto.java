package com.pos.system.dto.stock;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockCountItemRequestDto {
    private Long itemId;
    private Long variantId;
    private BigDecimal countedQty;
    private String note;
}
