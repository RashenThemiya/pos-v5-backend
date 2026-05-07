package com.pos.system.dto.sale;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PaymentRequest {
    private List<PaymentLineDto> payments;
    private Long receivedBy;

    @Data
    public static class PaymentLineDto {
        private String paymentMethod;   // CASH, CARD, CREDIT, CHEQUE
        private BigDecimal amount;
        private BigDecimal tenderedAmount; // actual cash given (for change calc)
        private String referenceNo;     // card ref, cheque no etc
        private String note;
    }
}
