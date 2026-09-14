package com.pos.system.dto.promotion;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ApplyPromotionRequest {
    private Long branchId;
    private Long customerId;
    private String promoCode;
    private BigDecimal billTotal;
    private List<CartItemDto> cartItems;

    @Data
    public static class CartItemDto {
        private Long itemId;
        private Long variantId;
        private Long unitId;            // which unit this line is sold in
        private String batchBarcode;    // for BATCH type promos
        private BigDecimal qty;         // qty in the above unitId
        private BigDecimal unitPrice;   // price per the above unit
    }
}
