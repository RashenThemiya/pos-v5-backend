package com.pos.system.dto.stock;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StockCountResponseDto {
    private Long stockCountId;
    private Long branchId;
    private LocalDateTime countDate;
    private String status;
    private Long createdBy;
    private Long approvedBy;
    private String note;
    private List<StockCountItemResponseDto> items;
}