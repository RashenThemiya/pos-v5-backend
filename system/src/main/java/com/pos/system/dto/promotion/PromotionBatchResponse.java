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
public class PromotionBatchResponse {
    private Long id;
    private Long promotionId;
    private String barcode;
    private BigDecimal maxQty;
    private BigDecimal usedQty;
    private Boolean isActive;
}
