package com.pos.system.dto.sale;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalesReturnRequest {
    private Long orderId;
    private Long branchId;
    private Long customerId;
    private Long processedBy;
    private String refundMethod;    // CASH, CARD, CREDIT
    private String reason;
    private List<ReturnItemDto> items;

    @Data
    public static class ReturnItemDto {
        /** Optional; when given the return is drawn against this exact order line. */
        private Long orderProductId;
        private Long itemId;
        private Long variantId;
        private String internalBatchBarcode;
        private BigDecimal quantity;
        /** Ignored: the refund is derived from the original order's net paid amounts. */
        private BigDecimal unitPrice;
        private String condition;   // GOOD, DAMAGED, EXPIRED
    }
}
