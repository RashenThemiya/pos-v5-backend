package com.pos.system.dto.promotion;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PromotionItemRequest {
    private Long branchId;
    private Long itemId;
    private Long variantId;
    private Long unitId;    // which unit this discount applies to
    private BigDecimal maxQty;
}
