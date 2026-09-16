package com.pos.system.dto.supplier;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseReturnRequestDto {

    private Long branchId;
    private Long supplierId;
    private Long supplyId;

    private LocalDateTime returnDate;

    // BALANCE_ADJUSTMENT / CASH_REFUND / BANK_REFUND
    private String refundMethod;
    private Long counterId;
    private Long cashSessionId;
    private String bankReference;

    private BigDecimal refundAmount;
    private String reason;
    private Long processedBy;
    private String status;

    private List<PurchaseReturnItemRequestDto> items;
}
