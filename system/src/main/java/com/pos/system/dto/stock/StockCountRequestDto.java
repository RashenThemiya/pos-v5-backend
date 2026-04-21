package com.pos.system.dto.stock;

import lombok.Data;

import java.util.List;

@Data
public class StockCountRequestDto {
    private Long branchId;
    private Long createdBy;
    private String note;
    private List<StockCountItemRequestDto> items;
}