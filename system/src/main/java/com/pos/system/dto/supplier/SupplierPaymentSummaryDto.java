package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SupplierPaymentSummaryDto {
    private long total;
    private long supplierCount;
    private BigDecimal totalAmount;
}
