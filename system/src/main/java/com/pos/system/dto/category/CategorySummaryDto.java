package com.pos.system.dto.category;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategorySummaryDto {
    private long mainCategories;
    private long subcategories;
    private long emptyCategories;
}
