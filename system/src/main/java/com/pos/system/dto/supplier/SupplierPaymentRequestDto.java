package com.pos.system.dto.supplier;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SupplierPaymentRequestDto {

    private Long branchId;
    private Long supplierId;
    private Long supplyId;
    private Long poId;

    private BigDecimal amount;

    // CASH, COUNTER_CASH, BANK_TRANSFER, CHEQUE, CARD
    private String paymentMethod;

    private LocalDateTime paymentDate;

    private Long paidBy;

    // Required only for CASH / COUNTER_CASH
    private Long cashSessionId;

    private String referenceNo;
    private String note;
}
