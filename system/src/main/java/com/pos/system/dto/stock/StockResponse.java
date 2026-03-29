package com.pos.system.dto.stock;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StockResponse {
    private Long stockId;
    private Long branchId;
    private Long itemId;
    private String itemName;
    private String itemSku;
    private BigDecimal availableQty;
    private BigDecimal damagedQty;
    private BigDecimal expiredQty;
    private LocalDateTime lastUpdated;
}
