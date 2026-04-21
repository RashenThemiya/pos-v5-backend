package com.pos.system.dto.stock;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class StockMovementResponseDto {
    private Long movementId;
    private Long branchId;
    private String movementType;
    private Long itemId;
    private Long unitId;
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