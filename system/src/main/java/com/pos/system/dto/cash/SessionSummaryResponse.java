package com.pos.system.dto.cash;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryResponse {
    private Long sessionId;
    private BigDecimal openingCash;
    private BigDecimal totalCashSales;
    private BigDecimal totalCardSales;
    private BigDecimal totalOtherSales;
    private BigDecimal totalExpenses;
    private BigDecimal totalWithdrawals;
    private BigDecimal totalSupplierPayments;
    private BigDecimal totalSupplierRefunds;
    private BigDecimal expectedCash;
    private BigDecimal closingCash;
    private BigDecimal cashDifference;
    private String status;
}
