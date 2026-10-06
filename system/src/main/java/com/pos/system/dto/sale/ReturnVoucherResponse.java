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
public class ReturnVoucherResponse {
    private Long returnId;
    private Long orderId;
    private Long branchId;
    private Long customerId;
    private String voucherNo;
    private String redemptionCode;
    private BigDecimal originalAmount;
    private BigDecimal usedAmount;
    private BigDecimal remainingAmount;
    private String status;
    private LocalDateTime returnDate;
}
