package com.pos.system.dto.item;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ItemResponse {
    private Long itemId;
    private Long branchId;
    private String sku;
    private String name;
    private String image;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private Boolean isWeighed;
    private String scaleBarcodePrefix;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
