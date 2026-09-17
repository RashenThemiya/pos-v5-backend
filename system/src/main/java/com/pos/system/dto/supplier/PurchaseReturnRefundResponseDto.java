package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PurchaseReturnRefundResponseDto {
    private Long refundId;
    private Long purchaseReturnId;
    private Long branchId;
    private BigDecimal amount;
    private String refundMethod;
    private String referenceNo;
    private String bankReference;
    private Long counterId;
    private Long cashSessionId;
    private Long processedBy;
    private String processedByName;
    private LocalDateTime refundedAt;
}
