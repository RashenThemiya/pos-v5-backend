package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PurchaseReturnResponseDto {

    private Long purchaseReturnId;
    private String returnNo;
    private Long branchId;
    private Long supplierId;
    private Long supplyId;

    private LocalDateTime returnDate;
    private String refundMethod;
    private Long counterId;
    private Long cashSessionId;
    private String bankReference;
    private BigDecimal refundAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceDue;
    private String paymentStatus;
    private String reason;
    private Long processedBy;
    private String status;

    private List<PurchaseReturnItemResponseDto> items;
}
