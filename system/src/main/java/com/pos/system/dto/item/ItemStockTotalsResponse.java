package com.pos.system.dto.item;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemStockTotalsResponse {
    private BigDecimal totalBaseQty;
    private BigDecimal availableBaseQty;
    private BigDecimal reservedBaseQty;
    private BigDecimal damagedBaseQty;
    private BigDecimal expiredBaseQty;
}
