package com.pos.system.dto.sale;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;

@Data
@Builder
public class AppliedPromotionResponse {
    private Long promotionId;
    private String promotionName;
    private String promoCode;
    private String promotionType;
    private BigDecimal discountAmount;
}
