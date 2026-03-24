package com.pos.system.dto.taxrate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TaxRateRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotBlank(message = "Tax name is required")
    @Size(max = 100, message = "Tax name must not exceed 100 characters")
    private String name;

    @NotNull(message = "Rate percent is required")
    @DecimalMin(value = "0.00", message = "Rate cannot be negative")
    @DecimalMax(value = "100.00", message = "Rate cannot exceed 100%")
    private BigDecimal ratePercent;

    private Boolean isActive = true;
}
