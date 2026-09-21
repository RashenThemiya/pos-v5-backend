package com.pos.system.dto.supplier;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PurchaseReturnSummaryDto {
    private long total;
    private long pending;
    private long completed;
    private long cancelled;
}
