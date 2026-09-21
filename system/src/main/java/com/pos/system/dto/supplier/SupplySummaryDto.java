package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SupplySummaryDto {
    private long total;
    private long pending;
    private long partial;
    private long completed;
    private long cancelled;
    private long unpaid;
    private long partiallyPaid;
    private long paid;
    private BigDecimal totalAmount;
    private BigDecimal balanceAmount;
}
