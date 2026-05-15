package com.pos.system.dto.customer;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CustomerBalanceTransactionResponse {
    private Long txnId;
    private Long branchId;
    private Long customerId;
    private String type;
    private BigDecimal amount;
    private String refTable;
    private Long refId;
    private String note;
    private Long createdBy;
    private LocalDateTime createdAt;
}