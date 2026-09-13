package com.pos.system.dto.promotion;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BuyXGetYRuleRequest {
    private Long branchId;
    private Long buyItemId;
    private Long buyVariantId;
    private Long buyUnitId;             // e.g. unitId for "Box"
    private BigDecimal buyQty;          // e.g. 2 (meaning 2 boxes)
    private Long getItemId;
    private Long getVariantId;
    private Long getUnitId;             // e.g. unitId for "Bottle"
    private BigDecimal getQty;          // e.g. 1 (meaning 1 bottle free)
    private BigDecimal getDiscountPercent; // 100 = fully free, 50 = half price
}
