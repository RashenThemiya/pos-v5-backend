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
public class ExpenseResponse {
    private Long expenseId;
    private Long branchId;
    private Long cashSessionId;
    private String category;
    private BigDecimal amount;
    private String paymentMethod;
    private LocalDateTime expenseDate;
    private Long createdBy;
    private String note;
}
