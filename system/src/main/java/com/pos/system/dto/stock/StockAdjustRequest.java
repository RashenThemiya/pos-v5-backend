package com.pos.system.dto.stock;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockAdjustRequest {

    @NotNull(message = "Item ID is required")
    private Long itemId;

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotNull(message = "Quantity is required")
    private BigDecimal quantity; // positive = add, negative = remove

    private String movementType; // ADJUSTMENT_IN, ADJUSTMENT_OUT, DAMAGE, EXPIRED

    private String note;

    @NotNull(message = "Created by user ID is required")
    private Long createdBy;
}
