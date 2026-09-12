package com.pos.system.dto.cash;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviousCashSessionResponse {
    private BigDecimal balance;
    private SessionResponse session;
    private SessionSummaryResponse summary;
    private List<CashSessionTransactionResponse> transactions;
    private List<ExpenseResponse> expenses;
    private List<WithdrawalResponse> withdrawals;
}
