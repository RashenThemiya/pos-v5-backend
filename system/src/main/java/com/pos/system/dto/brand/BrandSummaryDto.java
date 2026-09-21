package com.pos.system.dto.brand;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BrandSummaryDto {
    private long total;
    private long active;
    private long inactive;
}
