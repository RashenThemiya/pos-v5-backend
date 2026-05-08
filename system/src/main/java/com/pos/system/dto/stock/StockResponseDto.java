package com.pos.system.dto.stock;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class StockResponseDto {
    private Long stockId;
    private Long branchId;
    private Long itemId;
    private Long unitId;
    private BigDecimal availableQty;
    private BigDecimal damagedQty;
    private BigDecimal expiredQty;
    private LocalDateTime lastUpdated;
}