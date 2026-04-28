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
    private BigDecimal maxQty;
    private BigDecimal usedQty;
    private Boolean isActive;
}
