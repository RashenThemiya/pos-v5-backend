package com.pos.system.dto.sale;

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
public class PaymentResponse {
    private Long paymentId;
    private Long orderId;
    private Long cashSessionId;
    private BigDecimal amount;
    private BigDecimal tenderedAmount;
    private BigDecimal changeAmount;
    private String paymentMethod;
    private LocalDateTime paymentDate;
    private Long receivedBy;
    private String referenceNo;
    private String note;
}
