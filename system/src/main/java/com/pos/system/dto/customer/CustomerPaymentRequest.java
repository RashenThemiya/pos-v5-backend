package com.pos.system.dto.customer;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerPaymentRequest {
    private Long branchId;
    private BigDecimal amount;
    private String paymentMethod;
    private Long cashSessionId;
    private Long counterId;
    private Long receivedBy;
    private String referenceNo;
    private String note;
}
