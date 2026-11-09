package com.pos.system.dto.sale;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuotationItemRequest {
    private Long itemId;
    private Long variantId;
    private Long unitId;
    private String batchBarcode;
    private Long promotionId;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discount;
}
