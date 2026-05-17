package com.pos.system.dto.stock;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockTransferItemRequestDto {
    private Long itemId;
    private Long stockBatchId;
    private String internalBatchBarcode;
    private BigDecimal quantity; // base qty
}
