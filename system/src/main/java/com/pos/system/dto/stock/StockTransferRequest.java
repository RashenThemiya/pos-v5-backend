package com.pos.system.dto.stock;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class StockTransferRequest {

    @NotNull(message = "From branch ID is required")
    private Long fromBranchId;

    @NotNull(message = "To branch ID is required")
    private Long toBranchId;

    @NotNull(message = "Created by user ID is required")
    private Long createdBy;

    private String note;

    @NotEmpty(message = "At least one item is required")
    private List<StockTransferItemRequest> items;

    @Data
    public static class StockTransferItemRequest {
        @NotNull(message = "Item ID is required")
        private Long itemId;

        @NotNull(message = "Quantity is required")
        private BigDecimal quantity;

        private String internalBatchBarcode;
    }
}
