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
public class PromotionItemResponse {
    private Long id;
    private Long promotionId;
    private Long itemId;
    private Long variantId;
    private String variantSku;
    private String variantLabel;
    private Long unitId;
    private Long masterUnitId;
    private String unitName;        // populated for readability
    private BigDecimal maxQty;
    private BigDecimal usedQty;
    private Boolean isActive;
}
