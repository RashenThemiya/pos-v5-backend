package com.pos.system.dto.stock;

import lombok.Data;

import java.util.List;

@Data
public class OpeningStockRequestDto {
    private Long branchId;
    private Long createdBy;
    private List<OpeningStockItemRequestDto> items;
}