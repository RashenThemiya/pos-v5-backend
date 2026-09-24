package com.pos.system.dto.sale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReturnResponse {
    private Long returnId;
    private String returnNo;
    private Long orderId;
    private String invoiceNo;
    private Long branchId;
    private Long customerId;
    private Long cashSessionId;
    private LocalDateTime returnDate;
    private String refundMethod;
    private String settlementMethod;
    private BigDecimal refundAmount;
    private String creditNoteNo;
    private String voucherNo;
    private ReturnVoucherResponse voucher;
    private Long exchangeOrderId;
    private String reason;
    private Long processedBy;
    private Long approvedBy;
    private String status;
    private List<ReturnItemResponse> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReturnItemResponse {
        private Long returnItemId;
        private Long orderProductId;
        private Long itemId;
        private Long variantId;
        private String variantSku;
        private String variantLabel;
        private String itemName;
        private Long unitId;
        private String unitName;
        private String internalBatchBarcode;
        private BigDecimal quantity;
        private BigDecimal soldQuantity;
        private BigDecimal previouslyReturnedQuantity;
        private BigDecimal returnableQuantity;
        private BigDecimal unitPrice;
        private BigDecimal lineRefund;
        private String condition;
    }
}
