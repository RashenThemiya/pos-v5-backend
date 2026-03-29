package com.pos.system.dto.stock;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class StockCountRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotNull(message = "Created by user ID is required")
    private Long createdBy;

    private String note;

    @NotEmpty(message = "At least one item count is required")
    private List<StockCountItemRequest> items;

    @Data
    public static class StockCountItemRequest {
        @NotNull(message = "Item ID is required")
        private Long itemId;

        @NotNull(message = "Counted quantity is required")
        private BigDecimal countedQty;

        private String note;
    }
}
