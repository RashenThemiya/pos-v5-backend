package com.pos.system.dto.promotion;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PromotionItemRequest {
    private Long branchId;
    private Long itemId;
    private BigDecimal maxQty;
}
