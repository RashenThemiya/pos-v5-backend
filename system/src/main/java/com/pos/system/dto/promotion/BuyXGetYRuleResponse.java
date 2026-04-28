package com.pos.system.dto.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyXGetYRuleResponse {
    private Long ruleId;
    private Long promotionId;
    private Long buyItemId;
    private BigDecimal buyQty;
    private Long getItemId;
    private BigDecimal getQty;
    private BigDecimal getDiscountPercent;
}
