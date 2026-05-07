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
        private Long itemId;
        private String internalBatchBarcode;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private String condition;   // GOOD, DAMAGED, EXPIRED
    }
}
