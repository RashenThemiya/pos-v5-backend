package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SupplierBalanceTransactionResponseDto {

    private Long txnId;

    private Long branchId;

    private Long supplierId;

    // SUPPLY_CREDIT / PAYMENT / ADJUSTMENT
    private String type;

    private BigDecimal amount;

    // Reference tracking
    private String refTable;   // supplies / supplier_payments
    private Long refId;

    private String note;

    private Long createdBy;

    private LocalDateTime createdAt;
}