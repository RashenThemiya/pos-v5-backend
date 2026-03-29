package com.pos.system.dto.brand;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BrandResponse {
    private Long brandId;
    private Long branchId;
    private String name;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
