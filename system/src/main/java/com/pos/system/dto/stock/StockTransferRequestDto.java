package com.pos.system.dto.stock;

import lombok.Data;

import java.util.List;

@Data
public class StockTransferRequestDto {
    private Long fromBranchId;
    private Long toBranchId;
    private Long createdBy;
    private String note;
    private List<StockTransferItemRequestDto> items;
}