package com.pos.system.dto.brand;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class BrandResponse {
    private Long brandId;
    private Long branchId;
    private String name;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private List<Long> categoryIds;
    private List<String> categoryNames;
}
