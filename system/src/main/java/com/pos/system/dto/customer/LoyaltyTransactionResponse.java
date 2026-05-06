package com.pos.system.dto.customer;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LoyaltyTransactionResponse {
    private Long loyaltyTxnId;
    private Long branchId;
    private Long customerId;
    private Long orderId;
    private String type;
    private BigDecimal points;
    private BigDecimal valueAmount;
    private String note;
    private Long createdBy;
    private LocalDateTime createdAt;
}