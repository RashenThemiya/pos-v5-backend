package com.pos.system.dto.cash;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WithdrawalRequest {
    private Long cashSessionId;
    private BigDecimal amount;
    private String reason;
    private String notes;
    private LocalDateTime withdrawalDate;
    private Long userId;
}
