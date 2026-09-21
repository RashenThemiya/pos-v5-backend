package com.pos.system.dto.category;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryResponse {
    private Long categoryId;
    private Long branchId;
    private String name;
    private Long parentId;
    private String parentName;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private Long productCount;
    private Long subcategoryCount;
}
