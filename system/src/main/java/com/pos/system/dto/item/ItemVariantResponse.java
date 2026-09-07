package com.pos.system.dto.item;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ItemVariantResponse {
    private Long variantId;
    private Long itemId;
    private Long branchId;
    private String sku;
    private BigDecimal defaultSellingPrice;
    private String image;
    private Boolean isActive;
    private ItemStockTotalsResponse stock;
    private List<ItemVariantAttributeResponse> attributes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
