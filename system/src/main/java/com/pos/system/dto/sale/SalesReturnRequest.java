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
    private Long cashSessionId;
    private String refundMethod;    // Legacy alias for settlementMethod
    private String settlementMethod; // CASH, CARD, BANK, CUSTOMER_CREDIT, RETURN_VOUCHER, EXCHANGE
    private Long exchangeOrderId;
    private String reason;
    private List<ReturnItemDto> items;

    @Data
    public static class ReturnItemDto {
        private Long orderProductId;
        private Long itemId;
        private Long variantId;
        private Long unitId;
        private String internalBatchBarcode;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private String condition;   // GOOD, DAMAGED, EXPIRED
    }
}
