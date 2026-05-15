package com.pos.system.dto.sale;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProcessSaleRequest {

    // ─── Order fields ─────────────────────────────────────────────────────────
    private Long branchId;
    private Long userId;
    private Long customerId;            // nullable for walk-in; REQUIRED if any payment is CREDIT
    private Long cashSessionId;

    // Each item already carries its own promotionId + discount resolved by the
    // item-get route (ITEM_PERCENTAGE, ITEM_FIXED, BUY_X_GET_Y, BATCH).
    private List<OrderProductRequest> items;

    // Bill-level promotions only — PERCENTAGE or FIXED type.
    // Cashier manually picks these after all items are added.
    // Can be null/empty if no bill-level promo applied.
    private List<Long> billPromotionIds;

    // Total bill-level discount amount (sum from bill-promo calculations).
    // Item-level discounts are already inside each item's discount field.
    private BigDecimal discount;

    // Small +/- to make the final total a clean amount (round off small change).
    private BigDecimal rounding;

    private String notes;

    // ─── Payment fields ───────────────────────────────────────────────────────
    private List<PaymentLineDto> payments;

    @Data
    public static class PaymentLineDto {
        private String paymentMethod;       // CASH, CARD, CREDIT, CHEQUE
        private BigDecimal amount;
        private BigDecimal tenderedAmount;  // actual cash handed over (for change calculation)
        private String referenceNo;         // card ref no, cheque no, etc.
        private String note;
    }
}
