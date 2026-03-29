package com.pos.system.dto.stock;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StockMovementResponse {
    private Long movementId;
    private Long branchId;
    private Long itemId;
    private String itemName;
    private String movementType;
    private String internalBatchBarcode;
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private BigDecimal unitPrice;
    private String refTable;
    private Long refId;
    private String note;
    private Long createdBy;
    private LocalDateTime createdAt;
}
