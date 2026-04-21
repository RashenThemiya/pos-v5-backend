package com.pos.system.dto.supplier;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SupplierPaymentRequestDto {
    private Long branchId;
    private Long supplierId;
    private Long supplyId;
    private BigDecimal amount;
    private String paymentMethod;
    private LocalDateTime paymentDate;
    private Long paidBy;
    private Long cashSessionId;
    private String referenceNo;
    private String note;
}