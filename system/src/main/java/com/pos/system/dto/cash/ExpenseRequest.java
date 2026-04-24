package com.pos.system.dto.cash;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExpenseRequest {
    private Long branchId;
    private Long cashSessionId;
    private String category;
    private BigDecimal amount;
    private String paymentMethod;
    private LocalDateTime expenseDate;
    private Long createdBy;
    private String note;
}
