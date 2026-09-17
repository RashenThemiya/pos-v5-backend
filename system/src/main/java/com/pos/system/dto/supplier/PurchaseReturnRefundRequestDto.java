package com.pos.system.dto.supplier;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseReturnRefundRequestDto {
    private Long branchId;
    private BigDecimal amount;
    private String refundMethod;
    private String referenceNo;
    private String bankReference;
    private Long counterId;
    private Long cashSessionId;
    private Long processedBy;
}
