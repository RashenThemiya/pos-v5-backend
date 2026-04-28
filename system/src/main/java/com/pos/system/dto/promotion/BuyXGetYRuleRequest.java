package com.pos.system.dto.promotion;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BuyXGetYRuleRequest {
    private Long branchId;
    private Long buyItemId;
    private BigDecimal buyQty;
    private Long getItemId;
    private BigDecimal getQty;
    private BigDecimal getDiscountPercent; // default 100 = fully free
}
