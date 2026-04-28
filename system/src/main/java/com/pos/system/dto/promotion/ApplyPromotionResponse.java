package com.pos.system.dto.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyPromotionResponse {
    private Long promotionId;
    private String promotionName;
    private String promoType;
    private BigDecimal discountAmount;
    private BigDecimal finalBillTotal;
    private List<LineDiscountDto> lineDiscounts; // per-item discount breakdown

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDiscountDto {
        private Long itemId;
        private BigDecimal discountAmount;
        private String reason;
    }
}
