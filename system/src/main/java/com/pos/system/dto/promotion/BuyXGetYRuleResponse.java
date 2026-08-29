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
    private Long buyUnitId;
    private Long buyMasterUnitId;
    private String buyUnitName;         // populated for readability
    private BigDecimal buyQty;
    private Long getItemId;
    private Long getUnitId;
    private Long getMasterUnitId;
    private String getUnitName;         // populated for readability
    private BigDecimal getQty;
    private BigDecimal getDiscountPercent;
}
