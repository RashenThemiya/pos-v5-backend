package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PurchaseOrderSummaryDto {
    private long total;
    private long draft;
    private long open;
    private long partiallyReceived;
    private long fullyReceived;
    private long overdue;
}
