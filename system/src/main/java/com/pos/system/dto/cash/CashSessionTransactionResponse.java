package com.pos.system.dto.cash;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashSessionTransactionResponse {
    private Long id;
    private Long sessionId;
    private String type;
    private BigDecimal amount;
    private String paymentMethod;
    private Long paymentId;
    private Long supplierPaymentId;
    private Long purchaseReturnId;
    private Long expenseId;
    private Long withdrawalId;
    private Long orderId;
    private String invoiceNo;
    private Long salesReturnId;
    private String note;
    private Long createdBy;
    private LocalDateTime createdAt;
}
