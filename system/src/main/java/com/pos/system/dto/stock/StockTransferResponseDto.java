package com.pos.system.dto.stock;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StockTransferResponseDto {
    private Long transferId;
    private Long fromBranchId;
    private Long toBranchId;
    private LocalDateTime transferDate;
    private String status;
    private Long createdBy;
    private String note;
    private List<StockTransferItemResponseDto> items;
}