package com.pos.system.dto.customer;

import com.pos.system.dto.cash.CashSessionTransactionResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CustomerPaymentResponse {
    private CustomerBalanceTransactionResponse paymentTransaction;
    private BigDecimal updatedOutstandingBalance;
    private CashSessionTransactionResponse cashSessionTransaction;
}
