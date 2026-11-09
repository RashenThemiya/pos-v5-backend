package com.pos.system.dto.sale;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class QuotationItemResponse {
    private Long quotationItemId;
    private Long itemId;
    private Long variantId;
    private Long unitId;
    private String batchBarcode;
    private Long promotionId;
    private String promotionName;
    private String promotionType;
    private BigDecimal promotionValue;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discount;
    private BigDecimal lineTotal;
}
