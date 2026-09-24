package com.pos.system.dto.sale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnVoucherResponse {
    private Long voucherId;
    private Long branchId;
    private String voucherNo;
    private String redemptionCode;
    private Long originalOrderId;
    private Long salesReturnId;
    private String originalInvoiceNo;
    private String returnNo;
    private String creditNoteNo;
    private BigDecimal originalAmount;
    private BigDecimal usedAmount;
    private BigDecimal remainingAmount;
    private String status;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private Long issuedBy;
    private LocalDateTime createdAt;
    private List<TransactionResponse> transactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionResponse {
        private Long transactionId;
        private String type;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
        private Long orderId;
        private Long salesReturnId;
        private String referenceNo;
        private String note;
        private Long createdBy;
        private LocalDateTime createdAt;
    }
}
