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

    @NotNull(message = "Unit ID is required")
    private Long unitId;

    @NotNull(message = "Batch barcode is required")
    private String internalBatchBarcode;

    @NotNull(message = "Quantity is required")
    private BigDecimal quantity;

    @NotNull(message = "Adjustment type is required")
    private String adjustmentType;

    private String note;

    @NotNull(message = "Created by user ID is required")
    private Long createdBy;
}
