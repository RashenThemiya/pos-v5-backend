package com.pos.system.dto.taxrate;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TaxRateResponse {
    private Long taxId;
    private Long branchId;
    private String name;
    private BigDecimal ratePercent;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
