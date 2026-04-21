package com.pos.system.dto.supplier;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseOrderRequestDto {
    private Long branchId;
    private Long supplierId;
    private String poNo;
    private String status;
    private LocalDate expectedDate;
    private Long createdBy;
    private String note;
    private List<PurchaseOrderItemRequestDto> items;
}