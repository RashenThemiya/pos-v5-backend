package com.pos.system.dto.promotion;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PromotionBatchRequest {
    private Long branchId;
    private String barcode;
    private BigDecimal maxQty;
}
