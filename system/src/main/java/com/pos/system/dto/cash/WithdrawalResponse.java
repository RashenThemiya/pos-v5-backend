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
public class WithdrawalResponse {
    private Long withdrawalId;
    private Long cashSessionId;
    private BigDecimal amount;
    private String reason;
    private String notes;
    private LocalDateTime withdrawalDate;
    private Long userId;
}
