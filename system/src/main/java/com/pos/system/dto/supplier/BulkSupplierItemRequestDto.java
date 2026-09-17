package com.pos.system.dto.supplier;

import lombok.Data;

import java.util.List;

@Data
public class BulkSupplierItemRequestDto {

    private Long branchId;
    private Long supplierId;
    private List<SupplierItemBulkEntryRequestDto> items;
}
