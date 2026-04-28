package com.pos.system.dto.promotion;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ApplyPromotionRequest {
    private Long branchId;
    private Long customerId;       // nullable - for maxUsesPerCustomer check
    private String promoCode;      // nullable - for code-based promos
    private BigDecimal billTotal;
    private List<CartItemDto> cartItems;

    @Data
    public static class CartItemDto {
        private Long itemId;
        private String batchBarcode; // nullable - for BATCH type promos
        private BigDecimal qty;
        private BigDecimal unitPrice;
    }
}
