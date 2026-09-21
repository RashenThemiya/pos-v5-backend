package com.pos.system.dto.brand;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BrandPageResponseDto {
    private List<BrandResponse> content;
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private String sort;
    private BrandSummaryDto summary;
}
