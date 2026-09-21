package com.pos.system.dto.category;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CategoryPageResponseDto {
    private List<CategoryResponse> content;
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private String sort;
    private CategorySummaryDto summary;
}
