package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PurchaseOrderPageResponseDto {
    private List<PurchaseOrderResponseDto> content;
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private String sort;
    private PurchaseOrderSummaryDto summary;
}
